package dev.hyprconnect.app.ui.remoteinput

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.hyprconnect.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun RemoteInputScreen(
    viewModel: RemoteInputViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val showKeyboard by viewModel.showKeyboard.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    val sensitivity = 1.5f
    var lastX by remember { mutableFloatStateOf(0f) }
    var lastY by remember { mutableFloatStateOf(0f) }
    var pointerCount by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var scrollLastY by remember { mutableFloatStateOf(0f) }
    var accumulatedScrollY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(showKeyboard) {
        if (showKeyboard) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    Scaffold(
        containerColor = HyprBase,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "remote input",
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
                actions = {
                    IconButton(onClick = { viewModel.toggleKeyboard() }) {
                        Icon(
                            Icons.Default.Keyboard,
                            null,
                            tint = if (showKeyboard) HyprBlue else HyprSubtext1
                        )
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
                .padding(12.dp)
        ) {
            // Touchpad surface with dot grid
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(HyprMantle)
                    .pointerInteropFilter { event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                lastX = event.x; lastY = event.y
                                pointerCount = 1; isDragging = false; true
                            }
                            MotionEvent.ACTION_POINTER_DOWN -> {
                                pointerCount = event.pointerCount
                                if (pointerCount == 2) {
                                    scrollLastY = event.getY(1)
                                    accumulatedScrollY = 0f
                                }; true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (pointerCount >= 2) {
                                    val currentY = event.getY(0)
                                    val dy = currentY - lastY
                                    accumulatedScrollY += dy
                                    if (kotlin.math.abs(accumulatedScrollY) > 5f) {
                                        viewModel.sendScroll(0f, accumulatedScrollY / 10f)
                                        accumulatedScrollY = 0f
                                    }
                                    lastY = currentY
                                } else {
                                    val dx = (event.x - lastX) * sensitivity
                                    val dy = (event.y - lastY) * sensitivity
                                    if (kotlin.math.abs(dx) > 0.5f || kotlin.math.abs(dy) > 0.5f) {
                                        isDragging = true
                                        viewModel.sendMouseMove(dx, dy)
                                    }
                                    lastX = event.x; lastY = event.y
                                }; true
                            }
                            MotionEvent.ACTION_POINTER_UP -> {
                                if (pointerCount == 2 && !isDragging && kotlin.math.abs(accumulatedScrollY) < 10f) {
                                    viewModel.sendClick("right")
                                }
                                pointerCount = event.pointerCount - 1; true
                            }
                            MotionEvent.ACTION_UP -> {
                                if (!isDragging && pointerCount == 1) viewModel.sendClick("left")
                                pointerCount = 0; isDragging = false; true
                            }
                            else -> false
                        }
                    }
            ) {
                // Dot grid pattern — Hyprland desktop feel
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dotR   = 1.5.dp.toPx()
                    val spacing = 28.dp.toPx()
                    val rows = (size.height / spacing).toInt() + 2
                    val cols = (size.width / spacing).toInt() + 2
                    for (row in 0..rows) {
                        for (col in 0..cols) {
                            drawCircle(
                                color  = HyprSurface1,
                                radius = dotR,
                                center = Offset(col * spacing, row * spacing)
                            )
                        }
                    }
                }

                // Center hint text
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.TouchApp,
                        null,
                        Modifier.size(32.dp),
                        tint = HyprOverlay0.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "touchpad",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = HyprOverlay0.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "tap · 2-finger tap · 2-finger drag",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = HyprOverlay0.copy(alpha = 0.25f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Mouse button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MouseButton(
                    label = "Left",
                    modifier = Modifier.weight(1f),
                    containerColor = HyprBlueContainer,
                    contentColor = HyprBlue,
                    onClick = { viewModel.sendClick("left") }
                )
                MouseButton(
                    label = "Mid",
                    modifier = Modifier.weight(0.6f),
                    containerColor = HyprSurface0,
                    contentColor = HyprSubtext1,
                    onClick = { viewModel.sendClick("middle") }
                )
                MouseButton(
                    label = "Right",
                    modifier = Modifier.weight(1f),
                    containerColor = HyprMauveContainer,
                    contentColor = HyprMauve,
                    onClick = { viewModel.sendClick("right") }
                )
            }

            // Keyboard input field
            if (showKeyboard) {
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val diff = newValue.text.length - textFieldValue.text.length
                        if (diff > 0) {
                            viewModel.sendText(newValue.text.substring(textFieldValue.text.length))
                        } else if (diff < 0) {
                            viewModel.sendKey("BackSpace")
                        }
                        textFieldValue = newValue
                    },
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(HyprSurface0)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = HyprText,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(HyprBlue),
                    decorationBox = { innerTextField ->
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                "type here...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HyprOverlay0,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MouseButton(
    label: String,
    modifier: Modifier,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor   = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
