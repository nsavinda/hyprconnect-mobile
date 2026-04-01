package dev.hyprconnect.app.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hyprconnect.app.domain.model.Device
import dev.hyprconnect.app.domain.model.Workspace
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

    private val _workspaces = MutableStateFlow<List<Workspace>>(emptyList())
    val workspaces: StateFlow<List<Workspace>> = _workspaces.asStateFlow()

    private val _isWorkspaceLoading = MutableStateFlow(false)
    val isWorkspaceLoading: StateFlow<Boolean> = _isWorkspaceLoading.asStateFlow()

    private val _workspaceActionError = MutableStateFlow<String?>(null)
    val workspaceActionError: StateFlow<String?> = _workspaceActionError.asStateFlow()

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

    fun loadWorkspaces() {
        viewModelScope.launch {
            _isWorkspaceLoading.value = true
            _workspaceActionError.value = null
            try {
                _workspaces.value = deviceRepository.listWorkspaces()
            } catch (e: Exception) {
                _workspaceActionError.value = e.message ?: "Failed to load workspaces"
            } finally {
                _isWorkspaceLoading.value = false
            }
        }
    }

    fun switchWorkspace(id: Int) {
        viewModelScope.launch {
            _workspaceActionError.value = null
            val success = deviceRepository.switchWorkspace(id)
            if (!success) {
                _workspaceActionError.value = "Failed to switch workspace"
                return@launch
            }

            _workspaces.value = deviceRepository.listWorkspaces()
        }
    }
}
