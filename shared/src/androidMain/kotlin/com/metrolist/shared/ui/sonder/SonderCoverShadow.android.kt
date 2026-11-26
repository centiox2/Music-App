package com.metrolist.shared.ui.sonder

import android.graphics.Paint
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sonder's original soft shadow, drawn outside the artwork outline. */
actual fun Modifier.sonderCoverShadow(cornerRadius: Dp, weight: Float, dark: Boolean): Modifier = composed {
    val elevation = 22.dp * weight
    drawWithCache {
        val shade = Color.Black.copy(alpha = if (dark) 0.6f else 0.32f).toArgb()
        val paint = Paint().apply {
            isAntiAlias = true
            color = shade
            setShadowLayer(elevation.toPx() * 2.2f, 0f, elevation.toPx() * 0.4f, shade)
        }
        val radius = cornerRadius.toPx()
        val outline = android.graphics.Path().apply { addRoundRect(0f, 0f, size.width, size.height, radius, radius, android.graphics.Path.Direction.CW) }
        onDrawBehind {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                if (Build.VERSION.SDK_INT >= 26) native.clipOutPath(outline)
                else { @Suppress("DEPRECATION") native.clipPath(outline, android.graphics.Region.Op.DIFFERENCE) }
                native.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, paint)
                native.restore()
            }
        }
    }
}
