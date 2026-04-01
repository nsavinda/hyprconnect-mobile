package dev.hyprconnect.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import dev.hyprconnect.app.service.HyprConnectService
import dev.hyprconnect.app.ui.navigation.NavGraph
import dev.hyprconnect.app.ui.theme.HyprConnectTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start HyprConnectService when the app opens
        val serviceIntent = Intent(this, HyprConnectService::class.java)
        startForegroundService(serviceIntent)

        setContent {
            HyprConnectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph()
                }
            }
        }
    }
}
