package dev.hyprconnect.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hyprconnect.app.ui.theme.*

@Composable
fun VerificationCodeDisplay(
    code: String,
    modifier: Modifier = Modifier
) {
    val parts = if (code.length == 6) listOf(code.take(3), code.takeLast(3)) else listOf(code)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        parts.forEach { part ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(HyprSurface0),
                contentAlignment = Alignment.Center
            ) {
                // Glow accent bar at top
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            HyprBlue,
                            RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        )
                )

                Text(
                    text = part,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    letterSpacing = 8.sp,
                    color = HyprText
                )
            }
        }
    }
}
