package com.music.bitchord.ui.glow

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.music.bitchord.ui.graphics.ColorUtils
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val SLOTS = 6

/**
 * The liquid backdrop, drawn to the cover's real proportions.
 *
 * The cover's main colour fills the screen; every other colour is a soft blob
 * whose area matches its share of the cover — a red stripe that is 5% of a
 * black sleeve is a small red glow on black, not half the screen. Two shades
 * of the main colour drift across it so even a one-colour cover moves. No
 * saturation boost and no invented colours.
 *
 * Radial gradients are soft already, so there is no full-screen blur: that
 * was the most expensive thing on screen, redrawn every frame.
 */
@Composable
fun GlowMesh(
    palette: CoverPalette,
    modifier: Modifier = Modifier,
    moving: Boolean = true,
    dark: Boolean = true,
    loopMillis: Int = 40_000,
) {
    val spec = tween<Color>(1200)
    val base by animateColorAsState(readable(palette.dominant, dark), spec, label = "meshBase")
    val shadeA by animateColorAsState(readable(shade(palette.dominant, +0.07f), dark), spec, label = "meshShadeA")
    val shadeB by animateColorAsState(readable(shade(palette.dominant, -0.06f), dark), spec, label = "meshShadeB")
    // Slots for the cover's other colours, each with the share of the cover it covers.
    val others = palette.colors.drop(1).zip(palette.weights.drop(1)).take(SLOTS)
    val slotColors = List(SLOTS) { i ->
        val target = others.getOrNull(i)?.first?.let { readable(it, dark) } ?: base.copy(alpha = 0f)
        animateColorAsState(target, spec, label = "meshColor$i").value
    }
    val slotWeights = List(SLOTS) { i ->
        animateFloatAsState(others.getOrNull(i)?.second ?: 0f, tween(1200), label = "meshWeight$i").value
    }
    val phase = remember { Animatable(0f) }
    LaunchedEffect(moving) {
        if (!moving) return@LaunchedEffect
        while (isActive) {
            phase.animateTo(phase.value + (2 * PI).toFloat(), tween(loopMillis, easing = LinearEasing))
        }
    }
    Canvas(modifier.fillMaxSize().clipToBounds()) {
        drawRect(base)
        val area = size.width * size.height
        val drift = phase.value
        // A soft blob's visible area is about half its circle's; the radius is set so that
        // half-area matches the colour's share of the screen.
        fun radiusFor(weight: Float) = sqrt(weight * area / (0.5f * PI.toFloat())).coerceAtMost(size.maxDimension)
        fun blob(color: Color, weight: Float, anchor: Offset, speed: Float, seed: Float, alpha: Float = 0.95f) {
            if (weight <= 0.001f || color.alpha <= 0.01f) return
            val r = radiusFor(weight)
            val center = Offset(
                (anchor.x + 0.14f * cos(drift * speed + seed)) * size.width,
                (anchor.y + 0.10f * sin(drift * speed * 0.8f + seed * 1.3f)) * size.height,
            )
            drawCircle(
                Brush.radialGradient(
                    0f to color.copy(alpha = alpha * color.alpha),
                    0.55f to color.copy(alpha = alpha * color.alpha * 0.55f),
                    1f to color.copy(alpha = 0f),
                    center = center,
                    radius = r,
                ),
                r,
                center,
            )
        }
        blob(shadeA, 0.28f, Offset(0.25f, 0.3f), 1f, 0.3f, alpha = 0.6f)
        blob(shadeB, 0.28f, Offset(0.75f, 0.7f), -0.8f, 2.1f, alpha = 0.6f)
        val anchors = listOf(Offset(0.72f, 0.22f), Offset(0.25f, 0.68f), Offset(0.5f, 0.45f), Offset(0.82f, 0.62f), Offset(0.18f, 0.2f), Offset(0.6f, 0.85f))
        val speeds = listOf(0.9f, -1.1f, 0.7f, -0.6f, 1.2f, -0.95f)
        slotColors.forEachIndexed { i, color ->
            blob(color, slotWeights[i], anchors[i], speeds[i], i * 1.7f)
        }
    }
}

/**
 * The colour as it should sit behind white controls: hue and saturation kept,
 * only very bright colours brought down. Black stays black.
 */
private fun readable(color: Color, dark: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[2] = if (dark) minOf(hsl[2], 0.62f) * 0.88f else 0.12f + hsl[2] * 0.82f
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun shade(color: Color, by: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[2] = (hsl[2] + by).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}
