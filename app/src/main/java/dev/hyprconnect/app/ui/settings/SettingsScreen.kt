package dev.hyprconnect.app.ui.settings

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.hyprconnect.app.service.HyprConnectAccessibilityService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.hyprconnect.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val deviceName     by viewModel.deviceName.collectAsState()
    val notificationSync by viewModel.notificationSync.collectAsState()
    val clipboardSync  by viewModel.clipboardSync.collectAsState()
    val quicTransfer   by viewModel.quicTransfer.collectAsState()
    val maxConcurrent  by viewModel.maxConcurrentTransfers.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    // Re-check on every resume so indicators update after returning from system settings.
    var canDrawOverApps by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            canDrawOverApps = Settings.canDrawOverlays(context)
            accessibilityEnabled = isAccessibilityServiceEnabled(context)
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = HyprGlassDeep,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text(
                    "Clear App Data",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = HyprText
                )
            },
            text = {
                Text(
                    "This will clear all settings, paired devices, and cached discovery data. You will need to re-pair your devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HyprSubtext1
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearAllData { onNavigateBack() }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = HyprPink)
                ) {
                    Text("Clear", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = HyprSubtext1)
                ) {
                    Text("Cancel", fontFamily = FontFamily.Monospace)
                }
            }
        )
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = HyprText
                    )
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
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Device information section
            item { SectionLabel("// Device") }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = HyprGlass),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, HyprGlassBorder),
                    shape  = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Device Name",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = HyprSubtext0
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = deviceName,
                            onValueChange = { viewModel.updateDeviceName(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                color = HyprText
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = HyprBlue,
                                unfocusedBorderColor = HyprGlassBorder,
                                cursorColor          = HyprBlue,
                                focusedContainerColor   = HyprGlassDeep,
                                unfocusedContainerColor = HyprGlassDeep
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            // Features section
            item { SectionLabel("// Features") }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = HyprGlass),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, HyprGlassBorder),
                    shape  = RoundedCornerShape(14.dp)
                ) {
                    Column {
                        HyprToggleItem(
                            title       = "Notification Sync",
                            description = "Mirror phone notifications to desktop",
                            checked     = notificationSync,
                            onCheckedChange = { viewModel.setNotificationSync(it) }
                        )
                        HyprDivider()
                        HyprToggleItem(
                            title       = "Clipboard Sync",
                            description = "Share clipboard between devices",
                            checked     = clipboardSync,
                            onCheckedChange = { viewModel.setClipboardSync(it) }
                        )
                        if (clipboardSync) {
                            // Accessibility service row (recommended — works on all OEMs)
                            HyprDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Accessibility Service",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = if (accessibilityEnabled) HyprGreen else HyprSubtext1
                                    )
                                    Text(
                                        if (accessibilityEnabled) "Background Clipboard Sync Active"
                                        else "Enable for Automatic Background Sync (Recommended)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HyprSubtext0
                                    )
                                }
                                if (!accessibilityEnabled) {
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(
                                        onClick = {
                                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = HyprBlue)
                                    ) {
                                        Text("Enable", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                    }
                                }
                            }

                            // Display over other apps row (fallback)
                            HyprDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Display Over Other Apps",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = if (canDrawOverApps) HyprGreen else HyprSubtext1
                                    )
                                    Text(
                                        if (canDrawOverApps) "Overlay Fallback Active"
                                        else "Fallback if Accessibility is Unavailable",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HyprSubtext0
                                    )
                                }
                                if (!canDrawOverApps) {
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(
                                        onClick = {
                                            context.startActivity(Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            ))
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = HyprSubtext1)
                                    ) {
                                        Text("Grant", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            // Transfer section
            item { SectionLabel("// Transfer") }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = HyprGlass),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, HyprGlassBorder),
                    shape  = RoundedCornerShape(14.dp)
                ) {
                    Column {
                        HyprToggleItem(
                            title       = "QUIC File Transfer",
                            description = "Faster transfers via QUIC protocol",
                            checked     = quicTransfer,
                            onCheckedChange = { viewModel.setQuicTransfer(it) }
                        )
                        HyprDivider()
                        HyprStepperItem(
                            title = "Max Concurrent Transfers",
                            description = "Files Uploaded in Parallel From a Folder",
                            value = maxConcurrent,
                            range = 1..16,
                            onValueChange = { viewModel.setMaxConcurrentTransfers(it) }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            // About section
            item { SectionLabel("// About") }

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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Version",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = HyprSubtext1
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(HyprBlueContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "v0.1.0",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = HyprBlue
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            // Danger zone
            item { SectionLabel("// Data") }

            item {
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HyprPink),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HyprPink.copy(alpha = 0.5f))
                ) {
                    Text(
                        "Clear App Data",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val name = ComponentName(context, HyprConnectAccessibilityService::class.java)
        .flattenToString()
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(":").any { it.equals(name, ignoreCase = true) }
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
private fun HyprDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = HyprSurface2
    )
}

@Composable
private fun HyprStepperItem(
    title: String,
    description: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = HyprText
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = HyprSubtext0
            )
        }
        Spacer(Modifier.width(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(HyprGlassDeep)
        ) {
            TextButton(
                onClick = { if (value > range.first) onValueChange(value - 1) },
                enabled = value > range.first,
                colors = ButtonDefaults.textButtonColors(contentColor = HyprBlue)
            ) {
                Text("-", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Text(
                value.toString(),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = HyprText,
                modifier = Modifier.widthIn(min = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            TextButton(
                onClick = { if (value < range.last) onValueChange(value + 1) },
                enabled = value < range.last,
                colors = ButtonDefaults.textButtonColors(contentColor = HyprBlue)
            ) {
                Text("+", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HyprToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = HyprText
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = HyprSubtext0
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor  = HyprCrust,
                checkedTrackColor  = HyprBlue,
                uncheckedThumbColor = HyprSubtext0,
                uncheckedTrackColor = HyprSurface2
            )
        )
    }
}
