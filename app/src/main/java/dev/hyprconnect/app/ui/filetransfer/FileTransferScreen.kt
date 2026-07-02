package dev.hyprconnect.app.ui.filetransfer

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.hyprconnect.app.ui.theme.*
import dev.hyprconnect.app.util.toHumanReadableSize
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTransferScreen(
    viewModel: FileTransferViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    sharedUris: List<Uri> = emptyList()
) {
    val transfers by viewModel.transfers.collectAsState()
    val batches by viewModel.batches.collectAsState()
    val overallElapsedMs by viewModel.overallElapsedMs.collectAsState()
    val activeCount by viewModel.activeCount.collectAsState()
    val nowMs = System.currentTimeMillis()
    val context = LocalContext.current
    val handledUris = remember { mutableSetOf<String>() }

    LaunchedEffect(sharedUris) {
        sharedUris.forEach { uri ->
            val key = uri.toString()
            if (handledUris.add(key)) viewModel.sendSharedUri(context, uri)
        }
    }

    val pickFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* not all providers support persisting */ }
            viewModel.sendUri(context, uri, relativePath = null)
        }
    }

    val pickFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
            viewModel.sendFolder(context, uri)
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "File Transfers",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = HyprText
                        )
                        if (transfers.isNotEmpty()) {
                            val finished = activeCount == 0
                            val label = if (finished)
                                "${transfers.size} Done · Took ${formatElapsed(overallElapsedMs)}"
                            else
                                "$activeCount Active · Elapsed ${formatElapsed(overallElapsedMs)}"
                            Text(
                                label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = HyprSubtext0
                            )
                        }
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            PickerBar(
                onPickFiles = { pickFilesLauncher.launch(arrayOf("*/*")) },
                onPickFolder = { pickFolderLauncher.launch(null) }
            )

            if (transfers.isEmpty()) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(HyprGlass),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FilePresent,
                                null,
                                Modifier.size(36.dp),
                                tint = HyprOverlay0
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No Active Transfers",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = HyprSubtext0
                        )
                        Text(
                            "Pick a File or Folder Above",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = HyprOverlay0
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(batches, key = { it.id }) { batch ->
                        BatchCard(
                            batch = batch,
                            nowMs = nowMs,
                            onCancel = { viewModel.cancelBatch(batch.id) }
                        )
                    }
                    items(transfers, key = { it.id }) { transfer ->
                        TransferCard(transfer = transfer, nowMs = nowMs)
                    }
                }
            }
        }
    }
}

private fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun BatchCard(batch: FolderBatch, nowMs: Long, onCancel: () -> Unit) {
    val progress = if (batch.totalSize > 0)
        (batch.transferredBytes.toFloat() / batch.totalSize).coerceIn(0f, 1f)
    else if (batch.totalFiles > 0)
        batch.completedFiles.toFloat() / batch.totalFiles
    else 0f

    val accent = when {
        !batch.active && batch.completedFiles >= batch.totalFiles -> HyprGreen
        batch.active -> HyprBlue
        else -> HyprPink
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HyprGlassRaised),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, HyprGlassBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(HyprBlueContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            null,
                            Modifier.size(18.dp),
                            tint = accent
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = batch.rootName,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = HyprText,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${batch.completedFiles}/${batch.totalFiles} Files · " +
                                "${batch.transferredBytes.toHumanReadableSize()} / ${batch.totalSize.toHumanReadableSize()}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = HyprSubtext0
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${(progress * 100).toInt()}%",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = accent
                    )
                    if (batch.active) {
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                null,
                                tint = HyprSubtext1
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(HyprSurface2)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                )
            }

            if (batch.startMs != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (batch.activeSpeed > 0) {
                        Text(
                            text = "${batch.activeSpeed.toHumanReadableSize()}/s",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = HyprBlue
                        )
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    Text(
                        text = "Elapsed ${formatElapsed(elapsedFrom(batch.startMs, batch.endMs, nowMs))}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = HyprOverlay0
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerBar(onPickFiles: () -> Unit, onPickFolder: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PickerButton(
            icon = Icons.Default.InsertDriveFile,
            label = "Pick Files",
            modifier = Modifier.weight(1f),
            onClick = onPickFiles
        )
        PickerButton(
            icon = Icons.Default.Folder,
            label = "Pick Folder",
            modifier = Modifier.weight(1f),
            onClick = onPickFolder
        )
    }
}

@Composable
private fun PickerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.border(BorderStroke(0.5.dp, HyprGlassBorder), RoundedCornerShape(12.dp)),
        color = HyprGlass,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = HyprBlue)
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = HyprText
            )
        }
    }
}

@Composable
private fun TransferCard(transfer: FileTransfer, nowMs: Long) {
    val progressColor = when {
        transfer.progress >= 1f -> HyprGreen
        transfer.status.contains("error", ignoreCase = true) -> HyprPink
        else -> HyprBlue
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HyprGlass),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(0.5.dp, HyprGlassBorder),
        shape  = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    transfer.progress >= 1f -> HyprTealContainer
                                    transfer.status.contains("error", ignoreCase = true) -> HyprPinkContainer
                                    else -> HyprBlueContainer
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FilePresent,
                            null,
                            Modifier.size(18.dp),
                            tint = progressColor
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = transfer.name,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = HyprText,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = transfer.size.toHumanReadableSize(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = HyprSubtext0
                        )
                    }
                }

                // Percentage
                Text(
                    "${(transfer.progress * 100).toInt()}%",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = progressColor
                )
            }

            Spacer(Modifier.height(10.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(HyprSurface2)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(transfer.progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(progressColor)
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = transfer.status,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = HyprSubtext0
                )
                val showElapsed = transfer.batchId == null && transfer.startMs != null
                when {
                    transfer.speed > 0L -> {
                        Text(
                            text = "${transfer.speed.toHumanReadableSize()}/s",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = HyprBlue
                        )
                    }
                    showElapsed -> {
                        Text(
                            text = "Elapsed ${formatElapsed(elapsedFrom(transfer.startMs, transfer.endMs, nowMs))}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = HyprOverlay0
                        )
                    }
                }
            }
        }
    }
}

private fun elapsedFrom(startMs: Long?, endMs: Long?, nowMs: Long): Long {
    val start = startMs ?: return 0L
    val end = endMs ?: nowMs
    return (end - start).coerceAtLeast(0L)
}
