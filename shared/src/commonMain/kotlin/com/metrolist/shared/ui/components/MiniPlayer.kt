/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.metrolist.shared.constants.MiniPlayerHeight
import com.metrolist.shared.constants.ModernMiniPlayerHorizontalPadding
import com.metrolist.shared.constants.modernMiniPlayerHeight
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.action_like
import com.metrolist.shared.generated.resources.loading
import com.metrolist.shared.generated.resources.next
import com.metrolist.shared.generated.resources.previous
import com.metrolist.shared.generated.resources.pause
import com.metrolist.shared.generated.resources.play
import com.metrolist.shared.generated.resources.unlike
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.isLive
import com.metrolist.shared.model.isVideoSong
import com.metrolist.shared.model.previewThumbnailUrl
import com.metrolist.shared.settings.MiniPlayerType
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.settings.displayName
import com.metrolist.shared.ui.LocalIsTv
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.design.expressive.components.ExpressiveMiniPlayer
import com.metrolist.shared.ui.haptics.rememberMetrolistHaptics
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.sonder.miniPlayerTint
import com.metrolist.shared.ui.sonder.rememberGlowPalette
import com.metrolist.shared.ui.theme.getGradientColors
import com.metrolist.shared.ui.theme.rememberArtworkSeedColor
import com.metrolist.shared.ui.utils.artworkBlur
import com.metrolist.shared.ui.utils.supportsNativeBlur
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniPlayer(
    song: SongItem,
    isPlaying: Boolean,
    isLoading: Boolean,
    positionMs: () -> Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    isLiked: Boolean = false,
    onLike: (() -> Unit)? = null,
    artistId: String? = null,
    isArtistSubscribed: Boolean = false,
    onSubscribeToggle: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    canSkipNext: Boolean = true,
    canSkipPrevious: Boolean = true,
    error: String? = null,
    isActive: Boolean = true,
    playbackEnabled: Boolean = true,
) {
    val palette = rememberGlowPalette(song.thumbnail)
    val tint = miniPlayerTint(palette, MaterialTheme.colorScheme.primaryContainer, Settings.shared.getCurrentThemeIsDark())
    val shape = RoundedCornerShape(18.dp)
    var drag by remember { mutableFloatStateOf(0f) }
    val remote = LocalIsTv.current
    val showLike = onLike != null && Settings.shared.showMiniPlayerLikeButton
    val swipeThreshold = 0.5f - Settings.shared.swipeSensitivity.coerceIn(0f, 100f) * 0.004f
    Box(modifier.fillMaxWidth().height(MiniPlayerHeight).padding(horizontal = 12.dp).clip(shape)
        .background(tint).pointerInput(canSkipNext, canSkipPrevious, playbackEnabled, swipeThreshold) {
            detectHorizontalDragGestures(onDragEnd = {
                if (drag < -size.width * swipeThreshold && canSkipNext && playbackEnabled) onSkipNext()
                if (drag > size.width * swipeThreshold && canSkipPrevious && playbackEnabled) onSkipPrevious()
                drag = 0f
            }, onDragCancel = { drag = 0f }) { change, amount -> change.consume(); drag += amount }
        }.clickable(onClick = onExpand)) {
        Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(song.previewThumbnailUrl, Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artists.joinToString { it.displayName }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (remote) com.metrolist.shared.ui.sonder.SonderIcon(MetrolistIcons.Rounded.SkipPrevious, stringResource(Res.string.previous), onSkipPrevious, enabled = canSkipPrevious && playbackEnabled)
            com.metrolist.shared.ui.sonder.SonderIcon(if (isPlaying) MetrolistIcons.Rounded.Pause else MetrolistIcons.Rounded.PlayArrow, stringResource(if (isPlaying) Res.string.pause else Res.string.play), onTogglePlay, enabled = playbackEnabled)
            if (showLike && onLike != null) com.metrolist.shared.ui.sonder.SonderIcon(if (isLiked) MetrolistIcons.Rounded.Favorite else MetrolistIcons.Rounded.FavoriteBorder, stringResource(if (isLiked) Res.string.unlike else Res.string.action_like), onLike, enabled = playbackEnabled)
            if (remote || !showLike) com.metrolist.shared.ui.sonder.SonderIcon(MetrolistIcons.Rounded.SkipNext, stringResource(Res.string.next), onSkipNext, enabled = canSkipNext && playbackEnabled)
        }
        val color = MaterialTheme.colorScheme.onSurface
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).drawWithContent {
            drawRect(color.copy(alpha = 0.14f))
            val fraction = if (durationMs > 0) (positionMs().toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            drawRect(color, size = Size(size.width * fraction, size.height))
        })
    }
}
