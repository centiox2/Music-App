package com.music.bitchord.ui.glow

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.graphics.ColorUtils
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The liquid backdrop: four blurred blobs of the cover's colours drifting
 * around each other. Drawn the way BitChord's MeshGradientBackground draws
 * its mesh, but standalone — that one reads the player host BitChord's own
 * UI installs, which this app doesn't, and crashed without it.
 */
@Composable
fun GlowMesh(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    moving: Boolean = true,
    blurRadius: Dp = 64.dp,
    loopMillis: Int = 36_000,
) {
    val tuned = (colors.ifEmpty { listOf(Color.DarkGray) }.let { c -> List(4) { c[it % c.size] } }).map { it.tuned() }
    val spec = tween<Color>(1400)
    val animated = tuned.mapIndexed { i, c -> animateColorAsState(c, spec, label = "mesh$i").value }
    val base by animateColorAsState(tuned.first().dimmed(), if (moving) spec else snap(), label = "meshBase")
    val phase = remember { Animatable(0f) }
    LaunchedEffect(moving) {
        if (!moving) return@LaunchedEffect
        while (isActive) {
            phase.animateTo(phase.value + (2 * PI).toFloat(), tween(loopMillis, easing = LinearEasing))
        }
    }
    Canvas(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer { scaleX = 1.3f; scaleY = 1.3f }
            .background(base)
            .blur(blurRadius),
    ) {
        val anchors = listOf(Offset(0.20f, 0.25f), Offset(0.80f, 0.20f), Offset(0.75f, 0.80f), Offset(0.25f, 0.75f))
        val speeds = listOf(1f, -0.7f, 0.85f, -1.15f)
        val drift = phase.value
        animated.forEachIndexed { i, color ->
            val a = anchors[i]
            val center = Offset(
                (a.x + 0.16f * cos(drift * speeds[i] + i * 1.7f)) * size.width,
                (a.y + 0.16f * sin(drift * speeds[i] * 0.9f + i * 2.3f)) * size.height,
            )
            val radius = size.maxDimension * 0.62f
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0f)), center, radius), radius, center)
        }
    }
}

private fun Color.tuned(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = (hsl[1] * 1.35f).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceIn(0.28f, 0.58f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.dimmed(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[2] = 0.12f
    return Color(ColorUtils.HSLToColor(hsl))
}
