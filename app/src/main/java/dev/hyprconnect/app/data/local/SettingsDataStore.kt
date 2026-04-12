package dev.hyprconnect.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val NOTIFICATION_SYNC = booleanPreferencesKey("notification_sync")
        val CLIPBOARD_SYNC = booleanPreferencesKey("clipboard_sync")
        val FILE_TRANSFER = booleanPreferencesKey("file_transfer")
        val MEDIA_CONTROL = booleanPreferencesKey("media_control")
        val BATTERY_REPORTING = booleanPreferencesKey("battery_reporting")
        val QUIC_TRANSFER = booleanPreferencesKey("quic_transfer")
    }

    val deviceName: Flow<String> = context.dataStore.data.map { it[DEVICE_NAME] ?: android.os.Build.MODEL }
    val notificationSync: Flow<Boolean> = context.dataStore.data.map { it[NOTIFICATION_SYNC] ?: true }
    val clipboardSync: Flow<Boolean> = context.dataStore.data.map { it[CLIPBOARD_SYNC] ?: true }
    val fileTransfer: Flow<Boolean> = context.dataStore.data.map { it[FILE_TRANSFER] ?: true }
    val mediaControl: Flow<Boolean> = context.dataStore.data.map { it[MEDIA_CONTROL] ?: true }
    val batteryReporting: Flow<Boolean> = context.dataStore.data.map { it[BATTERY_REPORTING] ?: true }
    val quicTransfer: Flow<Boolean> = context.dataStore.data.map { it[QUIC_TRANSFER] ?: true }

    suspend fun updateDeviceName(name: String) {
        context.dataStore.edit { it[DEVICE_NAME] = name }
    }

    suspend fun setNotificationSync(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATION_SYNC] = enabled }
    }

    suspend fun setClipboardSync(enabled: Boolean) {
        context.dataStore.edit { it[CLIPBOARD_SYNC] = enabled }
    }

    suspend fun setFileTransfer(enabled: Boolean) {
        context.dataStore.edit { it[FILE_TRANSFER] = enabled }
    }

    suspend fun setMediaControl(enabled: Boolean) {
        context.dataStore.edit { it[MEDIA_CONTROL] = enabled }
    }

    suspend fun setBatteryReporting(enabled: Boolean) {
        context.dataStore.edit { it[BATTERY_REPORTING] = enabled }
    }

    suspend fun setQuicTransfer(enabled: Boolean) {
        context.dataStore.edit { it[QUIC_TRANSFER] = enabled }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
