package com.metrolist.shared.ui.sonder

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs

/** Platform independent HSL conversion for Sonder's artwork surfaces. */
internal object SonderColorUtils {
    fun colorToHSL(argb: Int, out: FloatArray) {
        val r = ((argb shr 16) and 255) / 255f
        val g = ((argb shr 8) and 255) / 255f
        val b = (argb and 255) / 255f
        val high = maxOf(r, g, b)
        val low = minOf(r, g, b)
        val delta = high - low
        val lightness = (high + low) / 2f
        out[0] = if (delta == 0f) 0f else {
            val sector = when (high) {
                r -> (g - b) / delta
                g -> (b - r) / delta + 2f
                else -> (r - g) / delta + 4f
            }
            (sector * 60f + 360f) % 360f
        }
        out[1] = if (delta == 0f) 0f else delta / (1f - abs(2f * lightness - 1f))
        out[2] = lightness
    }

    fun HSLToColor(hsl: FloatArray): Int {
        val hue = ((hsl[0] % 360f) + 360f) % 360f / 60f
        val saturation = hsl[1].coerceIn(0f, 1f)
        val lightness = hsl[2].coerceIn(0f, 1f)
        val chroma = (1f - abs(2f * lightness - 1f)) * saturation
        val x = chroma * (1f - abs(hue % 2f - 1f))
        val m = lightness - chroma / 2f
        val (r, g, b) = when (hue.toInt()) {
            0 -> Triple(chroma, x, 0f)
            1 -> Triple(x, chroma, 0f)
            2 -> Triple(0f, chroma, x)
            3 -> Triple(0f, x, chroma)
            4 -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }
        return Color(r + m, g + m, b + m).toArgb()
    }
}
