package dev.hyprconnect.app.ui.filetransfer

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
            if (handledUris.add(key)) {
                viewModel.sendSharedUri(context, uri)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File Transfers") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (transfers.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No active transfers", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                items(transfers) { transfer ->
                    val speedText = if (transfer.speed > 0L) {
                        " • ${transfer.speed.toHumanReadableSize()}/s"
                    } else {
                        ""
                    }

                    ListItem(
                        headlineContent = { Text(transfer.name) },
                        supportingContent = {
                            Column {
                                Text("${(transfer.progress * 100).toInt()}% • ${transfer.status}$speedText")
                                LinearProgressIndicator(
                                    progress = { transfer.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                )
                            }
                        },
                        trailingContent = {
                            Text(transfer.size.toHumanReadableSize())
                        }
                    )
                }
            }
        }
    }
}
