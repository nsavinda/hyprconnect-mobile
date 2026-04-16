package dev.hyprconnect.app.ui.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.hyprconnect.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    deviceId: String,
    viewModel: PairingViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onPairingSuccess: () -> Unit
) {
    val isPairing       by viewModel.isPairing.collectAsState()
    val isSubmitting    by viewModel.isSubmitting.collectAsState()
    val connectionReady by viewModel.connectionReady.collectAsState()
    val error           by viewModel.error.collectAsState()
    val isPaired        by viewModel.isPaired.collectAsState(initial = false)

    var codeInput by remember { mutableStateOf("") }

    LaunchedEffect(deviceId) {
        if (deviceId.isNotEmpty()) viewModel.startPairing(deviceId)
    }
    LaunchedEffect(isPaired) {
        if (isPaired) onPairingSuccess()
    }

    Scaffold(
        containerColor = HyprBase,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "pair device",
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                error != null -> {
                    // Error state
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(HyprPinkContainer)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "pairing failed",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = HyprPink
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = error!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = HyprSubtext1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.startPairing(deviceId) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HyprSurface0,
                            contentColor = HyprText
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("try again", fontFamily = FontFamily.Monospace)
                    }
                }

                isPairing -> {
                    // Connecting state
                    CircularProgressIndicator(
                        color = HyprBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "connecting...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = HyprSubtext0
                    )
                }

                connectionReady -> {
                    // Code input state
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Top accent
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(HyprBlue)
                        )
                        Spacer(Modifier.height(24.dp))

                        Text(
                            "complete pairing",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = HyprText
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "enter the 6-digit code shown on\nyour desktop terminal",
                            style = MaterialTheme.typography.bodySmall,
                            color = HyprSubtext0,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(Modifier.height(32.dp))

                        // Code input field — monospace styled
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { v ->
                                if (v.length <= 6 && v.all { it.isDigit() }) codeInput = v
                            },
                            placeholder = {
                                Text(
                                    "______",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 28.sp,
                                    letterSpacing = 12.sp,
                                    color = HyprSurface2
                                )
                            },
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 28.sp,
                                letterSpacing = 12.sp,
                                color = HyprText,
                                textAlign = TextAlign.Center
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = HyprBlue,
                                unfocusedBorderColor = HyprSurface2,
                                cursorColor          = HyprBlue,
                                focusedContainerColor   = HyprSurface0,
                                unfocusedContainerColor = HyprSurface0
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = { viewModel.submitCode(codeInput) },
                            enabled = codeInput.length == 6 && !isSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HyprBlue,
                                contentColor   = HyprCrust,
                                disabledContainerColor = HyprSurface1,
                                disabledContentColor   = HyprOverlay0
                            )
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = HyprCrust
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("verifying...", fontFamily = FontFamily.Monospace)
                            } else {
                                Text(
                                    "complete pairing",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HyprSubtext1),
                border = androidx.compose.foundation.BorderStroke(1.dp, HyprSurface2)
            ) {
                Text("cancel", fontFamily = FontFamily.Monospace)
            }
        }
    }
}
