package dev.hyprconnect.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.hyprconnect.app.ui.components.DeviceCard

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
        topBar = {
            TopAppBar(
                title = { Text("HyprConnect", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { 
                    isScanning = !isScanning
                    if (isScanning) viewModel.startDiscovery() else viewModel.stopDiscovery()
                },
                icon = { Icon(Icons.Default.Search, null) },
                text = { Text(if (isScanning) "Stop Scan" else "Scan") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pairedDevices.isNotEmpty()) {
                item {
                    Text(
                        "Paired Devices",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(pairedDevices) { device ->
                    DeviceCard(
                        device = device,
                        onClick = { onNavigateToDeviceDetail(device.id) }
                    )
                }
            }

            if (isScanning || discoveredDevices.isNotEmpty()) {
                item { Spacer(Modifier.height(16.dp)) }
                item {
                    Text(
                        "Discovered Devices",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                if (discoveredDevices.isEmpty() && isScanning) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
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
                        Modifier.fillParentMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.DevicesOther,
                            null,
                            Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No devices found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "Tap scan to find your desktop",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
