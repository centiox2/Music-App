package com.music.bitchord.data

import com.music.bitchord.data.lyrics.LyricAlignment
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricSyllable
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsCacheTest {
    @Test
    fun wordTimingSurvivesTheRoundTrip() {
        val line = LyricLine(
            timeMs = 1200,
            text = "hello world",
            words = listOf(
                LyricWord(1200, 1600, "hello", listOf(LyricSyllable(1200, 1400, 0, 3), LyricSyllable(1400, 1600, 3, 5))),
                LyricWord(1600, 2000, "world"),
            ),
            sungUntilMs = 2000,
            background = LyricLine(1500, "(oh)", words = listOf(LyricWord(1500, 1800, "(oh)"))),
            alignment = LyricAlignment.End,
        )
        val result = LyricsRepository.Result(LyricsSource.LRCLIB, listOf(line, LyricLine(3000, "")))
        val back = LyricsCache.decode(LyricsCache.encode(result))!!
        assertEquals(result.source, back.source)
        assertEquals(result.lines, back.lines)
    }
}
