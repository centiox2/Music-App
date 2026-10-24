/*
 * Ported from Metrolist's ExperimentalLyrics.kt and LyricsCommon.kt
 * (https://github.com/metrolistgroup/metrolist, GPL-3.0). The positioning
 * engine is Metrolist's: every line is laid out relative to the current one,
 * held at 35% of the height, and eased there with a stagger by distance; a
 * drag or fling detaches the view until "Auto-scroll" brings it back.
 * Metrolist's Hilt view model, database and AI translation are replaced by
 * BitChord's lyrics and the Cover Glow look.
 */
package com.music.bitchord.ui.glow.lyrics

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.ui.glow.Glow
import com.music.bitchord.ui.glow.GlowType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val LYRICS_ANCHOR_RATIO = 0.35f
private val LYRICS_ITEM_FALLBACK_HEIGHT_DP = 68.dp
private val LYRICS_ITEM_GAP_DP = 16.dp
private val LYRICS_FADE_TOP_DP = 90.dp
private val LYRICS_FADE_BOTTOM_DP = 120.dp
private const val LYRICS_STAGGER_DELAY_PER_DISTANCE = 20
private const val LYRICS_STAGGER_DELAY_MAX_MS = 200
private const val MAX_SELECTION = 5

/**
 * The player's lyrics view.
 *
 * [positionProvider] is read every frame; [lyricsOffsetMs] shifts the lyrics
 * against the audio. [onSeek] receives the position a tapped line starts at.
 */
@Composable
fun GlowLyrics(
    lyrics: List<LyricLine>?,
    checked: Boolean,
    positionProvider: () -> Long,
    isPlayingProvider: () -> Boolean,
    lyricsOffsetMs: Long,
    onSeek: (Long) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
    translated: List<LyricLine>? = null,
    sourceLabel: String? = null,
    textSize: Float = 30f,
    emptyContent: @Composable () -> Unit = {},
) {
    val density = LocalDensity.current
    val accent = Glow.colors.text
    val scope = rememberCoroutineScope()

    val (lines, isSynced) = remember(lyrics, translated) { lyrics.orEmpty().toLyricsEntries(translated) }
    val mergedLyricsList = remember(lines) { buildMergedList(lines) }
    val hasWordTimings = remember(lines) { lines.any { it.words?.isNotEmpty() == true } }

    var activeLineIndices by remember { mutableStateOf(emptySet<Int>()) }
    var scrollTargetIndex by remember { mutableIntStateOf(-1) }
    var previousScrollActiveIndices by remember { mutableStateOf(emptySet<Int>()) }
    var currentPositionState by remember { mutableLongStateOf(0L) }
    var deferredCurrentLineIndex by remember { mutableIntStateOf(0) }
    var isSelectionModeActive by remember { mutableStateOf(false) }
    val selectedIndices = remember { mutableStateListOf<Int>() }
    var isAutoScrollEnabled by remember { mutableStateOf(true) }
    var lastMainMaxSeen by remember(lyrics) { mutableIntStateOf(-1) }

    BackHandler(enabled = isSelectionModeActive) {
        isSelectionModeActive = false
        selectedIndices.clear()
    }

    // The frame loop: interpolate the playhead between player readings and
    // decide which line the view should follow.
    LaunchedEffect(lines) {
        if (lines.isEmpty()) {
            activeLineIndices = emptySet()
            return@LaunchedEffect
        }
        var lastPlayerPos = positionProvider()
        var lastUpdateTime = System.currentTimeMillis()
        while (isActive) {
            // Paused, nothing moves: check a few times a second instead of every frame.
            if (isPlayingProvider()) withFrameNanos { } else delay(100)
            val now = System.currentTimeMillis()
            val playerPos = positionProvider()
            if (playerPos != lastPlayerPos) {
                // A jump of more than a second is a seek: follow it at once.
                if (abs(playerPos - (lastPlayerPos + (now - lastUpdateTime))) > 1500) scrollTargetIndex = -1
                lastPlayerPos = playerPos
                lastUpdateTime = now
            }
            val position = lastPlayerPos + if (isPlayingProvider()) now - lastUpdateTime else 0
            currentPositionState = position
            val effectivePosition = position + lyricsOffsetMs

            fun withLeads(raw: Set<Int>): Set<Int> {
                val out = raw.toMutableSet()
                for (i in raw) {
                    if (lines.getOrNull(i)?.isBackground == true) {
                        (i - 1 downTo 0).firstOrNull { lines.getOrNull(it)?.isBackground == false }?.let(out::add)
                    }
                }
                return out
            }
            val newActiveIndices = withLeads(findActiveLineIndices(lines, effectivePosition))
            val scrollActiveIndices = withLeads(findActiveLineIndices(lines, effectivePosition + if (hasWordTimings) 0L else 250L))

            val scrollMax = scrollActiveIndices.filter { lines.getOrNull(it)?.isBackground == false }.maxOrNull()
                ?: (scrollActiveIndices.maxOrNull() ?: -1)
            val isCurrentTargetStillActive = scrollTargetIndex in scrollActiveIndices
            val anyStillActive = scrollActiveIndices.isNotEmpty()
            val shouldScroll = when {
                !isCurrentTargetStillActive && anyStillActive && scrollMax > scrollTargetIndex -> true
                !isCurrentTargetStillActive && !anyStillActive && previousScrollActiveIndices.isNotEmpty() -> true
                scrollTargetIndex == -1 && anyStillActive -> true
                previousScrollActiveIndices.isEmpty() && anyStillActive && scrollMax > scrollTargetIndex -> true
                else -> false
            }
            if (shouldScroll) {
                val target = when {
                    !isCurrentTargetStillActive && !anyStillActive ->
                        (lastMainMaxSeen + 1 until lines.size).firstOrNull { lines.getOrNull(it)?.isBackground == false } ?: scrollTargetIndex
                    else -> scrollMax
                }
                if (target != -1 && (scrollTargetIndex == -1 || target > scrollTargetIndex)) scrollTargetIndex = target
            }
            if (scrollMax > lastMainMaxSeen && scrollMax != -1) lastMainMaxSeen = scrollMax
            previousScrollActiveIndices = scrollActiveIndices
            activeLineIndices = newActiveIndices
        }
    }

    LaunchedEffect(scrollTargetIndex, isAutoScrollEnabled) {
        if (scrollTargetIndex != -1 && isAutoScrollEnabled) deferredCurrentLineIndex = scrollTargetIndex
    }

    var userManualOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lyrics) {
        isAutoScrollEnabled = true
        userManualOffset = 0f
        scrollTargetIndex = -1
        deferredCurrentLineIndex = 0
        isSelectionModeActive = false
        selectedIndices.clear()
        previousScrollActiveIndices = emptySet()
    }

    var flingJob by remember { mutableStateOf<Job?>(null) }
    val velocityTracker = remember { VelocityTracker() }
    val decayAnimSpec = remember { exponentialDecay<Float>(frictionMultiplier = 1.8f) }
    val itemHeights = remember(mergedLyricsList) { mutableStateMapOf<Int, Int>() }
    var isInitialLayout by remember(mergedLyricsList) { mutableStateOf(true) }

    val activeListIndex by remember(mergedLyricsList) {
        derivedStateOf {
            mergedLyricsList.indexOfFirst {
                (it is LyricsListItem.Line && it.index == deferredCurrentLineIndex) ||
                    (it is LyricsListItem.Indicator && it.afterLineIndex == deferredCurrentLineIndex)
            }.coerceAtLeast(0)
        }
    }

    BoxWithConstraints(contentAlignment = Alignment.TopCenter, modifier = modifier.fillMaxSize()) {
        val maxHeightPx = constraints.maxHeight.toFloat()
        val anchorY = maxHeightPx * LYRICS_ANCHOR_RATIO
        val lineHeightPx = with(density) { LYRICS_ITEM_FALLBACK_HEIGHT_DP.toPx() }
        val indicatorHeightPx = with(density) { 72.dp.toPx() }
        val constraintLineHeightPx = with(density) { 120.dp.toPx() }
        val gapPx = with(density) { LYRICS_ITEM_GAP_DP.toPx() }

        fun heightOf(i: Int, fallback: Float): Float {
            val item = mergedLyricsList[i]
            return itemHeights[i]?.toFloat() ?: if (item is LyricsListItem.Indicator) indicatorHeightPx else fallback
        }
        fun noGap(item: LyricsListItem) = (item as? LyricsListItem.Line)?.entry?.isBackground == true || item is LyricsListItem.Indicator

        // Where every item sits relative to the current one.
        val positions = remember(itemHeights.toMap(), activeListIndex, mergedLyricsList) {
            val map = mutableMapOf<Int, Float>()
            if (mergedLyricsList.isEmpty()) return@remember map
            map[activeListIndex] = 0f
            var y = 0f
            for (i in activeListIndex - 1 downTo 0) {
                y -= heightOf(i, lineHeightPx) + if (noGap(mergedLyricsList[i])) 0f else gapPx
                map[i] = y
            }
            y = 0f
            for (i in activeListIndex until mergedLyricsList.size - 1) {
                y += heightOf(i, lineHeightPx) + if (noGap(mergedLyricsList[i + 1])) 0f else gapPx
                map[i + 1] = y
            }
            map
        }

        val minOffset = remember(itemHeights.toMap(), mergedLyricsList, activeListIndex, anchorY) {
            if (mergedLyricsList.isEmpty()) return@remember 0f
            val totalBelow = (activeListIndex until mergedLyricsList.size - 1).sumOf { i ->
                (heightOf(i, constraintLineHeightPx) + if (noGap(mergedLyricsList[i + 1])) 0f else gapPx).toDouble()
            }.toFloat()
            with(density) { 100.dp.toPx() } - anchorY - totalBelow - heightOf(mergedLyricsList.size - 1, constraintLineHeightPx)
        }
        val maxOffset = remember(itemHeights.toMap(), mergedLyricsList, activeListIndex, maxHeightPx, anchorY) {
            if (mergedLyricsList.isEmpty()) return@remember 0f
            val totalAbove = (0 until activeListIndex).sumOf { i ->
                (heightOf(i, constraintLineHeightPx) + if (noGap(mergedLyricsList[i])) 0f else gapPx).toDouble()
            }.toFloat()
            maxHeightPx - with(density) { 150.dp.toPx() } - anchorY + totalAbove
        }
        val scrollClampMin = minOf(minOffset, maxOffset)
        val scrollClampMax = maxOf(minOffset, maxOffset)

        LaunchedEffect(scrollClampMin, scrollClampMax) {
            userManualOffset = userManualOffset.coerceIn(scrollClampMin, scrollClampMax)
        }

        // Returning to auto-scroll glides back rather than jumping.
        LaunchedEffect(isAutoScrollEnabled, lines) {
            if (isAutoScrollEnabled) {
                val start = userManualOffset
                if (abs(start) < 1f) {
                    userManualOffset = 0f
                    return@LaunchedEffect
                }
                val anim = Animatable(start)
                var lastValue = start
                anim.animateTo(0f, tween((abs(start) / 4f).toInt().coerceIn(200, 600), easing = FastOutSlowInEasing)) {
                    userManualOffset += value - lastValue
                    lastValue = value
                }
                userManualOffset = 0f
            }
        }

        // Snap, rather than animate, until the lines around the current one are measured.
        LaunchedEffect(mergedLyricsList.size) {
            if (mergedLyricsList.isNotEmpty()) {
                isInitialLayout = true
                snapshotFlow {
                    val h = itemHeights.toMap()
                    val from = (activeListIndex - 8).coerceAtLeast(0)
                    val to = (activeListIndex + 12).coerceAtMost(mergedLyricsList.size - 1)
                    (from..to).all { h.containsKey(it) }
                }.first { it }
                isInitialLayout = false
            }
        }
        LaunchedEffect(activeListIndex) {
            if (mergedLyricsList.isNotEmpty()) {
                val from = (activeListIndex - 3).coerceAtLeast(0)
                val to = (activeListIndex + 3).coerceAtMost(mergedLyricsList.size - 1)
                if ((from..to).any { !itemHeights.containsKey(it) }) {
                    isInitialLayout = true
                    snapshotFlow { (from..to).all { itemHeights.containsKey(it) } }.first { it }
                    isInitialLayout = false
                }
            }
        }

        val resync by rememberUpdatedState {
            flingJob?.cancel()
            var target = scrollTargetIndex
            if (target == -1) target = findActiveLineIndices(lines, currentPositionState + lyricsOffsetMs).maxOrNull() ?: -1
            if (target != -1) {
                val listIdx = mergedLyricsList.indexOfFirst { it is LyricsListItem.Line && it.index == target }.coerceAtLeast(0)
                userManualOffset += positions[listIdx] ?: 0f
                deferredCurrentLineIndex = target
                scrollTargetIndex = target
            }
            isAutoScrollEnabled = true
        }

        when {
            lines.isEmpty() && checked -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { emptyContent() }
            lines.isEmpty() -> LyricsPlaceholder()
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdge(top = LYRICS_FADE_TOP_DP, bottom = LYRICS_FADE_BOTTOM_DP)
                    .clipToBounds()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (isInitialLayout) continue
                                flingJob?.cancel()
                                velocityTracker.resetTracking()
                                velocityTracker.addPosition(down.uptimeMillis, down.position)
                                var dragged = false
                                verticalDrag(down.id) { change ->
                                    if (!dragged) {
                                        dragged = true
                                        isAutoScrollEnabled = false
                                    }
                                    userManualOffset = (userManualOffset + change.positionChange().y).coerceIn(scrollClampMin, scrollClampMax)
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    change.consume()
                                }
                                if (!dragged) continue
                                val velocity = velocityTracker.calculateVelocity().y
                                flingJob = scope.launch {
                                    AnimationState(initialValue = userManualOffset, initialVelocity = velocity).animateDecay(decayAnimSpec) {
                                        val clamped = value.coerceIn(scrollClampMin, scrollClampMax)
                                        userManualOffset = clamped
                                        if (value != clamped) cancelAnimation()
                                    }
                                }
                            }
                        }
                    },
            ) {
                val currentEffectivePosition = currentPositionState + lyricsOffsetMs

                if (sourceLabel != null && isSynced && !isSelectionModeActive) {
                    val targetProviderBase = anchorY + (positions[0] ?: 0f) - with(density) { 32.dp.toPx() }
                    val animatedProviderBase by animateFloatAsState(
                        targetValue = targetProviderBase,
                        animationSpec = if (isInitialLayout || !isAutoScrollEnabled) snap() else tween(750, 0, FastOutSlowInEasing),
                        label = "lyricsProviderOffset",
                    )
                    Text(
                        text = "Lyrics from $sourceLabel",
                        style = GlowType.caption.copy(fontSize = 12.sp),
                        color = accent.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(0, (animatedProviderBase + userManualOffset).roundToInt()) }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }

                mergedLyricsList.forEachIndexed { listIndex, listItem ->
                    key(listItem) {
                        val distance = abs(listIndex - activeListIndex)
                        val targetOffset = anchorY + positions.getOrDefault(listIndex, (listIndex - activeListIndex) * lineHeightPx)
                        val frozenOffset = remember { mutableFloatStateOf(targetOffset) }
                        LaunchedEffect(isAutoScrollEnabled, targetOffset, isInitialLayout) {
                            if (isAutoScrollEnabled || isInitialLayout) frozenOffset.floatValue = targetOffset
                        }
                        val animatedOffset by animateFloatAsState(
                            targetValue = if (isAutoScrollEnabled) targetOffset else frozenOffset.floatValue,
                            animationSpec = if (isInitialLayout || !isAutoScrollEnabled) {
                                snap()
                            } else {
                                tween(750, (distance * LYRICS_STAGGER_DELAY_PER_DISTANCE).coerceAtMost(LYRICS_STAGGER_DELAY_MAX_MS), FastOutSlowInEasing)
                            },
                            label = "lyricStaggeredOffset_$listIndex",
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .layout { m, c ->
                                    val p = m.measure(c.copy(maxHeight = Constraints.Infinity))
                                    layout(p.width, 0) { p.place(0, 0) }
                                }
                                .offset { IntOffset(0, (animatedOffset + userManualOffset).roundToInt()) },
                        ) {
                            when (listItem) {
                                is LyricsListItem.Indicator -> {
                                    val visible = isAutoScrollEnabled &&
                                        currentEffectivePosition >= listItem.gapStartMs &&
                                        currentEffectivePosition <= listItem.gapEndMs - 650L
                                    IntervalIndicator(
                                        listItem.gapStartMs,
                                        listItem.gapEndMs - 650L,
                                        currentEffectivePosition,
                                        visible,
                                        accent,
                                        Modifier.fillMaxWidth().onSizeChanged { itemHeights[listIndex] = it.height },
                                        alignEnd = listItem.nextAgent == "v2",
                                    )
                                }
                                is LyricsListItem.Line -> {
                                    val index = listItem.index
                                    val item = listItem.entry
                                    val isActiveLine = index in activeLineIndices
                                    val pairedMainLineIndex = if (item.isBackground) {
                                        (index - 1 downTo 0).firstOrNull { lines.getOrNull(it)?.isBackground == false } ?: -1
                                    } else {
                                        -1
                                    }
                                    val isInGapWithMain = item.isBackground && pairedMainLineIndex != -1 &&
                                        currentEffectivePosition >= lines[pairedMainLineIndex].time && currentEffectivePosition <= item.time
                                    val bgVisible = item.isBackground &&
                                        (pairedMainLineIndex in activeLineIndices || index in activeLineIndices || isInGapWithMain)

                                    LyricsLine(
                                        index = index,
                                        item = item,
                                        isSynced = isSynced,
                                        isActiveLine = isActiveLine,
                                        bgVisible = bgVisible,
                                        isSelected = index in selectedIndices,
                                        isSelectionModeActive = isSelectionModeActive,
                                        currentPositionState = currentPositionState,
                                        lyricsOffset = lyricsOffsetMs,
                                        positionProvider = positionProvider,
                                        isPlayingProvider = isPlayingProvider,
                                        lyricsTextSize = textSize,
                                        lyricsLineSpacing = 1.25f,
                                        accent = accent,
                                        isAutoScrollEnabled = isAutoScrollEnabled,
                                        displayedCurrentLineIndex = deferredCurrentLineIndex,
                                        onSizeChanged = { itemHeights[listIndex] = it },
                                        onClick = {
                                            if (isSelectionModeActive) {
                                                if (index in selectedIndices) {
                                                    selectedIndices.remove(index)
                                                    if (selectedIndices.isEmpty()) isSelectionModeActive = false
                                                } else if (selectedIndices.size < MAX_SELECTION) {
                                                    selectedIndices.add(index)
                                                }
                                            } else if (isSynced) {
                                                onSeek((item.time - lyricsOffsetMs).coerceAtLeast(0))
                                                scrollTargetIndex = -1
                                                isAutoScrollEnabled = true
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionModeActive) {
                                                isSelectionModeActive = true
                                                selectedIndices.add(index)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        LyricsActionOverlay(
            modifier = Modifier.align(Alignment.BottomCenter),
            isAutoScrollEnabled = isAutoScrollEnabled,
            isSynced = isSynced,
            isSelectionModeActive = isSelectionModeActive,
            anySelected = selectedIndices.isNotEmpty(),
            onSyncClick = resync,
            onCancelSelection = {
                isSelectionModeActive = false
                selectedIndices.clear()
            },
            onShareSelection = {
                val text = selectedIndices.sorted().mapNotNull { lines.getOrNull(it)?.text }.joinToString("\n")
                if (text.isNotBlank()) onShare(text)
                isSelectionModeActive = false
                selectedIndices.clear()
            },
        )
    }
}

/** A progress ring that grows in while an instrumental gap plays. */
@Composable
private fun IntervalIndicator(
    gapStartMs: Long,
    gapEndMs: Long,
    currentPositionMs: Long,
    visible: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    val alpha = remember { Animatable(0f) }
    val rowHeight = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            rowHeight.animateTo(1f, tween(200))
            alpha.animateTo(1f, tween(200))
        } else {
            alpha.animateTo(0f, tween(200))
            rowHeight.animateTo(0f, tween(200))
        }
    }
    val progress = if (gapEndMs > gapStartMs) {
        ((currentPositionMs - gapStartMs).toFloat() / (gapEndMs - gapStartMs)).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(progress, tween(100, easing = LinearEasing), label = "intervalProgress")
    Box(
        modifier = modifier
            .height(72.dp * rowHeight.value)
            .padding(top = 16.dp * rowHeight.value)
            .graphicsLayer {
                this.alpha = alpha.value
                clip = true
            },
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Canvas(Modifier.padding(horizontal = 6.dp).size(32.dp)) {
            val stroke = 4.dp.toPx()
            drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, style = Stroke(stroke))
            drawArc(color, -90f, 360f * animatedProgress, false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun LyricsActionOverlay(
    isAutoScrollEnabled: Boolean,
    isSynced: Boolean,
    isSelectionModeActive: Boolean,
    anySelected: Boolean,
    onSyncClick: () -> Unit,
    onCancelSelection: () -> Unit,
    onShareSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(bottom = 12.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = !isAutoScrollEnabled && isSynced && !isSelectionModeActive,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            OverlayPill(Icons.Rounded.Sync, "Auto-scroll", onClick = onSyncClick)
        }
        AnimatedVisibility(
            visible = isSelectionModeActive,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OverlayPill(Icons.Rounded.Close, null, onClick = onCancelSelection)
                OverlayPill(Icons.Rounded.IosShare, "Share", enabled = anySelected, onClick = onShareSelection)
            }
        }
    }
}

@Composable
private fun OverlayPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = Glow.colors
    Row(
        Modifier
            .height(42.dp)
            .clip(CircleShape)
            .background(c.primary.copy(alpha = if (enabled) 1f else 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (label == null) 11.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label ?: "Cancel", tint = c.onPrimary, modifier = Modifier.size(20.dp))
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(label, style = GlowType.label.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = c.onPrimary)
        }
    }
}

@Composable
private fun LyricsPlaceholder() {
    val c = Glow.colors
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().padding(top = 60.dp, start = 4.dp, end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        listOf(0.9f, 0.7f, 0.82f, 0.6f, 0.75f).forEach { w ->
            com.music.bitchord.ui.glow.SkeletonBox(Modifier.fillMaxWidth(w).height(26.dp), radius = 8.dp)
        }
    }
}
