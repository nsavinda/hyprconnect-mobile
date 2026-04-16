package dev.hyprconnect.app.ui.filetransfer

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilePresent
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTransferScreen(
    viewModel: FileTransferViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    sharedUris: List<Uri> = emptyList()
) {
    val transfers by viewModel.transfers.collectAsState()
    val context = LocalContext.current
    val handledUris = remember { mutableSetOf<String>() }

    LaunchedEffect(sharedUris) {
        sharedUris.forEach { uri ->
            val key = uri.toString()
            if (handledUris.add(key)) viewModel.sendSharedUri(context, uri)
        }
    }

    Scaffold(
        containerColor = HyprBase,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "file transfers",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = HyprText
                        )
                        if (transfers.isNotEmpty()) {
                            Text(
                                "${transfers.size} active",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HyprMantle)
            )
        }
    ) { padding ->
        if (transfers.isEmpty()) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(HyprSurface0),
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
                        "no active transfers",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = HyprSubtext0
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(transfers) { transfer ->
                    TransferCard(transfer = transfer)
                }
            }
        }
    }
}

@Composable
private fun TransferCard(transfer: FileTransfer) {
    val progressColor = when {
        transfer.progress >= 1f -> HyprGreen
        transfer.status.contains("error", ignoreCase = true) -> HyprPink
        else -> HyprBlue
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HyprSurface0),
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
                    text = transfer.status.lowercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = HyprSubtext0
                )
                if (transfer.speed > 0L) {
                    Text(
                        text = "${transfer.speed.toHumanReadableSize()}/s",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = HyprBlue
                    )
                }
            }
        }
    }
}
