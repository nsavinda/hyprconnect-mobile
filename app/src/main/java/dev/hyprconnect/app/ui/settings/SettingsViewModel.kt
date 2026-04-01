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
}
