package dev.hyprconnect.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Vertical gradient backdrop that every screen sits on top of. Cards use
 * translucent fills so the gradient bleeds through, producing the frosted
 * glass effect without a real backdrop blur.
 */
val HyprBackdropBrush: Brush
    get() = Brush.verticalGradient(
        0.0f to HyprBackdropTop,
        1.0f to HyprBackdropBottom
    )

/**
 * Inner highlight gradient applied behind glass surfaces — fakes the bright
 * top edge you see on real frosted glass.
 */
val HyprGlassSheen: Brush
    get() = Brush.verticalGradient(
        0.0f to HyprGlassHighlight,
        0.45f to Color.Transparent
    )

/**
 * Wraps a screen's content with the gradient backdrop. Use as the outermost
 * Box inside a Scaffold (with Scaffold containerColor = Color.Transparent).
 */
@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize().background(HyprBackdropBrush)) {
        content()
    }
}

/**
 * Modifier that gives a composable the standard frosted-glass treatment:
 * rounded corners, translucent fill, top-edge sheen, and a hairline border.
 *
 * @param fill base translucent color (defaults to [HyprGlass])
 * @param cornerRadius corner radius
 * @param withSheen draw the inner highlight at the top edge
 */
fun Modifier.glassCard(
    fill: Color = HyprGlass,
    cornerRadius: Dp = 14.dp,
    withSheen: Boolean = true
): Modifier = composedGlass(fill, cornerRadius, withSheen)

private fun Modifier.composedGlass(
    fill: Color,
    cornerRadius: Dp,
    withSheen: Boolean
): Modifier {
    var m = this.clip(RoundedCornerShape(cornerRadius)).background(fill)
    if (withSheen) m = m.background(HyprGlassSheen)
    return m.border(BorderStroke(0.5.dp, HyprGlassBorder), RoundedCornerShape(cornerRadius))
}
