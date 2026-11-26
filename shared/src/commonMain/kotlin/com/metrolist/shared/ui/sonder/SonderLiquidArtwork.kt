package com.metrolist.shared.ui.sonder

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** Sonder's cover-twisting liquid effect; compact/software hosts use the same palette mesh. */
@Composable
expect fun SonderLiquidArtwork(url: String?, fallback: CoverPalette, modifier: Modifier = Modifier, moving: Boolean = true, dark: Boolean = true)

expect fun Modifier.sonderCoverShadow(cornerRadius: Dp, weight: Float = 1f, dark: Boolean = true): Modifier
