package dev.hyprconnect.app.service

import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
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
import javax.inject.Singleton

@Singleton
class ClipboardMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: HyprConnectClient,
    private val settingsRepository: SettingsRepository
) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastClipboardContent: String? = null
    private var lastRemoteUpdateTime: Long = 0

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        scope.launch {
            if (!settingsRepository.clipboardSync.first()) return@launch
            
            val clip = clipboard.primaryClip ?: return@launch
            if (clip.itemCount == 0) return@launch
            val item = clip.getItemAt(0) ?: return@launch
            val text = item.text?.toString() ?: return@launch

            if (text == lastClipboardContent) return@launch
            
            // Rate limit remote updates
            if (System.currentTimeMillis() - lastRemoteUpdateTime < 500) return@launch

            lastClipboardContent = text
            val request = JsonRpcRequest(
                method = "clipboard.set",
                params = buildJsonObject {
                    put("content", text)
                }
            )
            client.sendRequest(request)
        }
    }

    fun start() {
        clipboard.addPrimaryClipChangedListener(listener)
    }

    fun stop() {
        clipboard.removePrimaryClipChangedListener(listener)
    }

    fun setRemoteClipboard(text: String) {
        lastClipboardContent = text
        lastRemoteUpdateTime = System.currentTimeMillis()
        val clip = android.content.ClipData.newPlainText("HyprConnect", text)
        clipboard.setPrimaryClip(clip)
    }
}
