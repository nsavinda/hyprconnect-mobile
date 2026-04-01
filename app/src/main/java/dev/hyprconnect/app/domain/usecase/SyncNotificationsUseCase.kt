package dev.hyprconnect.app.domain.usecase

import dev.hyprconnect.app.domain.repository.SettingsRepository
import javax.inject.Inject

class SyncNotificationsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend fun setEnabled(enabled: Boolean) {
        settingsRepository.setNotificationSync(enabled)
    }
}
