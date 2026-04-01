package dev.hyprconnect.app.ui.pairing

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hyprconnect.app.data.remote.HyprConnectClient
import dev.hyprconnect.app.data.remote.JsonRpcRequest
import dev.hyprconnect.app.domain.model.DeviceStatus
import dev.hyprconnect.app.domain.repository.DeviceRepository
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

    private var currentDeviceId: String? = null

    fun startPairing(deviceId: String) {
        viewModelScope.launch {
            _error.value = null
            _connectionReady.value = false
            currentDeviceId = deviceId
            Log.d(TAG, "Starting pairing for device: $deviceId")
            _isPairing.value = true

            val device = deviceRepository.discoveredDevices.first().find { it.id == deviceId }

            if (device == null) {
                _error.value = "Device $deviceId not found in discovered list"
                Log.e(TAG, "Device $deviceId not found in discovered list")
                _isPairing.value = false
                return@launch
            }

            val success = deviceRepository.pairDevice(device)
            if (success) {
                Log.d(TAG, "Pairing request sent, waiting for user to enter code")
                _connectionReady.value = true
            } else {
                _error.value = "Failed to initiate pairing handshake"
                Log.e(TAG, "Failed to initiate pairing handshake")
            }
            _isPairing.value = false
        }
    }

    fun submitCode(code: String) {
        val deviceId = currentDeviceId
        if (deviceId == null) {
            _error.value = "No active pairing session"
            return
        }

        viewModelScope.launch {
            _error.value = null
            _isSubmitting.value = true
            Log.d(TAG, "Submitting pairing code for device: $deviceId")

            val request = JsonRpcRequest(
                method = "pair.complete",
                params = buildJsonObject {
                    put("device_id", deviceId)
                    put("code", code)
                },
                id = 2
            )

            // Listen for the response before sending
            val responseJob = viewModelScope.launch {
                client.incomingMessages
                    .filter { it.id == 2 }
                    .first()
                    .let { message ->
                        if (message.error != null) {
                            _error.value = message.error.message
                            Log.e(TAG, "pair.complete error: ${message.error.message}")
                            _isSubmitting.value = false
                            return@launch
                        }

                        val result = message.result
                        if (result is JsonObject) {
                            val approvedDeviceId = result["device_id"]?.jsonPrimitive?.contentOrNull ?: deviceId
                            val deviceName = result["device_name"]?.jsonPrimitive?.contentOrNull ?: deviceId
                            val plugins = result["plugins"]?.jsonArray
                                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                ?: emptyList()

                            Log.d(TAG, "Pairing approved. Plugins: $plugins")
                            val completed = deviceRepository.handlePairingCompleted(
                                approvedDeviceId, deviceName, plugins
                            )
                            if (completed) {
                                _isPaired.emit(true)
                            } else {
                                _error.value = "Failed to finalize pairing"
                            }
                        } else {
                            _error.value = "Unexpected response format"
                        }
                        _isSubmitting.value = false
                    }
            }

            val sent = client.sendRequest(request)
            if (!sent) {
                responseJob.cancel()
                _error.value = "Failed to send pairing code"
                _isSubmitting.value = false
            }
        }
    }
}
