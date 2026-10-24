/*
 * The lyrics engine and animations here are ported from Metrolist
 * (https://github.com/metrolistgroup/metrolist, GPL-3.0): ExperimentalLyrics,
 * LyricsLine, LyricsCommon and LyricsUtils.findActiveLineIndices. Lyrics are
 * fetched by BitChord; this file adapts its LyricLine model to Metrolist's.
 */
package com.music.bitchord.ui.glow.lyrics

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.music.bitchord.data.lyrics.LyricAlignment
import com.music.bitchord.data.lyrics.LyricLine

/** One word with its timing, in seconds (Metrolist's unit). */
data class WordTimestamp(
    val text: String,
    val startTime: Double,
    val endTime: Double,
    val hasTrailingSpace: Boolean = true,
)

/**
 * A line as the renderer sees it. Background vocals are their own entries,
 * placed right after the line they belong to. [agent] is "v2" for the second
 * voice of a duet, which sits on the opposite side.
 */
data class LyricsEntry(
    val time: Long,
    val text: String,
    val words: List<WordTimestamp>? = null,
    val agent: String? = null,
    val isBackground: Boolean = false,
    /** The provider's own end for a line without word timing, when it states one. */
    val endMs: Long? = null,
)

sealed class LyricsListItem {
    data class Line(val index: Int, val entry: LyricsEntry) : LyricsListItem()
    data class Indicator(
        val afterLineIndex: Int,
        val gapMs: Long,
        val gapStartMs: Long,
        val gapEndMs: Long,
        val nextAgent: String?,
    ) : LyricsListItem()
}

/** Lines that never become active: unsynced lyrics sit past any real position. */
private const val UNSYNCED_BASE_TIME = 1_000_000_000L

/**
 * BitChord's lines → Metrolist entries. Synced lyrics get Metrolist's blank
 * head entry so a quiet intro shows the interval indicator.
 */
fun List<LyricLine>.toLyricsEntries(): Pair<List<LyricsEntry>, Boolean> {
    val synced = any { it.timeMs > 0 } || any { it.isWordSynced }
    if (!synced) {
        return filterNot { it.isGap }.mapIndexed { i, line ->
            LyricsEntry(UNSYNCED_BASE_TIME + i, line.text)
        } to false
    }
    val out = mutableListOf(LyricsEntry(0L, ""))
    for (line in this) {
        out += line.toEntry(isBackground = false)
        line.background?.let { out += it.toEntry(isBackground = true) }
    }
    return out.sortedWith(compareBy<LyricsEntry> { it.time }.thenBy { it.isBackground }) to true
}

private fun LyricLine.toEntry(isBackground: Boolean): LyricsEntry {
    val words = if (isWordSynced) {
        words.mapIndexed { i, w ->
            val span = wordSpans.getOrNull(i)
            val trailing = span != null && span.last + 1 < text.length && text[span.last + 1].isWhitespace()
            WordTimestamp(w.text, w.startMs / 1000.0, w.endMs / 1000.0, trailing || i < words.lastIndex && span == null)
        }
    } else {
        null
    }
    return LyricsEntry(
        time = timeMs,
        text = text,
        words = words,
        agent = if (alignment == LyricAlignment.End) "v2" else null,
        isBackground = isBackground,
        endMs = sungUntilMs,
    )
}

/** Interleaves lines with an indicator for every instrumental gap over 4 s. */
fun buildMergedList(lines: List<LyricsEntry>, showIntervalIndicator: Boolean = true): List<LyricsListItem> {
    val result = mutableListOf<LyricsListItem>()
    lines.forEachIndexed { i, entry ->
        if (entry.text.isNotBlank()) result.add(LyricsListItem.Line(i, entry))
        if (showIntervalIndicator && i < lines.size - 1) {
            val nextStart = lines[i + 1].time
            val currentEnd = when {
                !entry.words.isNullOrEmpty() -> (entry.words.last().endTime * 1000).toLong()
                entry.text.isBlank() -> entry.time
                else -> entry.endMs
            }
            if (currentEnd != null && currentEnd < nextStart) {
                val gap = nextStart - currentEnd
                if (gap > 4000L) result.add(LyricsListItem.Indicator(i, gap, currentEnd, nextStart, lines[i + 1].agent))
            }
        }
    }
    return result
}

/** Every line being sung at [position]; overlapping lines in duets can be several. */
fun findActiveLineIndices(lines: List<LyricsEntry>, position: Long): Set<Int> {
    val active = mutableSetOf<Int>()
    val hasWordTimings = lines.any { !it.words.isNullOrEmpty() }
    for (index in lines.indices) {
        val line = lines[index]
        if (line.time > position) break
        val lineEndMs: Long = when {
            !line.words.isNullOrEmpty() -> (line.words.last().endTime * 1000).toLong()
            line.endMs != null -> line.endMs
            index + 1 < lines.size -> lines[index + 1].time
            else -> Long.MAX_VALUE
        }
        if (position <= lineEndMs) active.add(index)
    }
    if (!hasWordTimings && active.size > 1) {
        val mainActive = active.filter { !lines[it].isBackground }
        if (mainActive.size > 1) {
            val maxTime = mainActive.maxOf { lines[it].time }
            active.removeAll { it in mainActive && lines[it].time < maxTime }
        }
    }
    return active
}

/** Fades content out towards the given edges. */
fun Modifier.fadingEdge(top: Dp? = null, bottom: Dp? = null): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            if (top != null) {
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = top.toPx()),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (bottom != null) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Black, Color.Transparent),
                        startY = size.height - bottom.toPx(),
                        endY = size.height,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
