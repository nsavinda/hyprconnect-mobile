package dev.hyprconnect.app.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a Hyprland-style glowing border around the composable.
 * Renders three concentric glow rings plus a crisp 1dp main border.
 */
fun Modifier.hyprGlowBorder(
    color: Color,
    cornerRadius: Dp = 14.dp,
    borderWidth: Dp = 1.dp
): Modifier = this.drawBehind {
    val bw   = borderWidth.toPx()
    val cr   = cornerRadius.toPx()

    // Glow layers — widest and most transparent first
    val glowLayers = listOf(
        Pair(10.dp.toPx(), 0.08f),
        Pair(5.dp.toPx(),  0.15f),
        Pair(2.dp.toPx(),  0.25f),
    )

    for ((expand, alpha) in glowLayers) {
        drawRoundRect(
            color        = color.copy(alpha = alpha),
            topLeft      = Offset(-expand / 2f, -expand / 2f),
            size         = Size(size.width + expand, size.height + expand),
            cornerRadius = CornerRadius(cr + expand / 2f),
            style        = Stroke(width = expand)
        )
    }

    // Crisp main border
    drawRoundRect(
        color        = color,
        cornerRadius = CornerRadius(cr),
        style        = Stroke(width = bw)
    )
}
