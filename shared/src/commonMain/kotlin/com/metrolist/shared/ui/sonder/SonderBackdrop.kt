package com.metrolist.shared.ui.sonder

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.metrolist.shared.settings.Settings

/** The quiet cover glow used behind Sonder's browsing screens. */
@Composable
fun Modifier.sonderBackdrop(artworkUrl: String?): Modifier {
    if (Settings.shared.isPureBlackTheme()) return background(MaterialTheme.colorScheme.surface)
    val palette = rememberGlowPalette(artworkUrl)
    val accent = palette?.let(::coverAccent) ?: MaterialTheme.colorScheme.secondary
    val base = MaterialTheme.colorScheme.surface
    return drawWithCache {
        val glow = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0f)),
                center = Offset(size.width * 0.8f, 0f),
                radius = (size.maxDimension * 0.85f).coerceAtLeast(1f),
            )
        onDrawBehind {
            drawRect(base)
            drawRect(glow)
        }
    }
}
