package dev.hyprconnect.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hyprconnect.app.domain.model.Device
import dev.hyprconnect.app.domain.model.DeviceStatus
import dev.hyprconnect.app.domain.model.DeviceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceCard(
    device: Device,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (device.type) {
                DeviceType.PHONE -> Icons.Default.PhoneAndroid
                DeviceType.TABLET -> Icons.Default.Tablet
                else -> Icons.Default.Computer
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (device.status == DeviceStatus.Connected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = when (device.status) {
                        is DeviceStatus.Connected -> "Connected"
                        is DeviceStatus.Connecting -> "Connecting..."
                        is DeviceStatus.Pairing -> "Pairing..."
                        is DeviceStatus.Disconnected -> "Disconnected"
                        is DeviceStatus.Error -> "Error"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (device.batteryLevel != null) {
                BatteryIndicator(level = device.batteryLevel)
            }
        }
    }
}

@Composable
fun BatteryIndicator(level: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$level%",
            style = MaterialTheme.typography.bodySmall
        )
        // Add a simple battery icon here if needed
    }
}
