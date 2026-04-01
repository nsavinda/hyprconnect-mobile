package dev.hyprconnect.app.ui.pairing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    deviceId: String,
    viewModel: PairingViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onPairingSuccess: () -> Unit
) {
    val isPairing by viewModel.isPairing.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val connectionReady by viewModel.connectionReady.collectAsState()
    val error by viewModel.error.collectAsState()
    val isPaired by viewModel.isPaired.collectAsState(initial = false)

    var codeInput by remember { mutableStateOf("") }

    LaunchedEffect(deviceId) {
        if (deviceId.isNotEmpty()) {
            viewModel.startPairing(deviceId)
        }
    }

    LaunchedEffect(isPaired) {
        if (isPaired) {
            onPairingSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair Device") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Complete Pairing",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            if (error != null) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { viewModel.startPairing(deviceId) }) {
                    Text("Try Again")
                }
            } else if (isPairing) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Connecting to device...")
            } else if (connectionReady) {
                Text(
                    "Enter the 6-digit code shown on your desktop terminal",
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))

                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { value ->
                        if (value.length <= 6 && value.all { it.isDigit() }) {
                            codeInput = value
                        }
                    },
                    label = { Text("Pairing Code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.submitCode(codeInput) },
                    enabled = codeInput.length == 6 && !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Verifying...")
                    } else {
                        Text("Complete Pairing")
                    }
                }
            }

            Spacer(Modifier.height(48.dp))

            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}
