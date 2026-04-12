package dev.hyprconnect.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val deviceName: Flow<String>
    val notificationSync: Flow<Boolean>
    val clipboardSync: Flow<Boolean>
    val fileTransfer: Flow<Boolean>
    val mediaControl: Flow<Boolean>
    val batteryReporting: Flow<Boolean>
    val quicTransfer: Flow<Boolean>

    suspend fun updateDeviceName(name: String)
    suspend fun setNotificationSync(enabled: Boolean)
    suspend fun setClipboardSync(enabled: Boolean)
    suspend fun setFileTransfer(enabled: Boolean)
    suspend fun setMediaControl(enabled: Boolean)
    suspend fun setBatteryReporting(enabled: Boolean)
    suspend fun setQuicTransfer(enabled: Boolean)
    suspend fun clearAllData()
}
