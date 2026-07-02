package dev.hyprconnect.app.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import dev.hyprconnect.app.domain.model.DeviceStatus
import dev.hyprconnect.app.domain.model.Workspace
import dev.hyprconnect.app.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: String,
    viewModel: DeviceDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToFileTransfer: () -> Unit,
    onNavigateToMediaControl: () -> Unit = {},
    onNavigateToRemoteInput: () -> Unit = {},
    onNavigateToClipboard: () -> Unit = {}
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
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = device?.name ?: "Device",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = HyprText
                        )
                        Text(
                            text = "Device Details",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HyprGlassDeep)
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
                        colors = CardDefaults.cardColors(containerColor = HyprGlass),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, HyprGlassBorder),
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
                                        is DeviceStatus.Connected    -> "Connected"
                                        is DeviceStatus.Connecting   -> "Connecting..."
                                        is DeviceStatus.Pairing      -> "Pairing..."
                                        is DeviceStatus.Disconnected -> "Disconnected"
                                        is DeviceStatus.Error        -> "Error"
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
                        SectionLabel("// Actions")
                    }

                    val actions = buildList {
                        if ("file_transfer" in plugins) add(Triple(Icons.Default.Share, "Send File") { onNavigateToFileTransfer() })
                        if ("media" in plugins) add(Triple(Icons.Default.MusicNote, "Media") { onNavigateToMediaControl() })
                        if ("input" in plugins) add(Triple(Icons.Default.TouchApp, "Remote Input") { onNavigateToRemoteInput() })
                        if ("clipboard" in plugins) add(Triple(Icons.Default.ContentPaste, "Clipboard") { onNavigateToClipboard() })
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
                    item { SectionLabel("// System Controls") }

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
                            SectionLabel("// Workspaces")
                            TextButton(
                                onClick = { viewModel.loadWorkspaces() },
                                colors = ButtonDefaults.textButtonColors(contentColor = HyprBlue)
                            ) {
                                Text("Refresh", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
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
                    } else if (workspaces.isNotEmpty()) {
                        item {
                            WorkspacesByDesktop(
                                workspaces = workspaces,
                                onSwitch   = { viewModel.switchWorkspace(it) }
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
                            if (isConnected) "Disconnect" else "Connect",
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
        colors   = CardDefaults.cardColors(containerColor = HyprGlass),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, HyprGlassBorder),
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
                        title,
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

/**
 * Workspaces split into one column per desktop (Hyprland monitor). Each
 * column is headed by the monitor name and stacks its workspaces vertically;
 * columns sit side by side and the page scrolls vertically when a column is
 * tall. Within a column, workspaces can be reordered by long-pressing and
 * dragging up/down. A short tap switches to a workspace.
 *
 * Grouping preserves the monitor order in which workspaces first appear.
 */
@Composable
private fun WorkspacesByDesktop(
    workspaces: List<Workspace>,
    onSwitch: (Int) -> Unit,
) {
    val groups = remember(workspaces) {
        // LinkedHashMap semantics via groupBy preserve first-seen monitor order.
        workspaces.groupBy { it.monitor?.takeIf(String::isNotBlank) ?: "unknown" }.toList()
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "long-press to reorder within a desktop · tap to switch",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = HyprOverlay0,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            groups.forEach { (monitor, wss) ->
                DesktopColumn(
                    monitor = monitor,
                    workspaces = wss,
                    onSwitch = onSwitch,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * A single desktop's workspaces as a vertically stacked, reorderable column.
 * Order is session-only: it re-syncs to the incoming order whenever this
 * desktop's set of workspaces changes (e.g. on Refresh). Layout uses absolute
 * vertical offsets over a fixed cell height, so the dragged cell floats above
 * the others and drag hit-testing is exact.
 */
@Composable
private fun DesktopColumn(
    monitor: String,
    workspaces: List<Workspace>,
    onSwitch: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = 8.dp
    val cellHeight = 74.dp

    val order = remember { mutableStateListOf<Workspace>() }
    // Stable holders so the one-shot pointerInput closures read current values.
    val draggingSlot = remember { mutableStateOf<Int?>(null) }
    val pointerY = remember { mutableStateOf(0f) } // finger Y in local px
    val grabY = remember { mutableStateOf(0f) }    // finger offset within grabbed cell

    LaunchedEffect(workspaces) {
        val incomingIds = workspaces.map { it.id }
        val currentIds = order.map { it.id }
        if (incomingIds.toSet() == currentIds.toSet() && order.isNotEmpty()) {
            val byId = workspaces.associateBy { it.id }
            val refreshed = order.mapNotNull { byId[it.id] }
            order.clear(); order.addAll(refreshed)
        } else {
            order.clear(); order.addAll(workspaces)
        }
    }

    Column(modifier) {
        Text(
            text = monitor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = HyprBlue,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        val n = order.size
        val colHeight = if (n == 0) 0.dp else cellHeight * n + spacing * (n - 1)

        Box(
            Modifier
                .fillMaxWidth()
                .height(colHeight)
                .pointerInput(n) {
                    detectTapGestures(onTap = { p ->
                        val step = (cellHeight + spacing).toPx()
                        val idx = (p.y / step).toInt()
                        val within = p.y - idx * step <= cellHeight.toPx()
                        if (within && idx in 0 until order.size) onSwitch(order[idx].id)
                    })
                }
                .pointerInput(n) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            val step = (cellHeight + spacing).toPx()
                            val slot = (start.y / step).toInt().coerceIn(0, order.size - 1)
                            draggingSlot.value = slot
                            pointerY.value = start.y
                            grabY.value = start.y - slot * step
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val from = draggingSlot.value
                            if (from != null) {
                                pointerY.value += amount.y
                                val step = (cellHeight + spacing).toPx()
                                val target = (pointerY.value / step).toInt().coerceIn(0, order.size - 1)
                                if (target != from) {
                                    order.add(target, order.removeAt(from))
                                    draggingSlot.value = target
                                }
                            }
                        },
                        onDragEnd = { draggingSlot.value = null },
                        onDragCancel = { draggingSlot.value = null },
                    )
                }
        ) {
            order.forEachIndexed { slot, ws ->
                val dragging = draggingSlot.value == slot
                val baseY = (cellHeight + spacing) * slot

                val cellModifier = if (dragging) {
                    Modifier
                        .zIndex(1f)
                        .offset { IntOffset(0, (pointerY.value - grabY.value).roundToInt()) }
                        .scale(1.03f)
                        .shadow(8.dp, RoundedCornerShape(10.dp))
                } else {
                    Modifier.offset(y = baseY)
                }

                Box(cellModifier.fillMaxWidth().height(cellHeight)) {
                    WorkspaceGridCell(
                        id = ws.id,
                        name = ws.name,
                        windows = ws.windows,
                        isActive = ws.isActive,
                        lifted = dragging
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceGridCell(
    id: Int,
    name: String,
    windows: Int,
    isActive: Boolean,
    lifted: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) HyprBlueContainer else HyprGlass
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (isActive || lifted) 1.dp else 0.5.dp,
            if (isActive || lifted) HyprBlue else HyprGlassBorder
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) HyprBlue.copy(alpha = 0.2f) else HyprGlassDeep),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$id",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isActive) HyprBlue else HyprSubtext0
                    )
                }
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(HyprBlue)
                    )
                }
            }

            Column {
                Text(
                    text = name,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isActive) HyprText else HyprSubtext1,
                    maxLines = 1
                )
                Text(
                    text = "$windows win${if (windows != 1) "s" else ""}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = HyprOverlay0
                )
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
        colors = CardDefaults.cardColors(containerColor = HyprGlass),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, HyprGlassBorder),
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
                label,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = HyprSubtext1,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
