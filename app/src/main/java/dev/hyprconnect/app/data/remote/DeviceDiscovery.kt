package dev.hyprconnect.app.data.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hyprconnect.app.domain.model.Device
import dev.hyprconnect.app.domain.model.DeviceType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceDiscovery @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_hyprconnect._tcp."
    private val TAG = "DeviceDiscovery"

    fun discoverDevices(): Flow<List<Device>> = callbackFlow {
        val discoveredDevices = mutableMapOf<String, Device>()

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                val port = serviceInfo.port
                val name = serviceInfo.serviceName
                
                val deviceType = when (serviceInfo.attributes["device_type"]?.toString(Charsets.UTF_8)) {
                    "desktop" -> DeviceType.DESKTOP
                    "laptop" -> DeviceType.LAPTOP
                    "phone" -> DeviceType.PHONE
                    "tablet" -> DeviceType.TABLET
                    else -> DeviceType.OTHER
                }

                val fingerprint = serviceInfo.attributes["fingerprint"]?.toString(Charsets.UTF_8)

                val device = Device(
                    id = name,
                    name = name,
                    host = host,
                    port = port,
                    type = deviceType,
                    fingerprint = fingerprint
                )
                
                discoveredDevices[name] = device
                trySend(discoveredDevices.values.toList())
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains(serviceType)) {
                    nsdManager.resolveService(service, resolveListener)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                discoveredDevices.remove(service.serviceName)
                trySend(discoveredDevices.values.toList())
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: $errorCode")
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: $errorCode")
                nsdManager.stopServiceDiscovery(this)
            }
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping discovery", e)
            }
        }
    }
}
