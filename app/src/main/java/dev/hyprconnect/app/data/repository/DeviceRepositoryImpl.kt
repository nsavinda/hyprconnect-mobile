package dev.hyprconnect.app.data.repository

import android.util.Log
import dev.hyprconnect.app.data.local.CertificateStore
import dev.hyprconnect.app.data.remote.DeviceDiscovery
import dev.hyprconnect.app.data.remote.HyprConnectClient
import dev.hyprconnect.app.data.remote.JsonRpcRequest
import dev.hyprconnect.app.domain.model.Device
import dev.hyprconnect.app.domain.model.DeviceStatus
import dev.hyprconnect.app.domain.model.DeviceType
import dev.hyprconnect.app.domain.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.cert.X509Certificate
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val discovery: DeviceDiscovery,
    private val client: HyprConnectClient,
    private val certificateStore: CertificateStore
) : DeviceRepository {

    private val TAG = "DeviceRepository"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _pairedDevices = MutableStateFlow<List<Device>>(emptyList())
    override val pairedDevices: Flow<List<Device>> = _pairedDevices.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    override val discoveredDevices: Flow<List<Device>> = _discoveredDevices.asStateFlow()

    private var discoveryJob: kotlinx.coroutines.Job? = null
    private var currentPairingDevice: Device? = null

    override fun startDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            discovery.discoverDevices().collect { devices ->
                _discoveredDevices.value = devices
            }
        }
    }

    override fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
    }

    override suspend fun pairDevice(device: Device): Boolean {
        currentPairingDevice = device
        // 1. Initiate TLS Handshake (Insecure)
        if (!client.connect(device.host, device.port, trustAll = true)) {
            Log.e(TAG, "Failed to connect for pairing")
            return false
        }

        // 2. We get the Host Certificate from the TLS session
        val hostCert = client.getPeerCertificate() ?: return false

        // 3. Send pair.request with our Client Certificate
        val selfCert = certificateStore.getSelfCertificate()
        val encodedCert = Base64.getEncoder().encodeToString(selfCert.encoded)
        
        val pairRequest = JsonRpcRequest(
            method = "pair.request",
            params = buildJsonObject {
                put("cert", encodedCert)
                put("device_name", android.os.Build.MODEL)
                put("device_type", "phone")
            },
            id = 1
        )
        
        val sent = client.sendRequest(pairRequest)
        if (sent) {
            Log.d(TAG, "Pairing request sent to ${device.name}")
            return true
        }
        return false
    }

    override suspend fun handlePairingCompleted(deviceId: String, deviceName: String, plugins: List<String>): Boolean {
        Log.d(TAG, "Pairing completed for $deviceId ($deviceName), plugins: $plugins")

        val hostCert = client.getPeerCertificate()
        if (hostCert == null) {
            Log.e(TAG, "Cannot complete pairing: No peer certificate found")
            return false
        }

        // Save certificate
        certificateStore.addTrustedCertificate(deviceId, hostCert)
        Log.d(TAG, "Saved certificate for $deviceId")

        // Update UI state
        val device = currentPairingDevice?.copy(
            id = deviceId,
            name = deviceName,
            isPaired = true,
            status = DeviceStatus.Connected,
            availablePlugins = plugins
        ) ?: Device(
            id = deviceId,
            name = deviceName,
            host = "",
            port = 0,
            type = DeviceType.DESKTOP,
            isPaired = true,
            status = DeviceStatus.Connected,
            availablePlugins = plugins
        )

        _pairedDevices.value = _pairedDevices.value.filter { it.id != deviceId } + device

        // Reconnect with trustAll=false
        scope.launch {
            Log.d(TAG, "Reconnecting to $deviceId with mutual trust...")
            val host = currentPairingDevice?.host ?: return@launch
            val port = currentPairingDevice?.port ?: return@launch

            client.disconnect()
            val success = client.connect(host, port, trustAll = false)
            if (success) {
                Log.d(TAG, "Reconnect success: Trusted connection established")
            } else {
                Log.e(TAG, "Reconnect failed after pairing approval")
            }
        }

        return true
    }

    override suspend fun unpairDevice(deviceId: String) {
        _pairedDevices.value = _pairedDevices.value.filter { it.id != deviceId }
    }

    override suspend fun connectToDevice(device: Device): Boolean {
        return client.connect(device.host, device.port, trustAll = false)
    }

    override fun disconnect() {
        client.disconnect()
    }
}
