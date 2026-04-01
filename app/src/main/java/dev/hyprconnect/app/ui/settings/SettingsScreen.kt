package dev.hyprconnect.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val deviceName by viewModel.deviceName.collectAsState()
    val notificationSync by viewModel.notificationSync.collectAsState()
    val clipboardSync by viewModel.clipboardSync.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            item {
                Text(
                    "Device Information",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { viewModel.updateDeviceName(it) },
                    label = { Text("Device Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Spacer(Modifier.height(24.dp)) }

            item {
                Text(
                    "Features",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                ToggleItem(
                    title = "Notification Sync",
                    description = "Mirror phone notifications to your desktop",
                    checked = notificationSync,
                    onCheckedChange = { viewModel.setNotificationSync(it) }
                )
            }

            item {
                ToggleItem(
                    title = "Clipboard Sync",
                    description = "Share clipboard between phone and desktop",
                    checked = clipboardSync,
                    onCheckedChange = { viewModel.setClipboardSync(it) }
                )
            }
            
            item { Spacer(Modifier.height(32.dp)) }
            
            item {
                Text(
                    "About",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                ListItem(
                    headlineContent = { Text("Version") },
                    supportingContent = { Text("0.1.0") }
                )
            }
        }
    }
}

@Composable
fun ToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}
