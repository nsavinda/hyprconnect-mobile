package dev.hyprconnect.app.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.hyprconnect.app.domain.model.DeviceStatus
import dev.hyprconnect.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: String,
    viewModel: DeviceDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToFileTransfer: () -> Unit,
    onNavigateToMediaControl: () -> Unit = {},
    onNavigateToRemoteInput: () -> Unit = {}
) {
    val device by viewModel.device.collectAsState()
    val workspaces by viewModel.workspaces.collectAsState()
    val isWorkspaceLoading by viewModel.isWorkspaceLoading.collectAsState()
    val workspaceActionError by viewModel.workspaceActionError.collectAsState()
    val systemVolume by viewModel.systemVolume.collectAsState()
    val systemBrightness by viewModel.systemBrightness.collectAsState()
    val controlsError by viewModel.controlsError.collectAsState()

    LaunchedEffect(deviceId) { viewModel.loadDevice(deviceId) }

    LaunchedEffect(device?.status) {
        if (device?.status is DeviceStatus.Connected) {
            viewModel.loadWorkspaces()
            viewModel.loadSystemControls()
        }
    }

    Scaffold(
        containerColor = HyprBase,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = device?.name ?: "device",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = HyprText
                        )
                        Text(
                            text = "device details",
                            style = MaterialTheme.typography.labelSmall,
                            color = HyprSubtext0
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = HyprSubtext1)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HyprMantle)
            )
        }
    ) { padding ->
        device?.let { dev ->
            val isConnected = dev.status is DeviceStatus.Connected

            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Status card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = HyprSurface0),
                        shape  = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isConnected) HyprTeal else HyprOverlay0)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = when (dev.status) {
                                        is DeviceStatus.Connected    -> "connected"
                                        is DeviceStatus.Connecting   -> "connecting..."
                                        is DeviceStatus.Pairing      -> "pairing..."
                                        is DeviceStatus.Disconnected -> "disconnected"
                                        is DeviceStatus.Error        -> "error"
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = if (isConnected) HyprTeal else HyprSubtext0
                                )
                            }
                            if (dev.batteryLevel != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.BatteryFull,
                                        null,
                                        Modifier.size(16.dp),
                                        tint = when {
                                            dev.batteryLevel > 60 -> HyprGreen
                                            dev.batteryLevel > 25 -> HyprYellow
                                            else                  -> HyprPink
                                        }
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "${dev.batteryLevel}%",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = HyprSubtext1
                                    )
                                }
                            }
                        }
                    }
                }

                // Available Actions grid
                val plugins = dev.availablePlugins
                if (plugins.isNotEmpty()) {
                    item {
                        SectionLabel("// actions")
                    }

                    val actions = buildList {
                        if ("file_transfer" in plugins) add(Triple(Icons.Default.Share, "Send File") { onNavigateToFileTransfer() })
                        if ("media" in plugins) add(Triple(Icons.Default.MusicNote, "Media") { onNavigateToMediaControl() })
                        if ("input" in plugins) add(Triple(Icons.Default.TouchApp, "Remote Input") { onNavigateToRemoteInput() })
                        if ("clipboard" in plugins) add(Triple(Icons.Default.ContentPaste, "Clipboard") { })
                        if ("notification" in plugins) add(Triple(Icons.Default.Notifications, "Notifications") { })
                    }

                    actions.chunked(2).forEach { row ->
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                row.forEach { (icon, label, onClick) ->
                                    ActionCard(
                                        icon = icon,
                                        label = label,
                                        modifier = Modifier.weight(1f),
                                        onClick = onClick
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                // System controls
                if (isConnected) {
                    item { SectionLabel("// system controls") }

                    if (controlsError != null) {
                        item {
                            Text(controlsError!!, style = MaterialTheme.typography.bodySmall, color = HyprPink)
                        }
                    }

                    item {
                        HyprSliderCard(
                            title = "Volume",
                            icon  = Icons.Default.VolumeUp,
                            value = systemVolume,
                            onValueChange  = { viewModel.setSystemVolume(it) },
                            onValueChangeFinished = { viewModel.commitSystemVolume(systemVolume) }
                        )
                    }

                    item {
                        HyprSliderCard(
                            title = "Brightness",
                            icon  = Icons.Default.LightMode,
                            value = systemBrightness,
                            onValueChange  = { viewModel.setSystemBrightness(it) },
                            onValueChangeFinished = { viewModel.commitSystemBrightness(systemBrightness) }
                        )
                    }

                    // Workspaces
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionLabel("// workspaces")
                            TextButton(
                                onClick = { viewModel.loadWorkspaces() },
                                colors = ButtonDefaults.textButtonColors(contentColor = HyprBlue)
                            ) {
                                Text("refresh", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                        }
                    }

                    if (workspaceActionError != null) {
                        item {
                            Text(workspaceActionError!!, style = MaterialTheme.typography.bodySmall, color = HyprPink)
                        }
                    }

                    if (isWorkspaceLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    color = HyprBlue,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else {
                        items(workspaces, key = { it.id }) { workspace ->
                            WorkspaceCard(
                                id       = workspace.id,
                                name     = workspace.name,
                                windows  = workspace.windows,
                                monitor  = workspace.monitor,
                                isActive = workspace.isActive,
                                onClick  = { viewModel.switchWorkspace(workspace.id) }
                            )
                        }
                    }
                }

                // Connect / Disconnect
                item {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            if (isConnected) viewModel.disconnect() else viewModel.connect()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = if (isConnected) {
                            ButtonDefaults.buttonColors(containerColor = HyprPinkContainer, contentColor = HyprPink)
                        } else {
                            ButtonDefaults.buttonColors(containerColor = HyprBlueContainer, contentColor = HyprBlue)
                        }
                    ) {
                        Text(
                            if (isConnected) "disconnect" else "connect",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = HyprBlue, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        color = HyprBlue,
        letterSpacing = 0.8.sp
    )
}

@Composable
private fun HyprSliderCard(
    title: String,
    icon: ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = HyprSurface0),
        shape    = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(18.dp), tint = HyprBlue)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        title.lowercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = HyprSubtext1
                    )
                }
                Text(
                    "${(value * 100).toInt()}%",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = HyprBlue
                )
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = 0f..1f,
                onValueChangeFinished = onValueChangeFinished,
                colors = SliderDefaults.colors(
                    thumbColor       = HyprBlue,
                    activeTrackColor = HyprBlue,
                    inactiveTrackColor = HyprSurface2
                )
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
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) HyprBlueContainer else HyprSurface0
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Workspace number badge
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isActive) HyprBlue.copy(alpha = 0.2f) else HyprMantle),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$id",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isActive) HyprBlue else HyprSubtext0
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isActive) HyprText else HyprSubtext1
                )
                Text(
                    text = buildString {
                        append("$windows window${if (windows != 1) "s" else ""}")
                        if (!monitor.isNullOrBlank()) append(" · $monitor")
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = HyprOverlay0
                )
            }

            if (isActive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(HyprBlue.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "active",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = HyprBlue
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionCard(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = HyprSurface0),
        shape  = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HyprBlueContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(22.dp), tint = HyprBlue)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                label.lowercase(),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = HyprSubtext1,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
