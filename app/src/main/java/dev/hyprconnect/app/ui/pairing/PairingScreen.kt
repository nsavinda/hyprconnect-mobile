package dev.hyprconnect.app.ui.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.hyprconnect.app.ui.components.VerificationCodeDisplay
import dev.hyprconnect.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    deviceId: String,
    viewModel: PairingViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onPairingSuccess: () -> Unit
) {
    val isPairing         by viewModel.isPairing.collectAsState()
    val isSubmitting      by viewModel.isSubmitting.collectAsState()
    val connectionReady   by viewModel.connectionReady.collectAsState()
    val error             by viewModel.error.collectAsState()
    val isPaired          by viewModel.isPaired.collectAsState(initial = false)
    val sas               by viewModel.sas.collectAsState()
    val waitingForDesktop by viewModel.waitingForDesktop.collectAsState()

    LaunchedEffect(deviceId) {
        if (deviceId.isNotEmpty()) viewModel.startPairing(deviceId)
    }
    LaunchedEffect(isPaired) {
        if (isPaired) onPairingSuccess()
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Pair Device",
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
                                "Pairing Failed",
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
                            containerColor = HyprGlass,
                            contentColor = HyprText
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Try Again", fontFamily = FontFamily.Monospace)
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
                        "Connecting...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = HyprSubtext0
                    )
                }

                connectionReady -> {
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
                            if (waitingForDesktop) "Waiting for Desktop" else "Verify Code",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = HyprText
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (waitingForDesktop)
                                "Approve the Same Code on Your\nDesktop Terminal to Finish Pairing"
                            else
                                "Make Sure This Number Matches\nWhat Your Desktop Shows",
                            style = MaterialTheme.typography.bodySmall,
                            color = HyprSubtext0,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(Modifier.height(32.dp))

                        // SAS display — same widget the desktop CLI text mirrors.
                        sas?.let { code ->
                            VerificationCodeDisplay(code = code)
                        }

                        Spacer(Modifier.height(28.dp))

                        if (waitingForDesktop) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = HyprBlue
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Run on Desktop:  hyprconnect pair-approve",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = HyprSubtext0
                                )
                            }
                        } else {
                            Button(
                                onClick = { viewModel.confirmMatch() },
                                enabled = sas != null && !isSubmitting,
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
                                    Text("Confirming…", fontFamily = FontFamily.Monospace)
                                } else {
                                    Text(
                                        "Codes Match — Confirm",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
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
                Text("Cancel", fontFamily = FontFamily.Monospace)
            }
        }
    }
}
