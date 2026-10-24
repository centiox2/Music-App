package com.music.bitchord.data

import android.content.Context
import com.music.bitchord.data.lyrics.EnhancedLrc
import com.music.bitchord.data.lyrics.LyricLine
import java.io.File

/**
 * Lyrics the user has corrected by hand, one LRC file per track. Read before
 * any provider is asked, so an edit sticks to its song (Metrolist keeps its
 * edits in its database the same way).
 */
object UserLyrics {
    private fun dir(context: Context) = File(context.filesDir, "lyrics_edits").apply { mkdirs() }

    private fun file(context: Context, videoId: String) =
        File(dir(context), videoId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".lrc")

    fun has(context: Context, videoId: String) = file(context, videoId).exists()

    fun get(context: Context, videoId: String): List<LyricLine>? =
        file(context, videoId).takeIf { it.exists() }?.readText()?.let(::parse)

    fun save(context: Context, videoId: String, text: String): List<LyricLine> {
        file(context, videoId).writeText(text)
        return parse(text)
    }

    fun remove(context: Context, videoId: String) {
        file(context, videoId).delete()
    }

    /** Timed LRC (line or word stamps) when it has stamps; otherwise plain lines. */
    fun parse(text: String): List<LyricLine> {
        val timed = runCatching { EnhancedLrc.parse(text) }.getOrDefault(emptyList())
        if (timed.any { it.text.isNotBlank() }) return timed
        return text.lines().map { LyricLine(0L, it.trim()) }.dropWhile { it.text.isEmpty() }
    }
}
