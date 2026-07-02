package dev.hyprconnect.app.data.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hyprconnect.app.domain.model.Device
import dev.hyprconnect.app.domain.model.DeviceType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.InetAddress
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

/**
 * Service discovery façade.
 *
 * Android 12+ uses the platform [NsdManager], which was reimplemented on
 * top of the modern resolver and is reliable. Earlier versions go through
 * a legacy mDNS path that is broken on many vendor ROMs (MIUI, Mediatek,
 * etc.) — even with `MulticastLock` held, announcements never reach the
 * app. On those devices we use [JmDNS] instead, which opens its own UDP
 * socket on port 5353 and bypasses the platform path entirely.
 */
@Singleton
class DeviceDiscovery @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val nsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val TAG = "DeviceDiscovery"

    fun discoverDevices(): Flow<List<Device>> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            discoverViaNsd()
        } else {
            discoverViaJmDns()
        }

    // -------------------------------------------------------------------------
    // NsdManager path (Android 12+)
    // -------------------------------------------------------------------------
    private fun discoverViaNsd(): Flow<List<Device>> = callbackFlow {
        val serviceType = "_hyprconnect._tcp"
        val discoveredDevices = mutableMapOf<String, Device>()

        val multicastLock = wifiManager.createMulticastLock("hyprconnect-mdns").apply {
            setReferenceCounted(false)
            acquire()
        }

        // NsdManager rejects reused ResolveListener instances and only
        // allows one resolve in flight at a time on older releases; we
        // keep the same discipline on 12+ to stay safe.
        val pending = ArrayDeque<NsdServiceInfo>()
        var resolving = false
        val lock = Any()

        lateinit var resolveNext: () -> Unit
        resolveNext = next@{
            val info = synchronized(lock) {
                if (resolving) return@next
                val n = pending.pollFirst() ?: return@next
                resolving = true
                n
            }
            nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                    synchronized(lock) { resolving = false }
                    resolveNext()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    serviceInfo.host?.hostAddress?.let { host ->
                        val name = serviceInfo.serviceName
                        Log.d(TAG, "Resolved $name at $host:${serviceInfo.port}")
                        discoveredDevices[name] = Device(
                            id = name,
                            name = name,
                            host = host,
                            port = serviceInfo.port,
                            type = parseDeviceType(serviceInfo.attributes["device_type"]
                                ?.toString(Charsets.UTF_8)),
                            fingerprint = serviceInfo.attributes["fingerprint"]
                                ?.toString(Charsets.UTF_8),
                            addresses = serviceInfo.attributes["addresses"]
                                ?.toString(Charsets.UTF_8)
                                ?.split(',')
                                ?.map { it.trim() }
                                ?.filter { it.isNotEmpty() }
                                ?: emptyList()
                        )
                        trySend(discoveredDevices.values.toList())
                    }
                    synchronized(lock) { resolving = false }
                    resolveNext()
                }
            })
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "NSD discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains(serviceType)) {
                    synchronized(lock) { pending.addLast(service) }
                    resolveNext()
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                discoveredDevices.remove(service.serviceName)
                trySend(discoveredDevices.values.toList())
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "NSD discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "NSD start failed: $errorCode")
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "NSD stop failed: $errorCode")
            }
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping NSD discovery", e)
            }
            try {
                if (multicastLock.isHeld) multicastLock.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing multicast lock", e)
            }
        }
    }

    // -------------------------------------------------------------------------
    // JmDNS path (Android <12 — works around vendor-broken NsdManager)
    // -------------------------------------------------------------------------
    private fun discoverViaJmDns(): Flow<List<Device>> = callbackFlow {
        val serviceType = "_hyprconnect._tcp.local."
        val discoveredDevices = mutableMapOf<String, Device>()

        val multicastLock = wifiManager.createMulticastLock("hyprconnect-mdns").apply {
            setReferenceCounted(false)
            acquire()
        }

        val bindAddress: InetAddress = wifiInetAddress() ?: InetAddress.getLocalHost()
        val jmdns = JmDNS.create(bindAddress, "hyprconnect-android")
        Log.d(TAG, "JmDNS discovery started on ${bindAddress.hostAddress}")

        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                jmdns.requestServiceInfo(event.type, event.name, true)
            }

            override fun serviceRemoved(event: ServiceEvent) {
                if (discoveredDevices.remove(event.name) != null) {
                    trySend(discoveredDevices.values.toList())
                }
            }

            override fun serviceResolved(event: ServiceEvent) {
                val info = event.info ?: return
                val host = info.inet4Addresses.firstOrNull()?.hostAddress
                    ?: info.hostAddresses.firstOrNull()
                    ?: return
                val name = info.name

                Log.d(TAG, "Resolved $name at $host:${info.port}")

                discoveredDevices[name] = Device(
                    id = name,
                    name = name,
                    host = host,
                    port = info.port,
                    type = parseDeviceType(info.getPropertyString("device_type")),
                    fingerprint = info.getPropertyString("fingerprint"),
                    addresses = info.getPropertyString("addresses")
                        ?.split(',')
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?: emptyList()
                )
                trySend(discoveredDevices.values.toList())
            }
        }

        jmdns.addServiceListener(serviceType, listener)

        awaitClose {
            Log.d(TAG, "JmDNS discovery stopped")
            try {
                jmdns.removeServiceListener(serviceType, listener)
                jmdns.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing JmDNS", e)
            }
            try {
                if (multicastLock.isHeld) multicastLock.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing multicast lock", e)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private fun parseDeviceType(raw: String?): DeviceType = when (raw) {
        "desktop" -> DeviceType.DESKTOP
        "laptop" -> DeviceType.LAPTOP
        "phone" -> DeviceType.PHONE
        "tablet" -> DeviceType.TABLET
        else -> DeviceType.OTHER
    }

    @Suppress("DEPRECATION")
    private fun wifiInetAddress(): InetAddress? {
        val ip = wifiManager.connectionInfo?.ipAddress ?: return null
        if (ip == 0) return null
        val bytes = byteArrayOf(
            (ip and 0xff).toByte(),
            (ip shr 8 and 0xff).toByte(),
            (ip shr 16 and 0xff).toByte(),
            (ip shr 24 and 0xff).toByte()
        )
        return InetAddress.getByAddress(bytes)
    }
}
