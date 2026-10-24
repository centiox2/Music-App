/*
 * Ported from Metrolist's LyricsLine.kt (https://github.com/metrolistgroup/metrolist,
 * GPL-3.0). The word animation — per-glyph gradient fill, start-of-word
 * wobble, long-note glow, hyphen crescendo wave — is Metrolist's; it reads
 * the playhead from a provider instead of Metrolist's PlayerConnection, and
 * drops romanization and AI translation.
 */
package com.music.bitchord.ui.glow.lyrics

import android.graphics.BlurMaskFilter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.music.bitchord.R
import com.music.bitchord.ui.glow.Inter
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

private data class HyphenGroupWord(
    val pos: Int,
    val size: Int,
    val isLast: Boolean,
    val groupStartMs: Long,
    val groupEndMs: Long,
)

private fun String.containsRtl(): Boolean = any {
    val d = Character.getDirectionality(it)
    d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
}

// Indic scripts join glyphs across grapheme clusters, so they're drawn whole.
private fun String.containsJoinedIndic(): Boolean = any { it in 'ऀ'..'෿' }

private fun String.toGraphemeClusters(): List<String> {
    if (isEmpty()) return emptyList()
    val result = mutableListOf<String>()
    val it = java.text.BreakIterator.getCharacterInstance()
    it.setText(this)
    var start = it.first()
    var end = it.next()
    while (end != java.text.BreakIterator.DONE) {
        result.add(substring(start, end))
        start = end
        end = it.next()
    }
    return result
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LyricsLine(
    index: Int,
    item: LyricsEntry,
    isSynced: Boolean,
    isActiveLine: Boolean,
    bgVisible: Boolean,
    isSelected: Boolean,
    isSelectionModeActive: Boolean,
    currentPositionState: Long,
    lyricsOffset: Long,
    positionProvider: () -> Long,
    isPlayingProvider: () -> Boolean,
    lyricsTextSize: Float,
    lyricsLineSpacing: Float,
    accent: Color,
    isAutoScrollEnabled: Boolean,
    displayedCurrentLineIndex: Int,
    onSizeChanged: (Int) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemModifier = modifier
        .fillMaxWidth()
        .onSizeChanged { onSizeChanged(it.height) }
        .clip(RoundedCornerShape(8.dp))
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .background(if (isSelected && isSelectionModeActive) accent.copy(alpha = 0.18f) else Color.Transparent)
        .padding(
            start = 4.dp,
            end = 4.dp,
            top = if (item.isBackground) 0.dp else 10.dp,
            bottom = if (item.isBackground) 2.dp else 10.dp,
        )

    // The design is left-aligned; the second voice of a duet answers from the right.
    val isSecondVoice = item.agent == "v2"
    val agentAlignment = when {
        isSecondVoice -> Alignment.End
        else -> Alignment.Start
    }
    val agentTextAlign = if (isSecondVoice) TextAlign.Right else TextAlign.Left

    Box(modifier = itemModifier, contentAlignment = if (isSecondVoice) Alignment.CenterEnd else Alignment.CenterStart) {
        @Composable
        fun LyricContent() {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = agentAlignment) {
                val inactiveAlpha = if (item.isBackground) 0.08f else 0.2f
                val focusedAlpha = if (item.isBackground) 0.5f else 0.3f
                val targetAlpha = if (!isSynced || item.isBackground || isActiveLine) {
                    1f
                } else if (isAutoScrollEnabled && displayedCurrentLineIndex >= 0) {
                    when (abs(index - displayedCurrentLineIndex)) {
                        0 -> focusedAlpha
                        1, 2 -> 0.2f
                        3 -> 0.15f
                        4 -> 0.1f
                        else -> 0.08f
                    }
                } else {
                    inactiveAlpha
                }
                val animatedAlpha by animateFloatAsState(targetAlpha, tween(250), label = "lyricsLineAlpha")
                val lineColor = accent.copy(alpha = if (item.isBackground) focusedAlpha else animatedAlpha)

                val mainText = if (item.isBackground) item.text.removePrefix("(").removeSuffix(")") else item.text

                val lyricStyle = TextStyle(
                    fontSize = if (item.isBackground) (lyricsTextSize * 0.7f).sp else lyricsTextSize.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = if (item.isBackground) FontStyle.Italic else FontStyle.Normal,
                    lineHeight = if (item.isBackground) (lyricsTextSize * 0.7f * lyricsLineSpacing).sp else (lyricsTextSize * lyricsLineSpacing).sp,
                    letterSpacing = (-0.5).sp,
                    textAlign = agentTextAlign,
                    fontFamily = Inter,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both),
                )

                // Lines without word timing get a quick ripple across their words as they start.
                val effectiveWords = if (item.words?.isNotEmpty() == true) {
                    item.words
                } else {
                    remember(mainText, item.time) {
                        val words = mainText.split(Regex("\\s+")).filter { it.isNotBlank() }
                        val startTimeSec = item.time / 1000.0
                        words.mapIndexed { idx, wordText ->
                            WordTimestamp(
                                text = wordText,
                                startTime = startTimeSec + idx * 0.03,
                                endTime = startTimeSec + idx * 0.03 + 0.18,
                                hasTrailingSpace = idx < words.size - 1,
                            )
                        }
                    }
                }

                if (isSynced && (isActiveLine || abs(index - displayedCurrentLineIndex) <= 3)) {
                    WordLevelLyrics(
                        mainText = mainText,
                        words = effectiveWords,
                        isActiveLine = isActiveLine,
                        currentPositionState = currentPositionState,
                        lyricsOffset = lyricsOffset,
                        positionProvider = positionProvider,
                        isPlayingProvider = isPlayingProvider,
                        lyricStyle = lyricStyle,
                        lineColor = lineColor,
                        accent = accent,
                        isBackground = item.isBackground,
                        focusedAlpha = focusedAlpha,
                        alignment = agentTextAlign,
                    )
                } else {
                    Text(
                        text = mainText,
                        style = lyricStyle.copy(color = if (isActiveLine || !isSynced) accent else lineColor),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item.translation?.let { translation ->
                    val translationAlpha by animateFloatAsState(
                        when {
                            !isSynced || isActiveLine -> 0.7f
                            item.isBackground -> focusedAlpha * 0.8f
                            else -> (animatedAlpha * 1.4f).coerceAtMost(0.5f)
                        },
                        tween(250),
                        label = "translationAlpha",
                    )
                    val translationSize = lyricsTextSize * if (item.isBackground) 0.45f else 0.56f
                    Text(
                        text = translation,
                        style = lyricStyle.copy(
                            fontSize = translationSize.sp,
                            lineHeight = (translationSize * 1.3f).sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.sp,
                            color = accent.copy(alpha = translationAlpha),
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
        }

        if (item.isBackground) {
            AnimatedVisibility(
                visible = bgVisible,
                enter = fadeIn(tween(durationMillis = 250, delayMillis = 100)),
                exit = fadeOut(tween(250)),
            ) { LyricContent() }
        } else {
            LyricContent()
        }
    }
}

@Composable
private fun WordLevelLyrics(
    mainText: String,
    words: List<WordTimestamp>,
    isActiveLine: Boolean,
    currentPositionState: Long,
    lyricsOffset: Long,
    positionProvider: () -> Long,
    isPlayingProvider: () -> Boolean,
    lyricStyle: TextStyle,
    lineColor: Color,
    accent: Color,
    isBackground: Boolean,
    focusedAlpha: Float,
    alignment: TextAlign,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    val glowPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = runCatching { ResourcesCompat.getFont(context, R.font.inter_bold) }.getOrNull()
                ?: android.graphics.Typeface.DEFAULT_BOLD
        }
    }

    // The active line runs its own frame clock between player readings.
    var smoothPosition by remember { mutableLongStateOf(currentPositionState + lyricsOffset) }
    LaunchedEffect(isActiveLine) {
        if (isActiveLine) {
            var lastPlayerPos = positionProvider()
            var lastUpdateTime = System.currentTimeMillis()
            while (isActive) {
                withFrameMillis {
                    val now = System.currentTimeMillis()
                    val playerPos = positionProvider()
                    if (playerPos != lastPlayerPos) {
                        lastPlayerPos = playerPos
                        lastUpdateTime = now
                    }
                    val elapsed = now - lastUpdateTime
                    smoothPosition = lastPlayerPos + lyricsOffset + (if (isPlayingProvider()) elapsed else 0)
                }
            }
        }
    }
    LaunchedEffect(isActiveLine, currentPositionState) {
        if (!isActiveLine) smoothPosition = currentPositionState + lyricsOffset
    }

    // Hyphenated words ("la-la-la") are split so each part can be sung in turn.
    val (effectiveWords, effectiveToOriginalIdx) = remember(words, isBackground) {
        words.flatMapIndexed { originalIdx, word ->
            val shouldSplit = word.text.contains('-') && word.text.length > 1 && (!word.hasTrailingSpace || words.size == 1)
            if (shouldSplit) {
                val segments = mutableListOf<String>()
                var start = 0
                for (i in word.text.indices) {
                    if (word.text[i] == '-') {
                        segments.add(word.text.substring(start, i + 1))
                        start = i + 1
                    }
                }
                if (start < word.text.length) segments.add(word.text.substring(start))
                if (segments.size > 1) {
                    val segmentDuration = (word.endTime - word.startTime) / segments.size
                    segments.mapIndexed { index, segmentText ->
                        WordTimestamp(
                            text = segmentText,
                            startTime = word.startTime + index * segmentDuration,
                            endTime = word.startTime + (index + 1) * segmentDuration,
                            hasTrailingSpace = if (index == segments.size - 1) word.hasTrailingSpace else false,
                        ) to originalIdx
                    }
                } else {
                    listOf(word to originalIdx)
                }
            } else {
                listOf(word to originalIdx)
            }
        }.let { data -> data.map { it.first } to data.map { it.second } }
    }

    val graphemeClusters = remember(mainText) { mainText.toGraphemeClusters() }
    val clusterCount = graphemeClusters.size
    val clusterCharOffsets = remember(mainText) {
        IntArray(clusterCount).also { offsets ->
            var charOffset = 0
            graphemeClusters.forEachIndexed { i, cluster ->
                offsets[i] = charOffset
                charOffset += cluster.length
            }
        }
    }

    // Which word, and which position in it, each glyph belongs to.
    val charToWordData = remember(mainText, effectiveWords, isBackground, graphemeClusters, clusterCharOffsets) {
        val matchText = mainText.normalizedForMatch()
        val wordIdxMap = IntArray(clusterCount) { -1 }
        val charInWordMap = IntArray(clusterCount)
        val wordLenMap = IntArray(clusterCount) { 1 }
        var currentPos = 0
        var clCursor = 0
        effectiveWords.forEachIndexed { wordIdx, word ->
            val rawWordText = if (isBackground) {
                var t = word.text
                if (wordIdx == 0) t = t.removePrefix("(")
                if (wordIdx == effectiveWords.size - 1) t = t.removeSuffix(")")
                t
            } else {
                word.text
            }
            // Providers send words with their own spacing and typography ("it’s" against "it's"),
            // and an exact search missed those, leaving the word dark while it was sung.
            val needle = rawWordText.trim()
            val indexInMain = if (needle.isEmpty()) -1 else matchText.indexOf(needle.normalizedForMatch(), currentPos, ignoreCase = true)
            if (indexInMain != -1) {
                val wordEndInMain = indexInMain + needle.length
                while (clCursor < clusterCount && clusterCharOffsets[clCursor] < indexInMain) clCursor++
                val wordClusterIndices = mutableListOf<Int>()
                while (clCursor < clusterCount && clusterCharOffsets[clCursor] < wordEndInMain) {
                    wordClusterIndices.add(clCursor)
                    clCursor++
                }
                val wordClusterLen = wordClusterIndices.size
                wordClusterIndices.forEachIndexed { posInWord, clIdx ->
                    wordIdxMap[clIdx] = wordIdx
                    charInWordMap[clIdx] = posInWord
                    wordLenMap[clIdx] = wordClusterLen
                }
                if (clCursor < clusterCount && clusterCharOffsets[clCursor] == wordEndInMain &&
                    wordEndInMain < mainText.length && mainText[wordEndInMain] == ' '
                ) {
                    wordIdxMap[clCursor] = wordIdx
                    charInWordMap[clCursor] = wordClusterLen
                    wordLenMap[clCursor] = wordClusterLen + 1
                    clCursor++
                }
                currentPos = wordEndInMain
            }
        }
        // Punctuation and anything else no word claimed ("Despacito," with the comma outside the
        // word) used to stay dark after the line was sung. It goes with the word before it.
        var lastWord = -1
        var lastLen = 1
        for (i in 0 until clusterCount) {
            if (wordIdxMap[i] != -1) {
                lastWord = wordIdxMap[i]
                lastLen = wordLenMap[i]
            } else if (lastWord != -1) {
                wordIdxMap[i] = lastWord
                charInWordMap[i] = (lastLen - 1).coerceAtLeast(0)
                wordLenMap[i] = lastLen
            }
        }
        // Leading characters before the first word belong to that word.
        val firstWord = wordIdxMap.firstOrNull { it != -1 } ?: -1
        if (firstWord != -1) {
            for (i in 0 until clusterCount) {
                if (wordIdxMap[i] != -1) break
                wordIdxMap[i] = firstWord
                charInWordMap[i] = 0
            }
        }
        Triple(wordIdxMap, charInWordMap, wordLenMap)
    }

    val hyphenGroupData = remember(effectiveWords) {
        val map = mutableMapOf<Int, HyphenGroupWord>()
        var currentGroup = mutableListOf<Int>()
        effectiveWords.forEachIndexed { wordIdx, word ->
            currentGroup.add(wordIdx)
            if (!word.text.endsWith("-")) {
                if (currentGroup.size > 1) {
                    val groupStartMs = (effectiveWords[currentGroup.first()].startTime * 1000).toLong()
                    val groupEndMs = (word.endTime * 1000).toLong()
                    currentGroup.forEachIndexed { pos, idx ->
                        map[idx] = HyphenGroupWord(pos, currentGroup.size, pos == currentGroup.size - 1, groupStartMs, groupEndMs)
                    }
                }
                currentGroup = mutableListOf()
            }
        }
        map
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth
        val layoutResult = remember(mainText, maxWidthPx, lyricStyle) {
            textMeasurer.measure(
                text = mainText,
                style = lyricStyle,
                constraints = Constraints(minWidth = maxWidthPx, maxWidth = maxWidthPx),
                softWrap = true,
            )
        }
        val letterLayouts = remember(mainText, lyricStyle) {
            graphemeClusters.map { cluster -> textMeasurer.measure(cluster, lyricStyle) }
        }
        val drawWholeLine = remember(mainText) { mainText.containsRtl() || mainText.containsJoinedIndic() }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { layoutResult.size.height.toDp() })
                .graphicsLayer(clip = false, compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            if (mainText.isEmpty()) return@Canvas
            if (!isActiveLine) {
                drawText(layoutResult, color = lineColor)
                return@Canvas
            }
            if (drawWholeLine) {
                val (wordIdxMap, _, _) = charToWordData
                drawText(layoutResult, color = lineColor.copy(alpha = focusedAlpha))
                effectiveWords.forEachIndexed { wIdx, word ->
                    val wStartMs = (word.startTime * 1000).toLong()
                    val wEndMs = (word.endTime * 1000).toLong()
                    val isWordSung = smoothPosition > wEndMs
                    val isWordActive = smoothPosition in wStartMs..wEndMs
                    val sungFactor = when {
                        isWordSung -> 1f
                        isWordActive -> ((smoothPosition - wStartMs).toFloat() / (wEndMs - wStartMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    var left = Float.MAX_VALUE
                    var right = Float.MIN_VALUE
                    var top = Float.MAX_VALUE
                    var bottom = Float.MIN_VALUE
                    var found = false
                    for (i in 0 until clusterCount) {
                        if (wordIdxMap[i] == wIdx) {
                            val bounds = layoutResult.getBoundingBox(clusterCharOffsets[i])
                            left = minOf(left, bounds.left)
                            right = maxOf(right, bounds.right)
                            top = minOf(top, bounds.top)
                            bottom = maxOf(bottom, bounds.bottom)
                            found = true
                        }
                    }
                    if (found && sungFactor > 0f) {
                        clipRect(left = left, top = top, right = right, bottom = bottom) {
                            drawText(
                                layoutResult,
                                color = if (isWordSung) accent else accent.copy(alpha = focusedAlpha + (1f - focusedAlpha) * sungFactor),
                            )
                        }
                    }
                }
                return@Canvas
            }

            val (wordIdxMap, charInWordMap, wordLenMap) = charToWordData
            val wordFactors = effectiveWords.map { word ->
                val wStartMs = (word.startTime * 1000).toLong()
                val wEndMs = (word.endTime * 1000).toLong()
                val isWordSung = smoothPosition > wEndMs
                val isWordActive = smoothPosition in wStartMs..wEndMs
                val sungFactor = when {
                    isWordSung -> 1f
                    isWordActive -> ((smoothPosition - wStartMs).toFloat() / (wEndMs - wStartMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                    else -> 0f
                }
                Triple(sungFactor, word, isWordSung)
            }

            // A short swell as each word starts.
            val wordWobbles = FloatArray(words.size)
            words.forEachIndexed { wordIdx, word ->
                val timeSinceStart = (smoothPosition - (word.startTime * 1000).toLong()).toFloat()
                wordWobbles[wordIdx] = if (timeSinceStart in 0f..750f) {
                    if (timeSinceStart < 125f) timeSinceStart / 125f else (1f - (timeSinceStart - 125f) / 625f).coerceAtLeast(0f)
                } else {
                    0f
                }
            }

            fun charProgress(i: Int, wordItem: WordTimestamp?): Float = if (wordItem != null) {
                val sMs = wordItem.startTime * 1000
                val dur = (wordItem.endTime * 1000 - sMs).coerceAtLeast(100.0)
                val wProg = (smoothPosition.toDouble() - sMs) / dur
                val cInW = charInWordMap[i].toDouble()
                val wLen = wordLenMap[i].toDouble()
                ((wProg - cInW / wLen) * wLen).coerceIn(0.0, 1.0).toFloat()
            } else {
                0f
            }

            val lineCurrentPushes = FloatArray(layoutResult.lineCount)
            val lineTotalPushes = FloatArray(layoutResult.lineCount)

            // How much each line grows, so centred and right-aligned lines stay put.
            for (i in 0 until clusterCount) {
                val charOffset = clusterCharOffsets[i]
                val lineIdx = layoutResult.getLineForOffset(charOffset)
                val wordIdx = wordIdxMap[i]
                val originalWordIdx = if (wordIdx != -1) effectiveToOriginalIdx[wordIdx] else -1
                val (sungFactor, wordItem, isWordSung) = if (wordIdx != -1) wordFactors[wordIdx] else Triple(0f, null, false)
                val wobble = if (originalWordIdx != -1) wordWobbles[originalWordIdx] else 0f
                var crescendoDeltaX = 0f
                val groupWord = if (wordIdx != -1) hyphenGroupData[wordIdx] else null
                if (groupWord != null) {
                    val p = sungFactor
                    val pOut = ((smoothPosition - groupWord.groupEndMs).toFloat() / 600f).coerceIn(0f, 1f)
                    val peakScale = 0.06f
                    val decay = 2.5f
                    val freq = 10.0f
                    val baseScalePerSegment = 0.012f
                    crescendoDeltaX = when {
                        pOut > 0f -> (groupWord.pos * baseScalePerSegment + peakScale) * exp(-decay * pOut) * cos(freq * pOut * PI.toFloat()) * (1f - pOut)
                        groupWord.isLast -> groupWord.pos * baseScalePerSegment + peakScale * (1f - exp(-decay * p) * cos(freq * p * PI.toFloat()) * (1f - p))
                        else -> groupWord.pos * baseScalePerSegment + if (p > 0f) 0.02f * (1f - p) else 0f
                    }
                }
                val charLp = charProgress(i, wordItem)
                val nudgeScale = if (wordItem != null && !isWordSung && sungFactor > 0f) 0.038f * sin(charLp * PI.toFloat()) * exp(-3f * charLp) else 0f
                val charScaleX = 1f + wobble * 0.025f + crescendoDeltaX + nudgeScale * 0.3f
                lineTotalPushes[lineIdx] += layoutResult.getBoundingBox(charOffset).width * (charScaleX - 1f)
            }

            for (i in 0 until clusterCount) {
                val charOffset = clusterCharOffsets[i]
                val lineIdx = layoutResult.getLineForOffset(charOffset)
                val charBounds = layoutResult.getBoundingBox(charOffset)
                val wordIdx = wordIdxMap[i]
                val originalWordIdx = if (wordIdx != -1) effectiveToOriginalIdx[wordIdx] else -1
                val alignShift = when (alignment) {
                    TextAlign.Center -> -lineTotalPushes[lineIdx] / 2f
                    TextAlign.Right -> -lineTotalPushes[lineIdx]
                    else -> 0f
                }
                val (sungFactor, wordItem, isWordSung) = if (wordIdx != -1) wordFactors[wordIdx] else Triple(0f, null, false)
                val wobble = if (originalWordIdx != -1) wordWobbles[originalWordIdx] else 0f
                val charLp = charProgress(i, wordItem)
                val shouldGlow = wordItem != null && !isWordSung && sungFactor > 0.001f

                var crescendoDeltaX = 0f
                var crescendoDeltaY = 0f
                val groupWord = if (wordIdx != -1) hyphenGroupData[wordIdx] else null
                if (groupWord != null) {
                    val p = sungFactor
                    val pOut = ((smoothPosition - groupWord.groupEndMs).toFloat() / 600f).coerceIn(0f, 1f)
                    val peakScale = 0.06f
                    val decay = 3.5f
                    val freq = 5.0f
                    val baseScalePerSegment = 0.012f
                    val v = when {
                        pOut > 0f -> (groupWord.pos * baseScalePerSegment + peakScale) * exp(-decay * pOut) * cos(freq * pOut * PI.toFloat()) * (1f - pOut)
                        groupWord.isLast -> groupWord.pos * baseScalePerSegment + peakScale * (1f - exp(-decay * p) * cos(freq * p * PI.toFloat()) * (1f - p))
                        else -> groupWord.pos * baseScalePerSegment + if (p > 0f) 0.02f * (1f - p) else 0f
                    }
                    crescendoDeltaX = v
                    crescendoDeltaY = v
                }
                val nudgeScale = if (wordItem != null && !isWordSung && sungFactor > 0f) 0.038f * sin(charLp * PI.toFloat()) * exp(-3f * charLp) else 0f
                val charScaleX = 1f + wobble * 0.025f + crescendoDeltaX + nudgeScale * 0.3f
                val charScaleY = 1f + wobble * 0.015f + crescendoDeltaY + nudgeScale

                withTransform({
                    var waveOffset = 0f
                    if (groupWord != null) {
                        val timeInGroup = (smoothPosition - groupWord.groupStartMs).toFloat()
                        val timeToGroupEnd = (groupWord.groupEndMs - smoothPosition).toFloat()
                        val waveFade = (timeInGroup / 200f).coerceIn(0f, 1f) * (timeToGroupEnd / 200f).coerceIn(0f, 1f)
                        if (waveFade > 0.01f) {
                            waveOffset = sin(System.currentTimeMillis() * 0.006f + i * 0.4f) * 3.24f * waveFade
                        }
                    }
                    translate(left = alignShift + lineCurrentPushes[lineIdx] + charBounds.left, top = charBounds.top + waveOffset)
                    if (wordIdx != -1) scale(charScaleX, charScaleY, pivot = Offset(charBounds.width / 2f, charBounds.height))
                }) {
                    if (shouldGlow) {
                        val sMs = wordItem!!.startTime * 1000
                        val dur = wordItem.endTime * 1000 - sMs
                        val impactRatio = dur.toFloat() / wordItem.text.length.coerceAtLeast(1)
                        val fadeFactor = (sungFactor * 5f).coerceIn(0f, 1f) * ((1f - sungFactor) * 8f).coerceIn(0f, 1f)
                        val impactFactor = (
                            ((impactRatio - 100f) / 250f).coerceIn(0f, 1f) * 0.6f +
                                ((dur.toFloat() - 300f) / 1500f).coerceIn(0f, 1f) * 0.4f
                            ).coerceIn(0f, 1f) * fadeFactor
                        if (impactFactor > 0.01f) {
                            drawIntoCanvas { canvas ->
                                glowPaint.maskFilter = BlurMaskFilter(12.dp.toPx() * impactFactor, BlurMaskFilter.Blur.NORMAL)
                                glowPaint.color = accent.copy(alpha = (0.35f * impactFactor).coerceIn(0f, 0.4f)).toArgb()
                                glowPaint.textSize = lyricStyle.fontSize.toPx()
                                canvas.nativeCanvas.drawText(letterLayouts[i].layoutInput.text.text, 0f, letterLayouts[i].firstBaseline, glowPaint)
                            }
                        }
                    }
                    val baseAlpha = if (isWordSung || charLp > 0.99f) 1f else focusedAlpha + (1f - focusedAlpha) * sungFactor
                    drawText(letterLayouts[i], color = accent.copy(alpha = if (wordIdx == -1) focusedAlpha else baseAlpha))
                    if (!isWordSung && charLp > 0f && charLp < 1f) {
                        // The fill's leading edge is a soft 12-step gradient, not a hard line.
                        val fXL = charBounds.width * charLp
                        val eW = (charBounds.width * 0.45f).coerceAtLeast(1f)
                        val sWL = (fXL - eW).coerceAtLeast(0f)
                        if (sWL > 0f) {
                            clipRect(left = 0f, top = 0f, right = sWL, bottom = charBounds.height) { drawText(letterLayouts[i], color = accent) }
                        }
                        for (j in 0 until 12) {
                            val start = sWL + j * eW / 12f
                            val end = (sWL + (j + 1) * eW / 12f + 0.5f).coerceAtMost(fXL)
                            if (end > start) {
                                clipRect(left = start, top = 0f, right = end, bottom = charBounds.height) {
                                    drawText(letterLayouts[i], color = accent.copy(alpha = 1f - (j + 0.5f) / 12f))
                                }
                            }
                        }
                    }
                }
                lineCurrentPushes[lineIdx] += charBounds.width * (charScaleX - 1f)
            }
        }
    }
}

/** Same length as the input, so indices found in the result are indices in the original. */
private fun String.normalizedForMatch(): String = map { ch ->
    when (ch) {
        '\u2018', '\u2019', '\u02BC', '`' -> '\''
        '\u201C', '\u201D' -> '"'
        '\u2013', '\u2014' -> '-'
        '\u00A0' -> ' '
        else -> ch
    }
}.joinToString("")
