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
    val palette = rememberGlowPalette(artworkUrl)
    val dark = Settings.shared.getCurrentThemeIsDark()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (palette != null) {
            GlowMesh(
                palette = palette,
                modifier = Modifier.fillMaxSize().alpha(if (isLyricsVisible) 0.7f else 1f),
                moving = animate && isPlaying && visible,
                dark = dark,
            )
        }
    }
}
