package com.metrolist.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import coil3.Image
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.sonder.GlowMesh
import com.metrolist.shared.ui.sonder.rememberGlowPalette

internal data class LayerArt(
    val painter: Painter,
    val pixelWidth: Float?,
    val pixelHeight: Float?,
    val image: Image? = null,
)

@Composable
internal expect fun PlatformLiquidBackgroundCanvas(
    modifier: Modifier,
    activeArt: LayerArt,
    previousArt: LayerArt?,
    crossfadeAlpha: () -> Float,
    rotationTimeMs: () -> Long,
)

/** Sonder's original cover-weighted mesh, driven by Metrolist's artwork and visibility. */
@Composable
fun LiquidBackground(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    isLyricsVisible: Boolean = false,
    animate: Boolean = true,
    isPlaying: Boolean = true,
    visible: Boolean = true,
) {
    val dark = Settings.shared.getCurrentThemeIsDark()
    val palette = rememberGlowPalette(artworkUrl) ?: com.metrolist.shared.ui.sonder.ArtworkPalettes.last
        ?: com.metrolist.shared.ui.sonder.CoverPalette(listOf(if (dark) androidx.compose.ui.graphics.Color(0xFF121212) else androidx.compose.ui.graphics.Color(0xFFEDEDED)), listOf(1f))
    Box(modifier.fillMaxSize()) {
        com.metrolist.shared.ui.sonder.SonderLiquidArtwork(artworkUrl, palette, Modifier.fillMaxSize(), animate && isPlaying && visible, dark)
        val shade = if (dark) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to shade.copy(alpha = 0.12f), 0.55f to shade.copy(alpha = 0.18f),
            1f to shade.copy(alpha = if (dark) 0.7f else 0.75f),
        )))
    }
}
