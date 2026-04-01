package dev.hyprconnect.app.service

import android.content.ClipboardManager
import android.content.Context
import android.app.ActivityManager
import android.os.Process
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hyprconnect.app.data.remote.HyprConnectClient
import dev.hyprconnect.app.data.remote.JsonRpcRequest
import dev.hyprconnect.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: HyprConnectClient,
    private val settingsRepository: SettingsRepository
) {
    private val tag = "ClipboardMonitor"
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastClipboardContent: String? = null
    private var lastRemoteUpdateTime: Long = 0
    @Volatile private var pendingRemoteClipboard: String? = null

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        scope.launch {
            if (!settingsRepository.clipboardSync.first()) {
                Log.d(tag, "Clipboard sync disabled, skipping local clipboard change")
                return@launch
            }

            if (!isAppInForeground()) {
                Log.d(tag, "Skipping local clipboard read: app is in background (Android clipboard restriction)")
                return@launch
            }
            
            // Ignore immediate callback caused by applying remote clipboard content.
            if (System.currentTimeMillis() - lastRemoteUpdateTime < 500) {
                Log.d(tag, "Skipping local clipboard read after recent remote apply")
                return@launch
            }

            val text = try {
                val clip = clipboard.primaryClip ?: return@launch
                if (clip.itemCount == 0) return@launch
                val item = clip.getItemAt(0) ?: return@launch
                item.text?.toString() ?: return@launch
            } catch (se: SecurityException) {
                Log.w(tag, "Clipboard read blocked by Android privacy restrictions", se)
                return@launch
            }

            if (text == lastClipboardContent) {
                Log.d(tag, "Skipping clipboard update because content is unchanged")
                return@launch
            }
            
            // Rate limit remote updates
            if (System.currentTimeMillis() - lastRemoteUpdateTime < 500) {
                Log.d(tag, "Skipping clipboard update due to rate limit")
                return@launch
            }

            lastClipboardContent = text
            val request = JsonRpcRequest(
                method = "clipboard.set",
                params = buildJsonObject {
                    put("content", text)
                }
            )
            val sent = client.sendRequest(request)
            Log.d(tag, "Sent clipboard.set to remote peer (success=$sent, length=${text.length})")
        }
    }

    fun start() {
        clipboard.addPrimaryClipChangedListener(listener)
        scope.launch {
            while (isActive) {
                tryApplyPendingRemoteClipboard()
                delay(500)
            }
        }
    }

    fun stop() {
        clipboard.removePrimaryClipChangedListener(listener)
    }

    fun setRemoteClipboard(text: String) {
        if (!isAppInForeground()) {
            pendingRemoteClipboard = text
            Log.d(tag, "Queued remote clipboard content until app is foreground (length=${text.length})")
            return
        }

        Log.d(tag, "Applying remote clipboard content (length=${text.length})")
        applyClipboardInternal(text)
    }

    private fun tryApplyPendingRemoteClipboard() {
        val pending = pendingRemoteClipboard ?: return
        if (!isAppInForeground()) return

        Log.d(tag, "Applying queued remote clipboard content after app returned to foreground")
        if (applyClipboardInternal(pending)) {
            pendingRemoteClipboard = null
        }
    }

    private fun applyClipboardInternal(text: String): Boolean {
        lastClipboardContent = text
        lastRemoteUpdateTime = System.currentTimeMillis()
        try {
            val clip = android.content.ClipData.newPlainText("HyprConnect", text)
            clipboard.setPrimaryClip(clip)
            return true
        } catch (se: SecurityException) {
            Log.w(tag, "Clipboard write blocked by Android restrictions", se)
            return false
        }
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val myPid = Process.myPid()
        val processes = activityManager.runningAppProcesses ?: return false
        val processInfo = processes.firstOrNull { it.pid == myPid } ?: return false

        return processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
            processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }
}
