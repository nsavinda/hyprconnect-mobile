package dev.hyprconnect.app.ui.pairing

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hyprconnect.app.data.remote.HyprConnectClient
import dev.hyprconnect.app.data.remote.JsonRpcRequest
import dev.hyprconnect.app.domain.model.DeviceStatus
import dev.hyprconnect.app.domain.repository.DeviceRepository
import dev.hyprconnect.app.domain.repository.PairingSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import javax.inject.Inject

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val client: HyprConnectClient
) : ViewModel() {

    private val TAG = "PairingViewModel"

    private val _isPairing = MutableStateFlow(false)
    val isPairing: StateFlow<Boolean> = _isPairing.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isPaired = MutableSharedFlow<Boolean>(replay = 1)
    val isPaired: SharedFlow<Boolean> = _isPaired.asSharedFlow()

    private val _connectionReady = MutableStateFlow(false)
    val connectionReady: StateFlow<Boolean> = _connectionReady.asStateFlow()

    /** SAS the user is asked to compare against the desktop. */
    private val _sas = MutableStateFlow<String?>(null)
    val sas: StateFlow<String?> = _sas.asStateFlow()

    /** True after the phone has confirmed match and is awaiting desktop. */
    private val _waitingForDesktop = MutableStateFlow(false)
    val waitingForDesktop: StateFlow<Boolean> = _waitingForDesktop.asStateFlow()

    /** Human-readable name of the paired daemon device. */
    private val _pairedDeviceName = MutableStateFlow<String?>(null)
    val pairedDeviceName: StateFlow<String?> = _pairedDeviceName.asStateFlow()

    // The device_id the daemon uses for this pairing session (our device name)
    private var pairingDeviceId: String? = null

    fun startPairing(deviceId: String) {
        viewModelScope.launch {
            _error.value = null
            _connectionReady.value = false
            _sas.value = null
            _waitingForDesktop.value = false
            Log.d(TAG, "Starting pairing for device: $deviceId")
            _isPairing.value = true

            val device = deviceRepository.discoveredDevices.first().find { it.id == deviceId }

            if (device == null) {
                _error.value = "Device $deviceId not found in discovered list"
                Log.e(TAG, "Device $deviceId not found in discovered list")
                _isPairing.value = false
                return@launch
            }

            val session: PairingSession? = deviceRepository.pairDevice(device)
            if (session != null) {
                pairingDeviceId = session.deviceId
                _sas.value = session.sas
                _connectionReady.value = true
                Log.d(TAG, "Pairing request acknowledged. device_id=${session.deviceId} sas=${session.sas}")
            } else {
                _error.value = "Failed to initiate pairing handshake"
                Log.e(TAG, "Failed to initiate pairing handshake")
            }
            _isPairing.value = false
        }
    }

    /**
     * Phone-side approval: the user has visually verified the SAS displayed
     * here matches the SAS shown on the desktop. The daemon waits for the
     * desktop to also approve before finalizing.
     */
    fun confirmMatch() {
        val deviceId = pairingDeviceId ?: run {
            _error.value = "No active pairing session"
            return
        }

        viewModelScope.launch {
            _error.value = null
            _isSubmitting.value = true
            Log.d(TAG, "Confirming SAS match for device: $deviceId")

            val request = JsonRpcRequest(
                method = "pair.complete",
                params = buildJsonObject { put("device_id", deviceId) },
                id = 2
            )

            val message = client.sendRequestAwait(request)
            if (message == null) {
                _error.value = "No response from server — check connection"
                _isSubmitting.value = false
                return@launch
            }
            if (message.error != null) {
                _error.value = message.error.message
                Log.e(TAG, "pair.complete error: ${message.error.message}")
                _isSubmitting.value = false
                return@launch
            }

            val result = message.result as? JsonObject
            if (result == null) {
                _error.value = "Unexpected response format"
                _isSubmitting.value = false
                return@launch
            }

            when (result["status"]?.jsonPrimitive?.contentOrNull) {
                "approved" -> finalizePairing(result, deviceId)
                "pending" -> {
                    _waitingForDesktop.value = true
                    Log.d(TAG, "Phone confirmed; waiting for desktop approval")
                    awaitDesktopApproval()
                }
                else -> _error.value = "Unexpected status: ${result["status"]}"
            }
            _isSubmitting.value = false
        }
    }

    private suspend fun awaitDesktopApproval() {
        // Daemon pushes a `pair.approved` notification when the desktop CLI
        // approves. The notification body has the same shape as the
        // pair.complete approved response.
        val notification = client.incomingMessages.first { msg ->
            msg.method == "pair.approved"
        }
        val params = notification.params as? JsonObject
        if (params == null) {
            _error.value = "Malformed pair.approved notification"
            _waitingForDesktop.value = false
            return
        }
        finalizePairing(params, pairingDeviceId ?: return)
    }

    private suspend fun finalizePairing(payload: JsonObject, fallbackDeviceId: String) {
        val approvedDeviceId = payload["device_id"]?.jsonPrimitive?.contentOrNull ?: fallbackDeviceId
        val deviceName = payload["device_name"]?.jsonPrimitive?.contentOrNull ?: approvedDeviceId
        val plugins = payload["plugins"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()

        Log.d(TAG, "Pairing approved. Plugins: $plugins")
        val completed = deviceRepository.handlePairingCompleted(approvedDeviceId, deviceName, plugins)
        if (completed) {
            _pairedDeviceName.value = deviceName
            _waitingForDesktop.value = false
            _isPaired.emit(true)
        } else {
            _error.value = "Failed to finalize pairing"
            _waitingForDesktop.value = false
        }
    }
}
