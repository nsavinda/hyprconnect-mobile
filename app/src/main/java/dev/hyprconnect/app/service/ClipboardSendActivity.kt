package dev.hyprconnect.app.service

import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import dev.hyprconnect.app.data.remote.HyprConnectClient
import javax.inject.Inject

/**
 * Transparent trampoline activity launched when clipboard changes in background.
 *
 * Android 10+ requires window focus (not just foreground process) for clipboard reads.
 * An activity with no content never receives focus — this was KDE Connect's MR #145 fix.
 * We set a minimal focusable FrameLayout so the window surfaces and receives focus, then
 * read the clipboard in onWindowFocusChanged(true) once focus is confirmed.
 */
@AndroidEntryPoint
class ClipboardSendActivity : ComponentActivity() {

    @Inject lateinit var clipboardMonitor: ClipboardMonitor
    @Inject lateinit var client: HyprConnectClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A window needs a surface to receive focus. An empty FrameLayout is invisible
        // but gives the window manager something to grant focus to.
        setContentView(FrameLayout(this))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            sendClipboardToPC()
            finish()
        }
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

        clipboardMonitor.cancelSyncNotification()
        clipboardMonitor.resend(text)
    }
}
