package dev.hyprconnect.app.ui.device

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
    val workspaces by viewModel.workspaces.collectAsState()
    val isWorkspaceLoading by viewModel.isWorkspaceLoading.collectAsState()
    val workspaceActionError by viewModel.workspaceActionError.collectAsState()
    val systemVolume by viewModel.systemVolume.collectAsState()
    val systemBrightness by viewModel.systemBrightness.collectAsState()
    val controlsError by viewModel.controlsError.collectAsState()

    LaunchedEffect(deviceId) {
        viewModel.loadDevice(deviceId)
    }

    LaunchedEffect(device?.status) {
        if (device?.status is DeviceStatus.Connected) {
            viewModel.loadWorkspaces()
            viewModel.loadSystemControls()
        }
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

                val plugins = dev.availablePlugins

                if (plugins.isNotEmpty()) {
                    item {
                        Text("Available Actions", style = MaterialTheme.typography.titleMedium)
                    }

                    // Build action rows from available plugins
                    val actions = buildList {
                        if ("file_transfer" in plugins) add(Triple(Icons.Default.Share, "Send File") { onNavigateToFileTransfer() })
                        if ("clipboard" in plugins) add(Triple(Icons.Default.ContentPaste, "Clipboard") { /* TODO */ })
                        if ("media" in plugins) add(Triple(Icons.Default.MusicNote, "Media") { /* TODO */ })
                        if ("notification" in plugins) add(Triple(Icons.Default.Notifications, "Notifications") { /* TODO */ })
                    }

                    actions.chunked(2).forEach { row ->
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { (icon, label, onClick) ->
                                    ActionCard(
                                        icon = icon,
                                        label = label,
                                        modifier = Modifier.weight(1f),
                                        onClick = onClick
                                    )
                                }
                                if (row.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                if (dev.status is DeviceStatus.Connected) {
                    item {
                        Text("System Controls", style = MaterialTheme.typography.titleMedium)
                    }

                    if (controlsError != null) {
                        item {
                            Text(
                                text = controlsError ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    item {
                        ControlSliderCard(
                            title = "Volume",
                            icon = Icons.Default.VolumeUp,
                            value = systemVolume,
                            onValueChange = { viewModel.setSystemVolume(it) },
                            onValueChangeFinished = { viewModel.commitSystemVolume(systemVolume) }
                        )
                    }

                    item {
                        ControlSliderCard(
                            title = "Brightness",
                            icon = Icons.Default.LightMode,
                            value = systemBrightness,
                            onValueChange = { viewModel.setSystemBrightness(it) },
                            onValueChangeFinished = { viewModel.commitSystemBrightness(systemBrightness) }
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Workspaces", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { viewModel.loadWorkspaces() }) {
                                Text("Refresh")
                            }
                        }
                    }

                    if (workspaceActionError != null) {
                        item {
                            Text(
                                text = workspaceActionError ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (isWorkspaceLoading) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else {
                        items(workspaces, key = { it.id }) { workspace ->
                            WorkspaceCard(
                                id = workspace.id,
                                name = workspace.name,
                                windows = workspace.windows,
                                monitor = workspace.monitor,
                                isActive = workspace.isActive,
                                onClick = { viewModel.switchWorkspace(workspace.id) }
                            )
                        }
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
        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun ControlSliderCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = title)
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleSmall)
                }
                Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = 0f..1f,
                onValueChangeFinished = onValueChangeFinished
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceCard(
    id: Int,
    name: String,
    windows: Int,
    monitor: String?,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("#$id · $name", style = MaterialTheme.typography.titleSmall)
                if (isActive) {
                    Text("Current", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Windows: $windows", style = MaterialTheme.typography.bodySmall)
            if (!monitor.isNullOrBlank()) {
                Text("Monitor: $monitor", style = MaterialTheme.typography.bodySmall)
            }
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
