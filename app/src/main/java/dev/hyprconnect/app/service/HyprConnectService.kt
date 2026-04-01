package dev.hyprconnect.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.hyprconnect.app.data.remote.HyprConnectClient
import dev.hyprconnect.app.data.remote.JsonRpcRequest
import dev.hyprconnect.app.domain.repository.DeviceRepository
import dev.hyprconnect.app.domain.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.*
import javax.inject.Inject

@AndroidEntryPoint
class HyprConnectService : Service() {

    @Inject lateinit var client: HyprConnectClient
    @Inject lateinit var deviceRepository: DeviceRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var clipboardMonitor: ClipboardMonitor

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val TAG = "HyprConnectService"
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "hyprconnect_service"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        clipboardMonitor.start()
        startBatteryUpdates()
        listenForMessages()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "HyprConnect Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HyprConnect")
            .setContentText("Service is running")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startBatteryUpdates() {
        serviceScope.launch {
            while (isActive) {
                try {
                    if (settingsRepository.batteryReporting.first()) {
                        sendBatteryUpdate()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Battery update failed: ${e.message}")
                }
                delay(60000)
            }
        }
    }

    private suspend fun sendBatteryUpdate() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                         status == BatteryManager.BATTERY_STATUS_FULL

        val update = JsonRpcRequest(
            method = "battery.update",
            params = buildJsonObject {
                put("level", level)
                put("charging", isCharging)
                put("timestamp", System.currentTimeMillis() / 1000)
            }
        )
        client.sendRequest(update)
    }

    private fun listenForMessages() {
        Log.d(TAG, "Starting to listen for messages from client...")
        client.incomingRequests
            .onEach { request ->
                Log.d(TAG, "Received request in service: ${request.method}")
                handleRemoteRequest(request)
            }
            .launchIn(serviceScope)
    }

    private fun handleRemoteRequest(request: JsonRpcRequest) {
        serviceScope.launch {
            val params = request.params
            Log.d(TAG, "Handling method: ${request.method}")
            when (request.method) {
                "clipboard.set" -> {
                    if (settingsRepository.clipboardSync.first()) {
                        if (params is JsonObject) {
                            val content = params["content"]?.jsonPrimitive?.contentOrNull
                            content?.let {
                                Log.d(TAG, "Setting remote clipboard content")
                                clipboardMonitor.setRemoteClipboard(it)
                            }
                        }
                    } else {
                        Log.d(TAG, "Clipboard sync is disabled, ignoring request")
                    }
                }
                else -> {
                    Log.d(TAG, "No handler for method: ${request.method}")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        super.onDestroy()
        clipboardMonitor.stop()
        serviceScope.cancel()
    }
}
