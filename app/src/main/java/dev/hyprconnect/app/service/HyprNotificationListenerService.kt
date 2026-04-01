package dev.hyprconnect.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dev.hyprconnect.app.data.remote.HyprConnectClient
import dev.hyprconnect.app.data.remote.JsonRpcRequest
import dev.hyprconnect.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

@AndroidEntryPoint
class HyprNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var client: HyprConnectClient
    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val TAG = "HyprNotificationListener"

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        scope.launch {
            if (!settingsRepository.notificationSync.first()) return@launch

            val notification = sbn.notification
            val extras = notification.extras
            val title = extras.getString("android.title") ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            val packageName = sbn.packageName

            val request = JsonRpcRequest(
                method = "notification.push",
                params = buildJsonObject {
                    put("id", sbn.id)
                    put("package", packageName)
                    put("title", title)
                    put("body", text)
                    put("timestamp", sbn.postTime)
                }
            )
            client.sendRequest(request)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        scope.launch {
            if (!settingsRepository.notificationSync.first()) return@launch

            val request = JsonRpcRequest(
                method = "notification.dismiss",
                params = buildJsonObject {
                    put("id", sbn.id)
                    put("package", sbn.packageName)
                }
            )
            client.sendRequest(request)
        }
    }
}
