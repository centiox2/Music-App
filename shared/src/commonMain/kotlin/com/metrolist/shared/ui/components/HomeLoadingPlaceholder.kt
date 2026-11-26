/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.shared.constants.GridThumbnailHeight
import com.metrolist.shared.constants.GridTileSpacing
import com.metrolist.shared.constants.ThumbnailCornerRadius
import com.metrolist.shared.constants.currentGridThumbnailHeight
import com.metrolist.shared.constants.speedDialLayout
import com.metrolist.shared.constants.thumbnailHeight
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.loading
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.theme.uiEnhanced
import org.jetbrains.compose.resources.stringResource

private val ChipWidths = listOf(72.dp, 96.dp, 64.dp, 88.dp, 76.dp, 104.dp, 68.dp)

@Composable
fun HomeLoadingPlaceholder(
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
) {

    val label = stringResource(Res.string.loading)
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp).semantics {
        contentDescription = label; liveRegion = LiveRegionMode.Polite
    }.loadingShimmer(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipWidths.take(3).forEach { SkeletonBlock(Modifier.width(it).height(34.dp), CircleShape) }
        }
        SkeletonBlock(Modifier.fillMaxWidth().height(190.dp), RoundedCornerShape(18.dp))
        SkeletonBlock(Modifier.width(140.dp).height(22.dp), RoundedCornerShape(6.dp))
        repeat(8) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(Modifier.size(48.dp), RoundedCornerShape(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBlock(Modifier.width(160.dp).height(14.dp), CircleShape)
                    SkeletonBlock(Modifier.width(100.dp).height(12.dp), CircleShape)
                }
            }
        }
    }

}

@Composable
private fun ShelfTitlePlaceholder(isWide: Boolean) {
    Column(Modifier.height(if (isWide) 60.dp else 50.dp), verticalArrangement = Arrangement.Center) {
        SkeletonBlock(Modifier.width(168.dp).height(22.dp), RoundedCornerShape(8.dp))
    }
}

@Composable
private fun CarouselItemPlaceholder(
    size: Dp,
    shape: Shape,
) {
    Column(Modifier.width(size), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(1f), shape)
        Spacer(Modifier.height(2.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(14.dp), CircleShape)
        SkeletonBlock(Modifier.fillMaxWidth(0.55f).height(12.dp), CircleShape)
    }
}

@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    shape: Shape,
) {
    Spacer(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
}
