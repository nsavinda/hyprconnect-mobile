package dev.hyprconnect.app.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hyprconnect.app.domain.model.Device
import dev.hyprconnect.app.domain.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceDetailViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _device = MutableStateFlow<Device?>(null)
    val device: StateFlow<Device?> = _device.asStateFlow()

    fun loadDevice(deviceId: String) {
        viewModelScope.launch {
            deviceRepository.pairedDevices.collect { devices ->
                _device.value = devices.find { it.id == deviceId }
            }
        }
    }

    fun connect() {
        _device.value?.let {
            viewModelScope.launch {
                deviceRepository.connectToDevice(it)
            }
        }
    }

    fun disconnect() {
        deviceRepository.disconnect()
    }
}
