package dev.hyprconnect.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.hyprconnect.app.ui.components.DeviceCard
import dev.hyprconnect.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToPairing: (String) -> Unit,
    onNavigateToDeviceDetail: (String) -> Unit
) {
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    var isScanning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.startDiscovery()
    }

    Scaffold(
        containerColor = HyprBase,
        topBar = {
            // Custom Hyprland-style header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HyprMantle)
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Column {
                    Text(
                        text = "hyprconnect",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = HyprBlue,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "wayland phone bridge",
                        style = MaterialTheme.typography.labelSmall,
                        color = HyprSubtext0
                    )
                }
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = HyprSubtext1
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    isScanning = !isScanning
                    if (isScanning) viewModel.startDiscovery() else viewModel.stopDiscovery()
                },
                containerColor = if (isScanning) HyprSurface1 else HyprBlue,
                contentColor   = if (isScanning) HyprSubtext1 else HyprCrust,
                shape = RoundedCornerShape(14.dp),
                icon = { Icon(Icons.Default.Radar, null) },
                text = { Text(if (isScanning) "Stop Scan" else "Scan", fontWeight = FontWeight.Medium) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (pairedDevices.isNotEmpty()) {
                item {
                    SectionLabel("// paired devices")
                }
                items(pairedDevices) { device ->
                    DeviceCard(
                        device = device,
                        onClick = { onNavigateToDeviceDetail(device.id) }
                    )
                }
            }

            if (isScanning || discoveredDevices.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SectionLabel("// discovered")
                        if (isScanning) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(HyprBlue.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "scanning",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HyprBlue,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                if (discoveredDevices.isEmpty() && isScanning) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = HyprBlue,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "looking for devices...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HyprSubtext0,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                items(discoveredDevices.filter { dev -> pairedDevices.none { it.id == dev.id } }) { device ->
                    DeviceCard(
                        device = device,
                        onClick = { onNavigateToPairing(device.id) }
                    )
                }
            }

            if (pairedDevices.isEmpty() && discoveredDevices.isEmpty() && !isScanning) {
                item {
                    Column(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(HyprSurface0),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.DevicesOther,
                                null,
                                Modifier.size(40.dp),
                                tint = HyprOverlay0
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "no devices found",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = HyprSubtext0,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "tap scan to find your desktop",
                            style = MaterialTheme.typography.bodySmall,
                            color = HyprOverlay0
                        )
                    }
                }
            }
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
