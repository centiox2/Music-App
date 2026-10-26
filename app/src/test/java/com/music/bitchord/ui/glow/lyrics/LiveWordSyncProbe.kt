package com.music.bitchord.ui.glow.lyrics

import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Probe: real word-synced lyrics through the player's conversion. Set LYRICS_PROBE to an output file. */
class LiveWordSyncProbe {
    @Test
    fun probe() {
        val out = System.getenv("LYRICS_PROBE")
        assumeTrue(out != null)
        val songs = listOf(
            Triple("Blinding Lights", "The Weeknd", 200_040L),
            Triple("Shape of You", "Ed Sheeran", 233_712L),
            Triple("Levitating", "Dua Lipa", 203_064L),
        )
        val report = StringBuilder()
        runBlocking {
            for ((title, artist, dur) in songs) for (source in LyricsSource.offered.filter { it.wordSynced }) {
                val r = runCatching {
                    LyricsRepository.lyrics("probe-$title", title, artist, dur, sources = setOf(source), order = listOf(source))
                }.getOrNull()
                report.appendLine("=== $title / ${source.label}: ${r?.lines?.size ?: "none"} lines, wordSynced=${r?.lines?.count { it.isWordSynced }}")
                val lines = r?.lines ?: continue
                if (lines.none { it.isWordSynced }) continue
                val text = lines.toMetrolistText()
                val (entries, _) = lines.toLyricsEntries()
                val main = entries.filter { !it.isBackground && it.text.isNotBlank() }
                val srcMain = lines.filter { !it.isGap }
                report.appendLine("source lines=${srcMain.size} parsed main=${main.size} parsed all=${entries.size}")
                var issues = 0
                srcMain.forEachIndexed { i, l ->
                    val e = main.getOrNull(i)
                    val srcWords = l.words.filter { it.text.isNotBlank() }
                    val w = e?.words.orEmpty()
                    val probs = mutableListOf<String>()
                    if (e == null) probs += "missing"
                    else {
                        if (e.time != l.timeMs) probs += "lineTime ${l.timeMs}->${e.time}"
                        if (srcWords.isNotEmpty() && srcWords.first().startMs < l.timeMs) probs += "firstWordBeforeLine ${srcWords.first().startMs}<${l.timeMs}"
                        if (w.size != srcWords.size) probs += "words ${srcWords.size}->${w.size}"
                        w.zipWithNext().forEach { (a, b) -> if (b.startTime < a.startTime) probs += "nonMonotonic" }
                        w.forEach { if (it.endTime <= it.startTime) probs += "zeroLen '${it.text}'" }
                        srcWords.zip(w).forEach { (s, p) ->
                            if (kotlin.math.abs(s.startMs - (p.startTime * 1000).toLong()) > 2) probs += "start '${s.text}' ${s.startMs}->${(p.startTime * 1000).toLong()}"
                            if (kotlin.math.abs(s.endMs - (p.endTime * 1000).toLong()) > 150) probs += "end '${s.text}' ${s.endMs}->${(p.endTime * 1000).toLong()}"
                        }
                        val next = main.getOrNull(i + 1)
                        if (next != null && w.isNotEmpty() && (w.last().endTime * 1000).toLong() > next.time + 50) probs += "overlapsNext by ${(w.last().endTime * 1000).toLong() - next.time}ms"
                    }
                    if (probs.isNotEmpty()) { issues++; if (issues <= 12) report.appendLine("  L$i [${l.timeMs}] '${l.text.take(40)}': ${probs.distinct().take(6)}") }
                }
                report.appendLine("lines with issues: $issues")
                // The player's follow logic (GlowLyrics' frame loop), run at 60fps over the song.
                var target = -1
                var prev = emptySet<Int>()
                var lastMax = -1
                val visited = LinkedHashSet<Int>()
                var pos = 0L
                val becameTarget = HashMap<Int, Long>()
                while (pos < dur) {
                    val act = findActiveLineIndices(entries, pos + 400)
                    val sm = act.filter { !entries[it].isBackground }.maxOrNull() ?: (act.maxOrNull() ?: -1)
                    val still = target in act
                    val any = act.isNotEmpty()
                    val should = (any && sm > target) || (!still && !any && prev.isNotEmpty()) ||
                        (target == -1 && any) || (prev.isEmpty() && any && sm > target)
                    if (should) {
                        val t = if (!still && !any) (lastMax + 1 until entries.size).firstOrNull { !entries[it].isBackground } ?: target else sm
                        if (t != -1 && (target == -1 || t > target)) target = t
                    }
                    if (sm > lastMax && sm != -1) lastMax = sm
                    prev = act
                    if (target >= 0) { visited += target; becameTarget.putIfAbsent(target, pos) }
                    pos += 16
                }
                // When the scroll toward each line starts, against when the line is sung.
                val leads = becameTarget.entries.filter { !entries[it.key].isBackground && entries[it.key].text.isNotBlank() }
                    .map { entries[it.key].time - it.value }.sorted()
                report.appendLine("scroll starts before line: min ${leads.firstOrNull()}ms median ${leads.getOrNull(leads.size / 2)}ms max ${leads.lastOrNull()}ms")
                val mainIdx = entries.indices.filter { !entries[it].isBackground && entries[it].text.isNotBlank() }
                val skipped = mainIdx.filter { it !in visited }
                report.appendLine("follow: skipped ${skipped.size} of ${mainIdx.size}: " + skipped.take(8).joinToString { "[${entries[it].time}] ${entries[it].text.take(30)}" })
                report.appendLine(text.lines().take(4).joinToString("\n"))
            }
        }
        File(out!!).writeText(report.toString())
    }
}
