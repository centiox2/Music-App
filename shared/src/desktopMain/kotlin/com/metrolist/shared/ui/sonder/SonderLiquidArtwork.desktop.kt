package com.metrolist.shared.ui.sonder

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
actual fun SonderLiquidArtwork(url: String?, fallback: CoverPalette, modifier: Modifier, moving: Boolean, dark: Boolean) {
    GlowMesh(fallback, modifier, moving, dark)
}

actual fun Modifier.sonderCoverShadow(cornerRadius: Dp, weight: Float, dark: Boolean): Modifier =
    shadow(22.dp * weight, RoundedCornerShape(cornerRadius), clip = false)
