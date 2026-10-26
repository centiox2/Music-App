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
    /** The line translated, romanized or transcribed, shown beneath it. */
    val translation: String? = null,
) : Comparable<LyricsEntry> {
    // As Metrolist's: entries order by start time, which its parser sorts with.
    override fun compareTo(other: LyricsEntry): Int = time.compareTo(other.time)
}

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
 * Lyrics as Metrolist shows them. The lines are written out in Metrolist's own text format
 * (rich-sync LRC: whole words with their start times, `{bg}` for background vocals,
 * `{agent:v2}` for a duet's second voice) and read back with Metrolist's parser, so the
 * timing the animation runs on is exactly Metrolist's. Syllable splits and other
 * BitChord-specific timing never reach the renderer.
 */
fun List<LyricLine>.toLyricsEntries(translated: List<LyricLine>? = null): Pair<List<LyricsEntry>, Boolean> {
    val synced = any { it.timeMs > 0 } || any { it.isWordSynced }
    // Translations come back in the same shape as the lines they translate.
    val tr = translated?.takeIf { it.size == size }
    fun String?.orNullIfSame(original: String) = this?.trim()?.takeIf { it.isNotBlank() && !it.equals(original.trim(), ignoreCase = true) }
    if (!synced) {
        return indices.filterNot { this[it].isGap }.mapIndexed { i, li ->
            LyricsEntry(UNSYNCED_BASE_TIME + i, this[li].text, translation = tr?.get(li)?.text.orNullIfSame(this[li].text))
        } to false
    }
    val parsed = MetrolistLyrics.parseLyrics(toMetrolistText())
    // Translations are matched back to the line they belong to by time and text.
    val translations = HashMap<Triple<Long, String, Boolean>, String>()
    forEachIndexed { li, line ->
        tr?.get(li)?.text.orNullIfSame(line.text)?.let { translations[Triple(line.timeMs, line.text.trim(), false)] = it }
        line.background?.let { bg ->
            tr?.get(li)?.background?.text.orNullIfSame(bg.text)?.let { translations[Triple(bg.timeMs, bg.text.trim(), true)] = it }
        }
    }
    val entries = parsed.map { e ->
        translations[Triple(e.time, e.text.trim(), e.isBackground)]?.let { e.copy(translation = it) } ?: e
    }
    // Metrolist's blank head entry, so a quiet intro shows the interval indicator.
    val withHead = if (entries.firstOrNull()?.time?.let { it > 0 } != false) listOf(LyricsEntry(0L, "")) + entries else entries
    return withHead to true
}

/** The lines in Metrolist's rich-sync text format; see [toLyricsEntries]. */
fun List<LyricLine>.toMetrolistText(): String = buildString {
    for (line in this@toMetrolistText) {
        if (line.isGap) {
            appendLine("[${stamp(line.timeMs)}]")
            continue
        }
        appendLine(line.metrolistLine(background = false))
        line.background?.let { appendLine(it.metrolistLine(background = true)) }
    }
}

private fun LyricLine.metrolistLine(background: Boolean): String = buildString {
    append('[').append(stamp(timeMs)).append(']')
    if (background) append("{bg}") else if (alignment == LyricAlignment.End) append("{agent:v2}")
    if (!isWordSynced) {
        append(text.trim())
        return@buildString
    }
    var lastEnd = timeMs
    words.forEachIndexed { i, w ->
        val clean = w.text.trim()
        if (clean.isEmpty()) return@forEachIndexed
        val span = wordSpans.getOrNull(i)
        val spaced = span != null && span.last + 1 < text.length && text[span.last + 1].isWhitespace()
        append('<').append(stamp(w.startMs)).append('>').append(clean)
        if (spaced && i < words.lastIndex) append(' ')
        lastEnd = maxOf(lastEnd, w.endMs, w.startMs + 1)
    }
    // Metrolist's closing stamp: when the last word ends.
    append('<').append(stamp(lastEnd)).append('>')
}

private fun stamp(ms: Long): String {
    val t = ms.coerceAtLeast(0L)
    return "%02d:%02d.%03d".format(java.util.Locale.ROOT, t / 60_000, (t / 1000) % 60, t % 1000)
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
