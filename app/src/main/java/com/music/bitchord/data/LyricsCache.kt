package com.music.bitchord.data

import android.content.Context
import com.music.bitchord.data.lyrics.LyricAlignment
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricSyllable
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Lyrics already found for a track, kept on disk. Once a song's lyrics have
 * loaded they come back from here the next time, instantly and offline, with
 * the word timing intact. Refetching from the lyrics menu replaces them.
 */
object LyricsCache {
    private const val LIMIT = 3000
    private val json = Json { ignoreUnknownKeys = true }

    private fun dir(context: Context) = File(context.filesDir, "lyrics_cache").apply { mkdirs() }

    private fun file(context: Context, videoId: String) =
        File(dir(context), videoId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    fun get(context: Context, videoId: String): LyricsRepository.Result? = runCatching {
        val f = file(context, videoId).takeIf { it.exists() } ?: return null
        decode(f.readText())?.also { f.setLastModified(System.currentTimeMillis()) }
    }.getOrNull()

    fun put(context: Context, videoId: String, result: LyricsRepository.Result) {
        if (result.lines.isEmpty()) return
        runCatching {
            file(context, videoId).writeText(encode(result))
            prune(context)
        }
    }

    fun remove(context: Context, videoId: String) {
        file(context, videoId).delete()
    }

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    fun sizeBytes(context: Context): Long = dir(context).listFiles()?.sumOf { it.length() } ?: 0L

    // Least recently shown go first once there are too many.
    private fun prune(context: Context) {
        val files = dir(context).listFiles() ?: return
        if (files.size <= LIMIT) return
        files.sortedBy { it.lastModified() }.take(files.size - LIMIT).forEach { it.delete() }
    }

    fun encode(result: LyricsRepository.Result): String =
        json.encodeToString(Entry.serializer(), Entry(result.source.name, result.lines.map(::toDto)))

    fun decode(text: String): LyricsRepository.Result? {
        val entry = json.decodeFromString(Entry.serializer(), text)
        val source = LyricsSource.entries.firstOrNull { it.name == entry.source } ?: return null
        val lines = entry.lines.map(::fromDto)
        return if (lines.isEmpty()) null else LyricsRepository.Result(source, lines)
    }

    @Serializable
    private data class Entry(val source: String, val lines: List<Line>)

    @Serializable
    private data class Line(
        val t: Long,
        val text: String,
        val words: List<Word> = emptyList(),
        val until: Long? = null,
        val bg: Line? = null,
        val end: Boolean = false,
        val timing: Line? = null,
    )

    @Serializable
    private data class Word(val s: Long, val e: Long, val text: String, val syl: List<Syllable> = emptyList())

    @Serializable
    private data class Syllable(val s: Long, val e: Long, val a: Int, val b: Int)

    private fun toDto(l: LyricLine): Line = Line(
        l.timeMs,
        l.text,
        l.words.map { w -> Word(w.startMs, w.endMs, w.text, w.syllables.map { Syllable(it.startMs, it.endMs, it.startChar, it.endChar) }) },
        l.sungUntilMs,
        l.background?.let(::toDto),
        l.alignment == LyricAlignment.End,
        l.timingSource?.let(::toDto),
    )

    private fun fromDto(l: Line): LyricLine = LyricLine(
        l.t,
        l.text,
        l.words.map { w -> LyricWord(w.s, w.e, w.text, w.syl.map { LyricSyllable(it.s, it.e, it.a, it.b) }) },
        l.until,
        l.bg?.let(::fromDto),
        if (l.end) LyricAlignment.End else LyricAlignment.Start,
        l.timing?.let(::fromDto),
    )
}
