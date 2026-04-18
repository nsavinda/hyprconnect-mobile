package dev.hyprconnect.app.ui.clipboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.hyprconnect.app.domain.model.ClipboardEntry
import dev.hyprconnect.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardScreen(
    onNavigateBack: () -> Unit,
    viewModel: ClipboardViewModel = hiltViewModel()
) {
    val history by viewModel.history.collectAsState()

    Scaffold(
        containerColor = HyprBase,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "clipboard",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = HyprText
                        )
                        Text(
                            text = "sync history",
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
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "// no clipboard history yet",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = HyprOverlay0
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "copy something on mobile or PC",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = HyprOverlay0
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Text(
                        "// clipboard history",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = HyprBlue,
                        letterSpacing = 0.8.sp
                    )
                }
                items(history, key = { "${it.timestamp}_${it.source}" }) { entry ->
                    ClipboardEntryCard(
                        entry = entry,
                        onResend = { viewModel.resend(entry) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipboardEntryCard(
    entry: ClipboardEntry,
    onResend: () -> Unit
) {
    val isLocal = entry.source == ClipboardEntry.Source.LOCAL
    val accentColor = if (isLocal) HyprBlue else HyprTeal
    val containerColor = if (isLocal) HyprBlueContainer else HyprTealContainer

    Card(
        onClick = onResend,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HyprSurface0),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Source icon
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLocal) Icons.Default.PhoneAndroid else Icons.Default.Computer,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = accentColor
                )
            }

            Spacer(Modifier.width(12.dp))

            // Content + meta
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.content,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = HyprText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isLocal) "mobile" else "pc",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = accentColor
                    )
                    Text(
                        "  ·  ",
                        fontSize = 10.sp,
                        color = HyprOverlay0
                    )
                    Text(
                        text = formatRelativeTime(entry.timestamp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = HyprOverlay0
                    )
                }
            }

            // Re-send button
            IconButton(onClick = onResend) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "re-send",
                    modifier = Modifier.size(18.dp),
                    tint = HyprSubtext0
                )
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    return when {
        seconds < 5    -> "just now"
        seconds < 60   -> "${seconds}s ago"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
