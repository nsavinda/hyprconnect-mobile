package dev.hyprconnect.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.hyprconnect.app.data.local.CertificateStore
import dev.hyprconnect.app.data.remote.HyprConnectClient
import dev.hyprconnect.app.data.remote.JsonRpcRequest
import dev.hyprconnect.app.domain.repository.DeviceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class HyprConnectService : Service() {

    @Inject lateinit var client: HyprConnectClient
    @Inject lateinit var deviceRepository: DeviceRepository
    @Inject lateinit var clipboardMonitor: ClipboardMonitor
    @Inject lateinit var certificateStore: CertificateStore

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
                    sendBatteryUpdate()
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
        val params = request.params
        Log.d(TAG, "Handling method: ${request.method}")
        when (request.method) {
            "clipboard.set" -> {
                if (params is JsonObject) {
                    val content = params["content"]?.jsonPrimitive?.contentOrNull
                    content?.let { 
                        Log.d(TAG, "Setting remote clipboard content")
                        clipboardMonitor.setRemoteClipboard(it) 
                    }
                }
            }
            "pair.approved" -> {
                Log.d(TAG, "Processing pair.approved notification")
                if (params is JsonObject) {
                    val deviceId = params["device_id"]?.jsonPrimitive?.contentOrNull
                    val deviceName = params["device_name"]?.jsonPrimitive?.contentOrNull
                    Log.d(TAG, "Extracted deviceId: $deviceId, deviceName: $deviceName")
                    if (deviceId != null && deviceName != null) {
                        val userCode = deviceRepository.getPairingCode()
                        val remoteCert = client.getPeerCertificate()
                        val localCert = certificateStore.getSelfCertificate()

                        if (userCode != null && remoteCert != null) {
                            val calculatedCode = calculateVerificationCode(localCert, remoteCert)
                            if (userCode == calculatedCode) {
                                Log.i(TAG, "SAS Verification Successful for $deviceId")
                                serviceScope.launch {
                                    try {
                                        val success = deviceRepository.handlePairingApproved(deviceId, deviceName)
                                        Log.d(TAG, "handlePairingApproved result: $success")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error handling pairing approval: ${e.message}", e)
                                    }
                                }
                            } else {
                                Log.e(TAG, "SECURITY ALERT: SAS Mismatch! Expected $calculatedCode, got $userCode. Possible MITM attack.")
                                client.disconnect()
                            }
                        } else {
                            Log.w(TAG, "Missing pairing code or certificates for verification")
                        }
                    } else {
                        Log.w(TAG, "pair.approved missing device_id or device_name")
                    }
                } else {
                    Log.w(TAG, "pair.approved params is not a JsonObject")
                }
            }
            else -> {
                Log.d(TAG, "No handler for method: ${request.method}")
            }
        }
    }

    private fun calculateVerificationCode(local: X509Certificate, remote: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val fingerprints = listOf(digest.digest(local.encoded), digest.digest(remote.encoded))
            .sortedWith { a, b ->
                val len = minOf(a.size, b.size)
                for (i in 0 until len) {
                    val res = (a[i].toInt() and 0xFF).compareTo(b[i].toInt() and 0xFF)
                    if (res != 0) return@sortedWith res
                }
                a.size.compareTo(b.size)
            }

        val combinedDigest = MessageDigest.getInstance("SHA-256")
        combinedDigest.update(fingerprints[0])
        combinedDigest.update(fingerprints[1])
        val hash = combinedDigest.digest()
        
        val num = ByteBuffer.wrap(hash.take(4).toByteArray())
            .order(ByteOrder.BIG_ENDIAN)
            .int.toLong() and 0xFFFFFFFFL
        
        return String.format(Locale.US, "%06d", num % 1000000)
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
