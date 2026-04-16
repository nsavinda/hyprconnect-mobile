package dev.hyprconnect.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Fixed Hyprland / Catppuccin Mocha dark scheme — no dynamic color
private val HyprColorScheme = darkColorScheme(
    primary              = HyprBlue,
    onPrimary            = HyprCrust,
    primaryContainer     = HyprBlueContainer,
    onPrimaryContainer   = HyprBlue,

    secondary            = HyprMauve,
    onSecondary          = HyprCrust,
    secondaryContainer   = HyprMauveContainer,
    onSecondaryContainer = HyprMauve,

    tertiary             = HyprTeal,
    onTertiary           = HyprCrust,
    tertiaryContainer    = HyprTealContainer,
    onTertiaryContainer  = HyprTeal,

    error                = HyprPink,
    onError              = HyprCrust,
    errorContainer       = HyprPinkContainer,
    onErrorContainer     = HyprPink,

    background           = HyprBase,
    onBackground         = HyprText,

    surface              = HyprMantle,
    onSurface            = HyprText,
    surfaceVariant       = HyprSurface0,
    onSurfaceVariant     = HyprSubtext1,

    outline              = HyprSurface2,
    outlineVariant       = HyprSurface1,

    scrim                = HyprCrust,
    inverseSurface       = HyprText,
    inverseOnSurface     = HyprBase,
    inversePrimary       = Color(0xFF3a5db5),
    surfaceTint          = HyprBlue
)

// Hyprland-inspired shapes — generously rounded everywhere
private val HyprShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(10.dp),
    medium     = RoundedCornerShape(14.dp),
    large      = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HyprConnectTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = HyprMantle.toArgb()
            window.navigationBarColor = HyprMantle.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = HyprColorScheme,
        shapes      = HyprShapes,
        typography  = Typography,
        content     = content
    )
}
