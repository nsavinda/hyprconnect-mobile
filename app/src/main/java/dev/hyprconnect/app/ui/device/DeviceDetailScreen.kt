package dev.hyprconnect.app.ui.device

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.hyprconnect.app.domain.model.DeviceStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: String,
    viewModel: DeviceDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToFileTransfer: () -> Unit
) {
    val device by viewModel.device.collectAsState()

    LaunchedEffect(deviceId) {
        viewModel.loadDevice(deviceId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.name ?: "Device Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        device?.let { dev ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Status: ${dev.status}", style = MaterialTheme.typography.bodyLarge)
                            if (dev.batteryLevel != null) {
                                Text("Battery: ${dev.batteryLevel}%", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }

                item {
                    Text("Quick Actions", style = MaterialTheme.typography.titleMedium)
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionCard(
                            icon = Icons.Default.Share,
                            label = "Send File",
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToFileTransfer
                        )
                        ActionCard(
                            icon = Icons.Default.ContentPaste,
                            label = "Clipboard",
                            modifier = Modifier.weight(1f),
                            onClick = { /* TODO */ }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionCard(
                            icon = Icons.Default.Cast,
                            label = "Media",
                            modifier = Modifier.weight(1f),
                            onClick = { /* TODO */ }
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }

                item {
                    Button(
                        onClick = {
                            if (dev.status is DeviceStatus.Connected) viewModel.disconnect()
                            else viewModel.connect()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (dev.status is DeviceStatus.Connected) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        Text(if (dev.status is DeviceStatus.Connected) "Disconnect" else "Connect")
                    }
                }
            }
        } ?: Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Icon(icon, null, Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
