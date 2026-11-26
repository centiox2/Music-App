/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.metrolist.shared.api.artwork.CanvasContentSource
import com.metrolist.shared.cast.CastState
import com.metrolist.shared.constants.QueuePeekHeight
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.action_like
import com.metrolist.shared.generated.resources.album_art
import com.metrolist.shared.generated.resources.cast
import com.metrolist.shared.generated.resources.collapse
import com.metrolist.shared.generated.resources.expand
import com.metrolist.shared.generated.resources.lyrics
import com.metrolist.shared.generated.resources.menu_seek_backward
import com.metrolist.shared.generated.resources.menu_seek_forward
import com.metrolist.shared.generated.resources.more_lyrics_options
import com.metrolist.shared.generated.resources.more_options
import com.metrolist.shared.generated.resources.next
import com.metrolist.shared.generated.resources.now_playing
import com.metrolist.shared.generated.resources.pause
import com.metrolist.shared.generated.resources.play
import com.metrolist.shared.generated.resources.playback_reconnecting
import com.metrolist.shared.generated.resources.previous
import com.metrolist.shared.generated.resources.queue
import com.metrolist.shared.generated.resources.repeat
import com.metrolist.shared.generated.resources.seek_feedback_description
import com.metrolist.shared.generated.resources.seek_feedback_seconds
import com.metrolist.shared.generated.resources.share
import com.metrolist.shared.generated.resources.sleep_timer
import com.metrolist.shared.generated.resources.small_album_art
import com.metrolist.shared.generated.resources.song
import com.metrolist.shared.generated.resources.unknown
import com.metrolist.shared.generated.resources.unlike
import com.metrolist.shared.generated.resources.video
import com.metrolist.shared.playback.LocalPlayerSession
import com.metrolist.shared.playback.SleepTimerState
import com.metrolist.shared.settings.PlayerBackgroundStyle
import com.metrolist.shared.settings.PlayerThumbnailStyle
import com.metrolist.shared.settings.PlayerType
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.LocalIsInPipMode
import com.metrolist.shared.ui.LocalIsTv
import com.metrolist.shared.ui.LocalPlayerImmersiveMode
import com.metrolist.shared.ui.components.BlurredArtworkBackground
import com.metrolist.shared.ui.components.BottomSheet
import com.metrolist.shared.ui.components.BottomSheetState
import com.metrolist.shared.ui.components.COLLAPSED_ANCHOR
import com.metrolist.shared.ui.components.CrossfadeArtwork
import com.metrolist.shared.ui.components.LiquidBackground
import com.metrolist.shared.ui.components.LoadingScreen
import com.metrolist.shared.ui.components.LocalPlayerArtworkPrefetchUrls
import com.metrolist.shared.ui.components.PlayerPrimaryControls
import com.metrolist.shared.ui.components.PopupVisibility
import com.metrolist.shared.ui.components.SleepTimerDialog
import com.metrolist.shared.ui.components.VintageAudioVideoModeSwitcher
import com.metrolist.shared.ui.components.VintageClassicActionButtons
import com.metrolist.shared.ui.components.VintageClassicBottomBar
import com.metrolist.shared.ui.components.VintageClassicControls
import com.metrolist.shared.ui.components.VintageClassicControlsText
import com.metrolist.shared.ui.components.VintagePlayerControls
import com.metrolist.shared.ui.components.adjacentArtworkUrls
import com.metrolist.shared.ui.components.focusOutline
import com.metrolist.shared.ui.components.formatSleepTimerRemaining
import com.metrolist.shared.ui.components.lyrics.LyricsContrastScrim
import com.metrolist.shared.ui.components.lyrics.LyricsOffsetEditor
import com.metrolist.shared.ui.components.playerControlBlend
import com.metrolist.shared.ui.components.rememberBottomSheetState
import com.metrolist.shared.ui.components.rememberTrackMarquee
import com.metrolist.shared.ui.components.sheetBackContent
import com.metrolist.shared.ui.components.vintageOnButtonColor
import com.metrolist.shared.ui.haptics.HapticEvent
import com.metrolist.shared.ui.haptics.rememberMetrolistHaptics
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.navigation.NavigationBackHandler
import com.metrolist.shared.ui.navigation.blockInput
import com.metrolist.shared.ui.theme.ComponentShapes
import com.metrolist.shared.ui.utils.supportsNativeBlur
import com.metrolist.shared.utils.Logger
import com.metrolist.shared.viewmodels.PlayerViewModel
import com.metrolist.shared.youtube.playback.PlaybackEventKind
import com.metrolist.shared.youtube.playback.PlaybackEventLog
import com.metrolist.shared.youtube.playback.PlaybackNetworkStateStore
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun Modifier.artworkSwipe(
    enabled: Boolean,
    sensitivity: Float,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
): Modifier {
    if (!enabled) return this
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val currentOnNext by rememberUpdatedState(onNext)
    var totalDrag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val offset =
        animateFloatAsState(
            targetValue = totalDrag,
            animationSpec =
                if (dragging) {
                    snap()
                } else {
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    )
                },
            label = "artworkSwipe",
        )
    return graphicsLayer { translationX = offset.value }
        .pointerInput(sensitivity) {
            detectHorizontalDragGestures(
                onDragStart = { dragging = true },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    totalDrag += dragAmount
                },
                onDragEnd = {
                    val threshold = artworkSwipeThresholdPx(size.width.toFloat(), sensitivity)
                    when {
                        totalDrag >= threshold -> currentOnPrevious()
                        totalDrag <= -threshold -> currentOnNext()
                    }
                    dragging = false
                    totalDrag = 0f
                },
                onDragCancel = {
                    dragging = false
                    totalDrag = 0f
                },
            )
        }
}

@Composable
internal fun Modifier.artworkSeekGestures(
    seekEnabled: Boolean,
    stepMs: Long,
    onTap: (() -> Unit)?,
    tapLabel: String?,
    progressive: Boolean = false,
    onSeek: (Long) -> Unit,
): Modifier {
    if (!seekEnabled && onTap != null) {
        return focusOutline(RoundedCornerShape(PlayerArtworkCornerRadius)).clickable(
            interactionSource = null,
            indication = null,
            onClickLabel = tapLabel,
            onClick = onTap,
        )
    }
    if (!seekEnabled) return this
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentProgressive by rememberUpdatedState(progressive)
    var skipMultiplier by remember { mutableStateOf(1) }
    var lastTapMark by remember { mutableStateOf<TimeMark?>(null) }
    return this
        .focusOutline(RoundedCornerShape(PlayerArtworkCornerRadius))
        .semantics {
            if (onTap != null) {
                role = Role.Button
                onClick(label = tapLabel) {
                    onTap()
                    true
                }
            }
        }.onKeyEvent { event ->
            if (onTap != null && (event.key == Key.Enter || event.key == Key.DirectionCenter || event.key == Key.Spacebar)) {
                if (event.type == KeyEventType.KeyUp) onTap()
                true
            } else {
                false
            }
        }.focusable(enabled = onTap != null)
        .pointerInput(stepMs) {
            detectTapGestures(
                onTap = { currentOnTap?.invoke() },
                onDoubleTap = { offset ->
                    val elapsedMs = lastTapMark?.elapsedNow()?.inWholeMilliseconds ?: Long.MAX_VALUE
                    skipMultiplier =
                        if (currentProgressive) nextSeekMultiplier(skipMultiplier, elapsedMs) else 1
                    lastTapMark = TimeSource.Monotonic.markNow()
                    val amount = stepMs * skipMultiplier
                    currentOnSeek(if (offset.x < size.width / 2f) -amount else amount)
                },
            )
        }
}

internal fun nextSeekMultiplier(
    previousMultiplier: Int,
    elapsedMs: Long,
    thresholdMs: Long = 1_000L,
): Int = if (elapsedMs in 0 until thresholdMs) previousMultiplier + 1 else 1

internal data class SeekFeedback(
    val deltaMs: Long,
    val nonce: Int,
)

@Composable
internal fun SeekFeedbackOverlay(
    feedback: SeekFeedback?,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val spatialSpec =
        MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effectsSpec =
        MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val deltaMs = feedback?.deltaMs ?: 0L
    val forward = deltaMs >= 0L
    val label = stringResource(Res.string.seek_feedback_seconds, (abs(deltaMs) / 1000L).toInt())
    val directionLabel = stringResource(if (forward) Res.string.menu_seek_forward else Res.string.menu_seek_backward)
    val seekDescription = stringResource(Res.string.seek_feedback_description, directionLabel, label)
    val density = LocalDensity.current
    val nudgeDistance = with(density) { 12.dp.toPx() }
    val supportsBlur = supportsNativeBlur()
    val labelShadow =
        remember(density) {
            Shadow(
                color = Color.Black.copy(alpha = 0.55f),
                offset = Offset(0f, with(density) { 1.dp.toPx() }),
                blurRadius = with(density) { 6.dp.toPx() },
            )
        }

    val arrowNudge = remember { Animatable(0f) }
    LaunchedEffect(feedback?.nonce) {
        if (feedback == null) return@LaunchedEffect
        arrowNudge.snapTo(if (forward) nudgeDistance else -nudgeDistance)
        arrowNudge.animateTo(0f, animationSpec = spatialSpec)
    }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(initialScale = 0.7f, animationSpec = spatialSpec) + fadeIn(animationSpec = effectsSpec),
        exit = scaleOut(targetScale = 0.9f, animationSpec = spatialSpec) + fadeOut(animationSpec = effectsSpec),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .size(96.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = seekDescription
                        liveRegion = LiveRegionMode.Polite
                    },
        ) {
            Box(modifier = Modifier.size(32.dp)) {
                if (supportsBlur) {
                    Icon(
                        imageVector = MetrolistIcons.Outlined.FastForward,
                        contentDescription = null,
                        tint = Color.Black.copy(alpha = 0.45f),
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .offset(y = 1.dp)
                                .blur(6.dp, BlurredEdgeTreatment.Unbounded)
                                .graphicsLayer {
                                    if (!forward) scaleX = -1f
                                    translationX = arrowNudge.value
                                },
                    )
                }
                Icon(
                    imageVector = MetrolistIcons.Outlined.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (!forward) scaleX = -1f
                                translationX = arrowNudge.value
                            },
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, shadow = labelShadow),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    bottomSheetState: BottomSheetState,
    onClose: () -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToListenTogether: () -> Unit = {},
    onNavigateToIntegrations: () -> Unit = {},
    modifier: Modifier = Modifier,
    requestPlayPauseFocus: Boolean = false,
    onPlayPauseFocusRequested: () -> Unit = {},
    onLyricsVisibleChange: (Boolean) -> Unit = {},
    isScreenVisible: Boolean = true,
) {
    SonderPlayerScreen(
        viewModel, bottomSheetState, onClose, onNavigateToAlbum, onNavigateToArtist,
        onNavigateToListenTogether, onNavigateToIntegrations, modifier, requestPlayPauseFocus,
        onPlayPauseFocusRequested, onLyricsVisibleChange, isScreenVisible,
    )
}

@Composable
private fun FullscreenBackgroundBounds(
    bounds: State<FullscreenTransitionBounds?>,
    content: @Composable (FullscreenTransitionBounds?) -> Unit,
) {
    content(bounds.value)
}

private val MinPortraitPlayerHeight = 420.dp
