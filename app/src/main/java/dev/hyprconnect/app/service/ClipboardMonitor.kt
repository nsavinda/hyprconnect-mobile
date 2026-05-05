package dev.hyprconnect.app.service

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.hyprconnect.app.R
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private const val CLIPBOARD_CHANNEL_ID = "clipboard_sync"
private const val CLIPBOARD_NOTIFICATION_ID = 2

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
            if (!settingsRepository.clipboardSync.first()) return@launch

            if (System.currentTimeMillis() - lastRemoteUpdateTime < 500) return@launch

            when {
                isAppInForeground() || hasBackgroundClipboardPermission() || isAccessibilityServiceEnabled() -> {
                    cancelSyncNotification()
                    sendLocalClipboard()
                }
                else -> {
                    if (!client.isConnected.value) return@launch

                    if (canDrawOverApps()) {
                        // Modal TYPE_APPLICATION_OVERLAY window (no FLAG_NOT_TOUCH_MODAL) steals WM
                        // input focus, making isUidFocused(uid)=true in ClipboardService.
                        // We skip the primaryClipDescription pre-check — Samsung denies even that
                        // in background, which would prevent the overlay from being attempted.
                        withContext(Dispatchers.Main) { readClipboardViaOverlay() }
                    } else {
                        val hasText = clipboard.primaryClipDescription
                            ?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                        if (hasText) showSyncNotification()
                    }
                }
            }
        }
    }

    /**
     * Adds a 1x1 modal TYPE_APPLICATION_OVERLAY window to steal WM input focus.
     *
     * Without FLAG_NOT_TOUCH_MODAL the window is modal — the WM transfers input focus
     * to it, making isUidFocused(uid)=true in ClipboardService. We wait for the
     * onWindowFocusChanged(true) callback rather than view.post(), because post() fires
     * on the next frame before the WM has finished the focus assignment.
     *
     * Must be called on the main thread.
     */
    private fun readClipboardViaOverlay() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, // no FLAG_NOT_TOUCH_MODAL → modal
            PixelFormat.TRANSPARENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        // Capture before the anonymous View subclass — inside it, `tag` would resolve to
        // View.getTag() (type Any!) and shadow ClipboardMonitor.tag (type String).
        val logTag = tag

        val view = object : View(context) {
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (!hasWindowFocus) return
                try {
                    val clip = clipboard.primaryClip
                    val text: String = clip?.getItemAt(0)?.text?.toString() ?: run {
                        if (clip == null) showSyncNotification() // null = denied, not empty
                        return
                    }
                    if (text != lastClipboardContent && client.isConnected.value) {
                        lastClipboardContent = text
                        cancelSyncNotification()
                        addToHistory(ClipboardEntry(text, ClipboardEntry.Source.LOCAL))
                        // Capture text as String before crossing coroutine boundary
                        // (smart cast doesn't survive lambda capture).
                        val content = text
                        scope.launch {
                            val sent = client.sendRequest(JsonRpcRequest(
                                method = "clipboard.set",
                                params = buildJsonObject { put("content", content) }
                            ))
                            Log.d(logTag, "Overlay clipboard sync sent=$sent length=${content.length}")
                        }
                    }
                } catch (e: SecurityException) {
                    Log.w(logTag, "Overlay clipboard denied: ${e.message}")
                    showSyncNotification()
                } finally {
                    runCatching { wm.removeView(this) }
                }
            }
        }

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            Log.e(tag, "Failed to add overlay view: ${e.message}")
            showSyncNotification()
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

    fun onAppForeground() {
        cancelSyncNotification()
        scope.launch {
            if (settingsRepository.clipboardSync.first()) sendLocalClipboard()
        }
    }

    fun setRemoteClipboard(text: String) {
        Log.d(tag, "Received remote clipboard content (length=${text.length})")
        if (!applyClipboardInternal(text, ClipboardEntry.Source.REMOTE)) {
            pendingRemoteClipboard = text
        }
    }

    fun resend(content: String) {
        lastClipboardContent = content
        scope.launch {
            try {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("HyprConnect", content))
            } catch (e: Exception) {
                Log.w(tag, "Failed to set clipboard for resend", e)
            }
            val sent = client.sendRequest(JsonRpcRequest(
                method = "clipboard.set",
                params = buildJsonObject { put("content", content) }
            ))
            Log.d(tag, "Resent clipboard entry to PC (success=$sent, length=${content.length})")
        }
    }

    private fun tryApplyPendingRemoteClipboard(): Boolean {
        val pending = pendingRemoteClipboard ?: return false
        return if (applyClipboardInternal(pending, ClipboardEntry.Source.REMOTE)) {
            pendingRemoteClipboard = null
            true
        } else false
    }

    private fun applyClipboardInternal(text: String, source: ClipboardEntry.Source): Boolean {
        return try {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("HyprConnect", text))
            lastClipboardContent = text
            lastRemoteUpdateTime = System.currentTimeMillis()
            addToHistory(ClipboardEntry(text, source))
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
        if (text == lastClipboardContent) return
        lastClipboardContent = text
        addToHistory(ClipboardEntry(text, ClipboardEntry.Source.LOCAL))
        val sent = client.sendRequest(JsonRpcRequest(
            method = "clipboard.set",
            params = buildJsonObject { put("content", text) }
        ))
        Log.d(tag, "Sent clipboard.set to PC (success=$sent, length=${text.length})")
    }

    private fun addToHistory(entry: ClipboardEntry) {
        val current = _history.value
        val updated = if (current.isNotEmpty() && current.first().content == entry.content) {
            listOf(entry) + current.drop(1)
        } else {
            listOf(entry) + current
        }
        _history.value = updated.take(MAX_HISTORY)
    }

    private fun showSyncNotification() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(
            CLIPBOARD_CHANNEL_ID, "Clipboard Sync", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Tap to sync copied text to your PC" })

        val intent = Intent(context, ClipboardSendActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(CLIPBOARD_NOTIFICATION_ID, NotificationCompat.Builder(context, CLIPBOARD_CHANNEL_ID)
            .setContentTitle("Text copied")
            .setContentText("Tap to sync clipboard to your PC")
            .setSmallIcon(R.drawable.ic_tile_clipboard)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(R.drawable.ic_tile_clipboard, "Sync to PC", pi)
            .build())
    }

    fun cancelSyncNotification() {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(CLIPBOARD_NOTIFICATION_ID)
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val name = ComponentName(context, HyprConnectAccessibilityService::class.java)
            .flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(":").any { it.equals(name, ignoreCase = true) }
    }

    private fun canDrawOverApps(): Boolean = Settings.canDrawOverlays(context)

    private fun hasBackgroundClipboardPermission(): Boolean =
        context.checkSelfPermission("android.permission.READ_CLIPBOARD_IN_BACKGROUND") ==
                PackageManager.PERMISSION_GRANTED

    private fun isAppInForeground(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = am.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() } ?: return false
        return info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }
}
