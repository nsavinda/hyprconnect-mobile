package dev.hyprconnect.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hyprconnect.app.domain.model.Device
import dev.hyprconnect.app.domain.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    val discoveredDevices: StateFlow<List<Device>> = deviceRepository.discoveredDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pairedDevices: StateFlow<List<Device>> = deviceRepository.pairedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startDiscovery() {
        deviceRepository.startDiscovery()
    }

    fun stopDiscovery() {
        deviceRepository.stopDiscovery()
    }
}
