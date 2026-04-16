package dev.hyprconnect.app.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.hyprconnect.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaControlScreen(
    viewModel: MediaControlViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) { viewModel.startPolling() }
    DisposableEffect(Unit) { onDispose { viewModel.stopPolling() } }

    val isPlaying = nowPlaying?.status?.lowercase() == "playing"

    Scaffold(
        containerColor = HyprBase,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "media control",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HyprMantle)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            // Album art placeholder
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(HyprSurface0),
                contentAlignment = Alignment.Center
            ) {
                // Decorative rings
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(HyprMantle),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(HyprSurface1),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            null,
                            Modifier.size(36.dp),
                            tint = if (isPlaying) HyprBlue else HyprOverlay0
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Track info
            if (nowPlaying != null) {
                Text(
                    text = nowPlaying!!.title.ifEmpty { "Unknown Track" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = HyprText
                )
                if (nowPlaying!!.artist.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = nowPlaying!!.artist,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = HyprSubtext0,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (nowPlaying!!.album.isNotEmpty()) {
                    Text(
                        text = nowPlaying!!.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = HyprOverlay0,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Status badge
                val statusText = when (nowPlaying!!.status.lowercase()) {
                    "playing" -> "▶ playing"
                    "paused"  -> "⏸ paused"
                    else      -> "⏹ stopped"
                }
                val statusColor = when (nowPlaying!!.status.lowercase()) {
                    "playing" -> HyprGreen
                    "paused"  -> HyprYellow
                    else      -> HyprOverlay0
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        statusText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = statusColor
                    )
                }
            } else {
                Text(
                    "no media playing",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = HyprSubtext0
                )
            }

            Spacer(Modifier.weight(1f))

            // Transport controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = HyprSurface0),
                shape  = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous
                    IconButton(
                        onClick = { viewModel.previous() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            null,
                            Modifier.size(32.dp),
                            tint = HyprSubtext1
                        )
                    }

                    // Play / Pause — larger + accented
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) HyprBlue else HyprBlueContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { if (isPlaying) viewModel.pause() else viewModel.play() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                null,
                                Modifier.size(32.dp),
                                tint = if (isPlaying) HyprCrust else HyprBlue
                            )
                        }
                    }

                    // Next
                    IconButton(
                        onClick = { viewModel.next() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            null,
                            Modifier.size(32.dp),
                            tint = HyprSubtext1
                        )
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    error!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = HyprPink,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
