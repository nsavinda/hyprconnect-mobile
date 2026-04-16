package dev.hyprconnect.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hyprconnect.app.domain.model.Device
import dev.hyprconnect.app.domain.model.DeviceStatus
import dev.hyprconnect.app.domain.model.DeviceType
import dev.hyprconnect.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceCard(
    device: Device,
    onClick: () -> Unit
) {
    val isConnected = device.status is DeviceStatus.Connected
    val isConnecting = device.status is DeviceStatus.Connecting || device.status is DeviceStatus.Pairing
    val borderColor = when {
        isConnected  -> HyprTeal
        isConnecting -> HyprBlue
        else         -> HyprSurface1
    }
    val accentColor = when {
        isConnected  -> HyprTeal
        isConnecting -> HyprBlue
        else         -> HyprOverlay0
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HyprSurface0),
        shape  = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(72.dp)
                    .background(
                        borderColor,
                        RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
                    )
            )

            Spacer(Modifier.width(14.dp))

            // Device icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isConnected) HyprTealContainer else HyprMantle
                    ),
                contentAlignment = Alignment.Center
            ) {
                val icon = when (device.type) {
                    DeviceType.PHONE  -> Icons.Default.PhoneAndroid
                    DeviceType.TABLET -> Icons.Default.Tablet
                    else              -> Icons.Default.Computer
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = accentColor
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = HyprText
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status dot
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(borderColor)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = when (device.status) {
                            is DeviceStatus.Connected    -> "connected"
                            is DeviceStatus.Connecting   -> "connecting..."
                            is DeviceStatus.Pairing      -> "pairing..."
                            is DeviceStatus.Disconnected -> "disconnected"
                            is DeviceStatus.Error        -> "error"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = HyprSubtext0
                    )
                }
            }

            if (device.batteryLevel != null) {
                BatteryChip(level = device.batteryLevel)
                Spacer(Modifier.width(14.dp))
            }
        }
    }
}

@Composable
private fun BatteryChip(level: Int) {
    val color = when {
        level > 60 -> HyprGreen
        level > 25 -> HyprYellow
        else       -> HyprPink
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            Icons.Default.BatteryFull,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = color
        )
        Text(
            text = "$level%",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = color
        )
    }
}
