package dev.hyprconnect.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import dev.hyprconnect.app.service.ClipboardMonitor
import dev.hyprconnect.app.service.HyprConnectService
import dev.hyprconnect.app.ui.navigation.NavGraph
import dev.hyprconnect.app.ui.theme.HyprConnectTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var clipboardMonitor: ClipboardMonitor

    private var sharedUris by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedUris = extractSharedUris(intent)

        // Start HyprConnectService when the app opens
        val serviceIntent = Intent(this, HyprConnectService::class.java)
        startForegroundService(serviceIntent)

        setContent {
            HyprConnectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(sharedUris = sharedUris)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reliable foreground signal: check if clipboard changed while we were away
        // and push it to the PC. This handles the common case of copying in another app.
        clipboardMonitor.onAppForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUris = extractSharedUris(intent)
    }

    private fun extractSharedUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()

        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                if (uri != null) listOf(uri) else emptyList()
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
            }
            else -> emptyList()
        }
    }
}
