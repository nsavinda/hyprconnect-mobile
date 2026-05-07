package dev.hyprconnect.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hyprconnect.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val deviceName: StateFlow<String> = settingsRepository.deviceName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val notificationSync: StateFlow<Boolean> = settingsRepository.notificationSync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val clipboardSync: StateFlow<Boolean> = settingsRepository.clipboardSync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val quicTransfer: StateFlow<Boolean> = settingsRepository.quicTransfer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val maxConcurrentTransfers: StateFlow<Int> = settingsRepository.maxConcurrentTransfers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)

    val transferPriority: StateFlow<String> = settingsRepository.transferPriority
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "off")

    fun updateDeviceName(name: String) {
        viewModelScope.launch {
            settingsRepository.updateDeviceName(name)
        }
    }

    fun setNotificationSync(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationSync(enabled)
        }
    }

    fun setClipboardSync(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setClipboardSync(enabled)
        }
    }

    fun setQuicTransfer(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setQuicTransfer(enabled)
        }
    }

    fun setMaxConcurrentTransfers(value: Int) {
        viewModelScope.launch {
            settingsRepository.setMaxConcurrentTransfers(value)
        }
    }

    fun setTransferPriority(value: String) {
        viewModelScope.launch {
            settingsRepository.setTransferPriority(value)
        }
    }

    fun clearAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.clearAllData()
            onComplete()
        }
    }
}
