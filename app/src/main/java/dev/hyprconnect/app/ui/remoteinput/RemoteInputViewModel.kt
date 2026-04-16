package dev.hyprconnect.app.ui.remoteinput

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hyprconnect.app.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemoteInputViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _showKeyboard = MutableStateFlow(false)
    val showKeyboard: StateFlow<Boolean> = _showKeyboard.asStateFlow()

    fun sendMouseMove(dx: Float, dy: Float) {
        // Fire-and-forget for low latency - no response awaited.
        viewModelScope.launch {
            deviceRepository.inputMouseMove(dx, dy)
        }
    }

    fun sendClick(button: String = "left") {
        viewModelScope.launch {
            deviceRepository.inputMouseClick(button)
        }
    }

    fun sendScroll(dx: Float, dy: Float) {
        viewModelScope.launch {
            deviceRepository.inputMouseScroll(dx, dy)
        }
    }

    fun sendText(text: String) {
        viewModelScope.launch {
            deviceRepository.inputKeyboardType(text)
        }
    }

    fun sendKey(key: String, modifiers: List<String> = emptyList()) {
        viewModelScope.launch {
            deviceRepository.inputKeyboardKey(key, modifiers)
        }
    }

    fun toggleKeyboard() {
        _showKeyboard.value = !_showKeyboard.value
    }
}
