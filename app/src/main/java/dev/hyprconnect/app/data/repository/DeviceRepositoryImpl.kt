package dev.hyprconnect.app.data.repository

import android.util.Log
import dev.hyprconnect.app.data.local.CertificateStore
import dev.hyprconnect.app.data.remote.DeviceDiscovery
import dev.hyprconnect.app.data.remote.HyprConnectClient
import dev.hyprconnect.app.data.remote.JsonRpcRequest
import dev.hyprconnect.app.domain.model.Device
import dev.hyprconnect.app.domain.model.DeviceStatus
import dev.hyprconnect.app.domain.model.DeviceType
import dev.hyprconnect.app.domain.model.NowPlaying
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
        client.isConnected
            .onEach { isConnected ->
                Log.d(TAG, "Client connection state changed: $isConnected")
                updateAllDevicesStatus(if (isConnected) DeviceStatus.Connected else DeviceStatus.Disconnected)
            }
            .launchIn(scope)
    }

    private fun updateAllDevicesStatus(status: DeviceStatus) {
        _pairedDevices.value = _pairedDevices.value.map { it.copy(status = status) }
    }

    override fun startDiscovery() {
        discoveryJob?.cancel()
        _discoveredDevices.value = emptyList()
        discoveryJob = scope.launch {
            discovery.discoverDevices().collect { devices ->
                _discoveredDevices.value = devices
            }
        }
    }

    override fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _discoveredDevices.value = emptyList()
    }

    override suspend fun pairDevice(device: Device): String? {
        currentPairingDevice = device
        var pairingTarget = device

        // If we're already connected to this host from a previous attempt, skip the
        // TLS handshake so we don't generate a second pairing code on the server.
        val alreadyConnected = client.isConnected.value &&
            client.connectedHost == pairingTarget.host
        if (alreadyConnected) {
            Log.d(TAG, "Already connected to ${pairingTarget.host}, reusing connection for pair.request")
            val pairingDeviceName = android.os.Build.MODEL
            val selfCert = certificateStore.getSelfCertificate()
            val encodedCert = java.util.Base64.getEncoder().encodeToString(selfCert.encoded)
            val pairRequest = JsonRpcRequest(
                method = "pair.request",
                params = buildJsonObject {
                    put("cert", encodedCert)
                    put("device_name", pairingDeviceName)
                    put("device_type", "phone")
                },
                id = 1
            )
            return if (client.sendRequest(pairRequest)) pairingDeviceName else null
        }

        val resolvedHost = tryConnectAlternates(pairingTarget, trustAll = true)
        if (resolvedHost == null) {
            val refreshed = findRefreshedDeviceTarget(device)
            if (refreshed == null) {
                Log.e(TAG, "Failed to connect for pairing")
                return null
            }

            Log.w(
                TAG,
                "Retrying pairing connection with refreshed endpoint ${refreshed.host}:${refreshed.port} (was ${pairingTarget.host}:${pairingTarget.port})"
            )
            pairingTarget = refreshed
            currentPairingDevice = refreshed

            val refreshedHost = tryConnectAlternates(pairingTarget, trustAll = true)
            if (refreshedHost == null) {
                Log.e(TAG, "Failed to connect for pairing after refresh retry")
                return null
            }
            pairingTarget = pairingTarget.copy(host = refreshedHost)
            currentPairingDevice = pairingTarget
        } else if (resolvedHost != pairingTarget.host) {
            pairingTarget = pairingTarget.copy(host = resolvedHost)
            currentPairingDevice = pairingTarget
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
            Log.d(TAG, "Pairing request sent to ${pairingTarget.name} at ${pairingTarget.host}:${pairingTarget.port}, pairingDeviceId=$pairingDeviceName")
            return pairingDeviceName
        }
        return null
    }

    private suspend fun findRefreshedDeviceTarget(original: Device): Device? {
        return try {
            withTimeout(10000) {
                discovery.discoverDevices()
                    .mapNotNull { discovered ->
                        discovered.firstOrNull { it.id == original.id && it.host != original.host }
                    }
                    .first()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh discovery for ${original.id}: ${e.message}")
            null
        }
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

        scope.launch {
            val target = currentPairingDevice ?: return@launch
            client.disconnect()
            tryConnectAlternates(target, trustAll = false)
        }

        return true
    }

    override suspend fun unpairDevice(deviceId: String) {
        _pairedDevices.value = _pairedDevices.value.filter { it.id != deviceId }
    }

    override suspend fun connectToDevice(device: Device): Boolean {
        currentPairingDevice = device
        val resolvedHost = tryConnectAlternates(device, trustAll = false) ?: return false
        if (resolvedHost != device.host) {
            currentPairingDevice = device.copy(host = resolvedHost)
        }
        return true
    }

    /**
     * Try the device's primary host first, then each address advertised in
     * the mDNS TXT record, preferring those on the same /24 subnet as one of
     * this phone's interfaces. Returns the first host that connects.
     */
    private suspend fun tryConnectAlternates(device: Device, trustAll: Boolean): String? {
        val candidates = buildCandidateList(device)
        for (host in candidates) {
            if (client.connect(host, device.port, trustAll = trustAll)) {
                if (host != device.host) {
                    Log.w(TAG, "Primary host ${device.host} unreachable; connected via alternate $host")
                }
                return host
            }
        }
        return null
    }

    private fun buildCandidateList(device: Device): List<String> {
        val all = LinkedHashSet<String>()
        all += device.host
        all += device.addresses
        if (all.size <= 1) return all.toList()

        val localPrefixes = localSubnetPrefixes()
        // Same-subnet first, then everything else, preserving original order.
        val (sameSubnet, other) = all.partition { ip ->
            localPrefixes.any { prefix -> ip.startsWith(prefix) }
        }
        return sameSubnet + other
    }

    private fun localSubnetPrefixes(): List<String> {
        return try {
            java.net.NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { ia ->
                    val addr = ia.address ?: return@mapNotNull null
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        // /24 prefix as a string ("10.39.214.")
                        addr.hostAddress?.substringBeforeLast('.')?.plus('.')
                    } else null
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read local interfaces: ${e.message}")
            emptyList()
        }
    }

    override fun disconnect() {
        client.disconnect()
    }

    override suspend fun listWorkspaces(): List<Workspace> {
        val request = JsonRpcRequest(method = "workspace.list", id = nextRpcId())
        val response = sendAndAwait(request) ?: return emptyList()
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
        val request = JsonRpcRequest(
            method = "workspace.switch",
            params = buildJsonObject { put("id", id) },
            id = nextRpcId()
        )
        val response = sendAndAwait(request) ?: return false
        if (response.error != null) {
            Log.w(TAG, "workspace.switch error: ${response.error.message}")
            return false
        }
        return true
    }

    override suspend fun getSystemVolume(): Float? {
        val response = sendAndAwait(JsonRpcRequest(method = "system.volume.get", id = nextRpcId())) ?: return null
        if (response.error != null) return null
        val resultObject = response.result as? JsonObject ?: return null
        return resultObject["level"]?.jsonPrimitive?.floatOrNull
    }

    override suspend fun setSystemVolume(level: Float): Boolean {
        val request = JsonRpcRequest(
            method = "system.volume.set",
            params = buildJsonObject { put("level", level) },
            id = nextRpcId()
        )
        val response = sendAndAwait(request) ?: return false
        return response.error == null
    }

    override suspend fun getSystemBrightness(): Float? {
        val response = sendAndAwait(JsonRpcRequest(method = "system.brightness.get", id = nextRpcId())) ?: return null
        if (response.error != null) return null
        val resultObject = response.result as? JsonObject ?: return null
        return resultObject["level"]?.jsonPrimitive?.floatOrNull
    }

    override suspend fun setSystemBrightness(level: Float): Boolean {
        val request = JsonRpcRequest(
            method = "system.brightness.set",
            params = buildJsonObject { put("level", level) },
            id = nextRpcId()
        )
        val response = sendAndAwait(request) ?: return false
        return response.error == null
    }

    override suspend fun mediaAction(action: String, player: String?): Boolean {
        val request = JsonRpcRequest(
            method = "media.$action",
            params = if (player != null) buildJsonObject { put("player", player) } else null,
            id = nextRpcId()
        )
        val response = sendAndAwait(request) ?: return false
        return response.error == null
    }

    override suspend fun getNowPlaying(): NowPlaying? {
        val response = sendAndAwait(JsonRpcRequest(method = "media.now_playing", id = nextRpcId())) ?: return null
        if (response.error != null) return null
        val result = response.result as? JsonObject ?: return null
        return NowPlaying(
            title = result["title"]?.jsonPrimitive?.contentOrNull ?: "",
            artist = result["artist"]?.jsonPrimitive?.contentOrNull ?: "",
            album = result["album"]?.jsonPrimitive?.contentOrNull ?: "",
            status = result["status"]?.jsonPrimitive?.contentOrNull ?: "stopped"
        )
    }

    override suspend fun inputMouseMove(dx: Float, dy: Float): Boolean {
        val request = JsonRpcRequest(
            method = "input.mouse.move",
            params = buildJsonObject {
                put("dx", dx)
                put("dy", dy)
            }
        )
        return client.sendRequest(request)
    }

    override suspend fun inputMouseClick(button: String): Boolean {
        val request = JsonRpcRequest(
            method = "input.mouse.click",
            params = buildJsonObject { put("button", button) },
            id = nextRpcId()
        )
        val response = sendAndAwait(request) ?: return false
        return response.error == null
    }

    override suspend fun inputMouseScroll(dx: Float, dy: Float): Boolean {
        val request = JsonRpcRequest(
            method = "input.mouse.scroll",
            params = buildJsonObject {
                put("dx", dx)
                put("dy", dy)
            }
        )
        return client.sendRequest(request)
    }

    override suspend fun inputKeyboardType(text: String): Boolean {
        val request = JsonRpcRequest(
            method = "input.keyboard.type",
            params = buildJsonObject { put("text", text) },
            id = nextRpcId()
        )
        val response = sendAndAwait(request) ?: return false
        return response.error == null
    }

    override suspend fun inputKeyboardKey(key: String, modifiers: List<String>): Boolean {
        val request = JsonRpcRequest(
            method = "input.keyboard.key",
            params = buildJsonObject {
                put("key", key)
                put("modifiers", kotlinx.serialization.json.JsonArray(modifiers.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            },
            id = nextRpcId()
        )
        val response = sendAndAwait(request) ?: return false
        return response.error == null
    }

    private fun nextRpcId(): Int {
        nextRequestId += 1
        return nextRequestId
    }

    // Registers the deferred BEFORE sending to avoid the race between
    // sendRequest completing and the response arriving on the IO thread.
    private suspend fun sendAndAwait(request: JsonRpcRequest) =
        client.sendRequestAwait(request, timeoutMs = 5_000)
}
