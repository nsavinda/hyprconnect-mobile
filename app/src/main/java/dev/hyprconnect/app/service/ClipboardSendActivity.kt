package dev.hyprconnect.app.service

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import dev.hyprconnect.app.data.remote.HyprConnectClient
import javax.inject.Inject

/**
 * Transparent trampoline activity launched by [ClipboardSyncTile].
 *
 * Android 10+ forbids clipboard reads from background processes, but an Activity
 * that has focus can always read the clipboard. This activity:
 *   1. Gets focus (transparent window, invisible to the user)
 *   2. Reads the current clipboard in onResume()
 *   3. Pushes the content to the connected PC
 *   4. Immediately finishes
 *
 * The user sees no UI — the quick-settings panel collapses, the send happens,
 * and a brief toast confirms success or explains why it was skipped.
 */
@AndroidEntryPoint
class ClipboardSendActivity : ComponentActivity() {

    @Inject lateinit var clipboardMonitor: ClipboardMonitor
    @Inject lateinit var client: HyprConnectClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No setContent — the window is transparent and immediately closes.
    }

    override fun onResume() {
        super.onResume()
        sendClipboardToPC()
        finish()
    }

    private fun sendClipboardToPC() {
        if (!client.isConnected.value) {
            Toast.makeText(this, "HyprConnect: not connected to PC", Toast.LENGTH_SHORT).show()
            return
        }

        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val text = try {
            val clip = cm.primaryClip ?: run {
                Toast.makeText(this, "HyprConnect: clipboard is empty", Toast.LENGTH_SHORT).show()
                return
            }
            if (clip.itemCount == 0) return
            clip.getItemAt(0)?.text?.toString() ?: run {
                Toast.makeText(this, "HyprConnect: clipboard has no text", Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "HyprConnect: clipboard access denied", Toast.LENGTH_SHORT).show()
            return
        }

        clipboardMonitor.resend(text)
        Toast.makeText(this, "Clipboard sent to PC", Toast.LENGTH_SHORT).show()
    }
}
