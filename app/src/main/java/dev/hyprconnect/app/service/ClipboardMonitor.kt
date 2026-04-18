package dev.hyprconnect.app.service

import android.content.ClipboardManager
import android.content.Context
import android.app.ActivityManager
import android.os.Process
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hyprconnect.app.data.remote.HyprConnectClient
import dev.hyprconnect.app.data.remote.JsonRpcRequest
import dev.hyprconnect.app.domain.model.ClipboardEntry
import dev.hyprconnect.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_HISTORY = 20

@Singleton
class ClipboardMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: HyprConnectClient,
    private val settingsRepository: SettingsRepository
) {
    private val tag = "ClipboardMonitor"
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var lastClipboardContent: String? = null
    @Volatile private var lastRemoteUpdateTime: Long = 0
    @Volatile private var pendingRemoteClipboard: String? = null

    private val _history = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    val history: StateFlow<List<ClipboardEntry>> = _history.asStateFlow()

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        scope.launch {
            if (!settingsRepository.clipboardSync.first()) {
                Log.d(tag, "Clipboard sync disabled, skipping local clipboard change")
                return@launch
            }

            // Ignore the callback that fires immediately after we apply remote content.
            if (System.currentTimeMillis() - lastRemoteUpdateTime < 500) {
                Log.d(tag, "Skipping local clipboard read after recent remote apply")
                return@launch
            }

            if (!isAppInForeground()) {
                // Android 10+: clipboard reads are restricted in background.
                // onAppForeground() will handle it when the user returns to the app.
                Log.d(tag, "Skipping local clipboard read: app is in background")
                return@launch
            }

            sendLocalClipboard()
        }
    }

    fun start() {
        clipboard.addPrimaryClipChangedListener(listener)
        // Poll only for applying pending remote clipboard — foreground detection
        // is now handled reliably via onAppForeground() called from MainActivity.onResume().
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

    /**
     * Called from MainActivity.onResume() whenever the app comes to foreground.
     * Reads the current clipboard and sends to PC if content changed while we were away.
     */
    fun onAppForeground() {
        scope.launch {
            if (settingsRepository.clipboardSync.first()) {
                Log.d(tag, "App came to foreground, checking local clipboard for changes")
                sendLocalClipboard()
            }
        }
    }

    /**
     * Called by HyprConnectService when the remote PC pushes a new clipboard value.
     * Android restricts clipboard reads in the background, not writes — attempt directly.
     */
    fun setRemoteClipboard(text: String) {
        Log.d(tag, "Received remote clipboard content (length=${text.length})")
        if (!applyClipboardInternal(text, ClipboardEntry.Source.REMOTE)) {
            pendingRemoteClipboard = text
            Log.d(tag, "Queued remote clipboard content for later apply (length=${text.length})")
        }
    }

    /**
     * Re-copy a history entry to the local clipboard and push it to the PC.
     * Sets lastClipboardContent first so the listener doesn't echo back.
     */
    fun resend(content: String) {
        lastClipboardContent = content
        scope.launch {
            try {
                val clip = android.content.ClipData.newPlainText("HyprConnect", content)
                clipboard.setPrimaryClip(clip)
            } catch (e: Exception) {
                Log.w(tag, "Failed to set clipboard for resend", e)
            }
            val request = JsonRpcRequest(
                method = "clipboard.set",
                params = buildJsonObject { put("content", content) }
            )
            val sent = client.sendRequest(request)
            Log.d(tag, "Resent clipboard entry to PC (success=$sent, length=${content.length})")
        }
    }

    private fun tryApplyPendingRemoteClipboard(): Boolean {
        val pending = pendingRemoteClipboard ?: return false
        Log.d(tag, "Applying queued remote clipboard content (length=${pending.length})")
        return if (applyClipboardInternal(pending, ClipboardEntry.Source.REMOTE)) {
            pendingRemoteClipboard = null
            true
        } else {
            false
        }
    }

    private fun applyClipboardInternal(text: String, source: ClipboardEntry.Source): Boolean {
        return try {
            val clip = android.content.ClipData.newPlainText("HyprConnect", text)
            clipboard.setPrimaryClip(clip)
            lastClipboardContent = text
            lastRemoteUpdateTime = System.currentTimeMillis()
            addToHistory(ClipboardEntry(text, source))
            Log.d(tag, "Applied clipboard content from $source (length=${text.length})")
            true
        } catch (se: SecurityException) {
            Log.w(tag, "Clipboard write blocked by Android restrictions", se)
            false
        }
    }

    private suspend fun sendLocalClipboard() {
        val text = try {
            val clip = clipboard.primaryClip ?: return
            if (clip.itemCount == 0) return
            clip.getItemAt(0)?.text?.toString() ?: return
        } catch (se: SecurityException) {
            Log.w(tag, "Clipboard read blocked by Android privacy restrictions", se)
            return
        }

        if (text == lastClipboardContent) {
            Log.d(tag, "Skipping clipboard send: content unchanged")
            return
        }
        lastClipboardContent = text

        addToHistory(ClipboardEntry(text, ClipboardEntry.Source.LOCAL))

        val request = JsonRpcRequest(
            method = "clipboard.set",
            params = buildJsonObject { put("content", text) }
        )
        val sent = client.sendRequest(request)
        Log.d(tag, "Sent clipboard.set to PC (success=$sent, length=${text.length})")
    }

    private fun addToHistory(entry: ClipboardEntry) {
        val current = _history.value
        // Deduplicate: if top entry has same content, just bump its timestamp.
        val updated = if (current.isNotEmpty() && current.first().content == entry.content) {
            listOf(entry) + current.drop(1)
        } else {
            listOf(entry) + current
        }
        _history.value = updated.take(MAX_HISTORY)
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
