package dev.hyprconnect.app.data.repository

import android.util.Log
import dev.hyprconnect.app.data.local.CertificateStore
import dev.hyprconnect.app.data.remote.DeviceDiscovery
import dev.hyprconnect.app.data.remote.HyprConnectClient
import dev.hyprconnect.app.data.remote.JsonRpcRequest
import dev.hyprconnect.app.domain.model.Device
import dev.hyprconnect.app.domain.model.DeviceStatus
import dev.hyprconnect.app.domain.model.DeviceType
import dev.hyprconnect.app.domain.model.Workspace
import dev.hyprconnect.app.domain.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.put
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
    private var nextRequestId: Int = 1000

    init {
        // Observe connection state to update device status
        client.isConnected
            .onEach { isConnected ->
                Log.d(TAG, "Client connection state changed: $isConnected")
                updateAllDevicesStatus(if (isConnected) DeviceStatus.Connected else DeviceStatus.Disconnected)
            }
            .launchIn(scope)
    }

    private fun updateAllDevicesStatus(status: DeviceStatus) {
        _pairedDevices.value = _pairedDevices.value.map { 
            it.copy(status = status)
        }
    }

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

    override suspend fun pairDevice(device: Device): String? {
        currentPairingDevice = device
        if (!client.connect(device.host, device.port, trustAll = true)) {
            Log.e(TAG, "Failed to connect for pairing")
            return null
        }

        val selfCert = certificateStore.getSelfCertificate()
        val encodedCert = Base64.getEncoder().encodeToString(selfCert.encoded)
        val pairingDeviceName = android.os.Build.MODEL

        val pairRequest = JsonRpcRequest(
            method = "pair.request",
            params = buildJsonObject {
                put("cert", encodedCert)
                put("device_name", pairingDeviceName)
                put("device_type", "phone")
            },
            id = 1
        )

        val sent = client.sendRequest(pairRequest)
        if (sent) {
            Log.d(TAG, "Pairing request sent to ${device.name}, pairingDeviceId=$pairingDeviceName")
            return pairingDeviceName
        }
        return null
    }

    override suspend fun handlePairingCompleted(deviceId: String, deviceName: String, plugins: List<String>): Boolean {
        Log.d(TAG, "Pairing completed for $deviceId ($deviceName), plugins: $plugins")

        val hostCert = client.getPeerCertificate()
        if (hostCert == null) {
            Log.e(TAG, "Cannot complete pairing: No peer certificate found")
            return false
        }

        certificateStore.addTrustedCertificate(deviceId, hostCert)

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

        // Reconnect with trustAll=false to establish trusted session
        scope.launch {
            val host = currentPairingDevice?.host ?: return@launch
            val port = currentPairingDevice?.port ?: return@launch
            client.disconnect()
            client.connect(host, port, trustAll = false)
        }

        return true
    }

    override suspend fun unpairDevice(deviceId: String) {
        _pairedDevices.value = _pairedDevices.value.filter { it.id != deviceId }
    }

    override suspend fun connectToDevice(device: Device): Boolean {
        currentPairingDevice = device // Track this so we can reconnect if needed
        return client.connect(device.host, device.port, trustAll = false)
    }

    override fun disconnect() {
        client.disconnect()
    }

    override suspend fun listWorkspaces(): List<Workspace> {
        val requestId = nextRpcId()
        val request = JsonRpcRequest(
            method = "workspace.list",
            id = requestId
        )

        if (!client.sendRequest(request)) {
            Log.w(TAG, "workspace.list send failed")
            return emptyList()
        }

        val response = awaitResponse(requestId) ?: return emptyList()
        if (response.error != null) {
            Log.w(TAG, "workspace.list error: ${response.error.message}")
            return emptyList()
        }

        val resultObject = response.result as? JsonObject ?: return emptyList()
        val workspaces = resultObject["workspaces"] as? JsonArray ?: return emptyList()

        return workspaces.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            Workspace(
                id = id,
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: id.toString(),
                monitor = obj["monitor"]?.jsonPrimitive?.contentOrNull,
                windows = obj["windows"]?.jsonPrimitive?.intOrNull ?: 0,
                isActive = obj["active"]?.jsonPrimitive?.booleanOrNull ?: false
            )
        }.sortedBy { it.id }
    }

    override suspend fun switchWorkspace(id: Int): Boolean {
        val requestId = nextRpcId()
        val request = JsonRpcRequest(
            method = "workspace.switch",
            params = buildJsonObject {
                put("id", id)
            },
            id = requestId
        )

        if (!client.sendRequest(request)) {
            Log.w(TAG, "workspace.switch send failed for id=$id")
            return false
        }

        val response = awaitResponse(requestId) ?: return false
        if (response.error != null) {
            Log.w(TAG, "workspace.switch error: ${response.error.message}")
            return false
        }

        return true
    }

    override suspend fun getSystemVolume(): Float? {
        val requestId = nextRpcId()
        val request = JsonRpcRequest(method = "system.volume.get", id = requestId)
        if (!client.sendRequest(request)) {
            Log.w(TAG, "system.volume.get send failed")
            return null
        }

        val response = awaitResponse(requestId) ?: return null
        if (response.error != null) {
            Log.w(TAG, "system.volume.get error: ${response.error.message}")
            return null
        }

        val resultObject = response.result as? JsonObject ?: return null
        return resultObject["level"]?.jsonPrimitive?.floatOrNull
    }

    override suspend fun setSystemVolume(level: Float): Boolean {
        val requestId = nextRpcId()
        val request = JsonRpcRequest(
            method = "system.volume.set",
            params = buildJsonObject { put("level", level) },
            id = requestId
        )
        if (!client.sendRequest(request)) {
            Log.w(TAG, "system.volume.set send failed")
            return false
        }

        val response = awaitResponse(requestId) ?: return false
        if (response.error != null) {
            Log.w(TAG, "system.volume.set error: ${response.error.message}")
            return false
        }
        return true
    }

    override suspend fun getSystemBrightness(): Float? {
        val requestId = nextRpcId()
        val request = JsonRpcRequest(method = "system.brightness.get", id = requestId)
        if (!client.sendRequest(request)) {
            Log.w(TAG, "system.brightness.get send failed")
            return null
        }

        val response = awaitResponse(requestId) ?: return null
        if (response.error != null) {
            Log.w(TAG, "system.brightness.get error: ${response.error.message}")
            return null
        }

        val resultObject = response.result as? JsonObject ?: return null
        return resultObject["level"]?.jsonPrimitive?.floatOrNull
    }

    override suspend fun setSystemBrightness(level: Float): Boolean {
        val requestId = nextRpcId()
        val request = JsonRpcRequest(
            method = "system.brightness.set",
            params = buildJsonObject { put("level", level) },
            id = requestId
        )
        if (!client.sendRequest(request)) {
            Log.w(TAG, "system.brightness.set send failed")
            return false
        }

        val response = awaitResponse(requestId) ?: return false
        if (response.error != null) {
            Log.w(TAG, "system.brightness.set error: ${response.error.message}")
            return false
        }
        return true
    }

    private fun nextRpcId(): Int {
        nextRequestId += 1
        return nextRequestId
    }

    private suspend fun awaitResponse(requestId: Int) = withTimeout(5000) {
        client.incomingMessages.first { message ->
            message.id == requestId && message.isResponse()
        }
    }
}
