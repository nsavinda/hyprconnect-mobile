package dev.hyprconnect.app.data.repository

import dev.hyprconnect.app.data.local.CertificateStore
import dev.hyprconnect.app.data.local.SettingsDataStore
import dev.hyprconnect.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val certificateStore: CertificateStore
) : SettingsRepository {
    override val deviceName: Flow<String> = settingsDataStore.deviceName
    override val notificationSync: Flow<Boolean> = settingsDataStore.notificationSync
    override val clipboardSync: Flow<Boolean> = settingsDataStore.clipboardSync
    override val fileTransfer: Flow<Boolean> = settingsDataStore.fileTransfer
    override val mediaControl: Flow<Boolean> = settingsDataStore.mediaControl
    override val batteryReporting: Flow<Boolean> = settingsDataStore.batteryReporting
    override val quicTransfer: Flow<Boolean> = settingsDataStore.quicTransfer

    override suspend fun updateDeviceName(name: String) = settingsDataStore.updateDeviceName(name)
    override suspend fun setNotificationSync(enabled: Boolean) = settingsDataStore.setNotificationSync(enabled)
    override suspend fun setClipboardSync(enabled: Boolean) = settingsDataStore.setClipboardSync(enabled)
    override suspend fun setFileTransfer(enabled: Boolean) = settingsDataStore.setFileTransfer(enabled)
    override suspend fun setMediaControl(enabled: Boolean) = settingsDataStore.setMediaControl(enabled)
    override suspend fun setBatteryReporting(enabled: Boolean) = settingsDataStore.setBatteryReporting(enabled)
    override suspend fun setQuicTransfer(enabled: Boolean) = settingsDataStore.setQuicTransfer(enabled)

    override suspend fun clearAllData() {
        settingsDataStore.clearAll()
        certificateStore.clearTrustedCertificates()
    }
}
