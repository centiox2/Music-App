package com.music.bitchord.ui.glow.lyrics

import com.music.bitchord.data.LyricsQuery
import com.music.bitchord.data.lyrics.LyricAlignment
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricSyllable
import com.music.bitchord.data.lyrics.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lyrics reach the screen through Metrolist's own text format and parser. */
class MetrolistLyricsTest {
    private val lines = listOf(
        LyricLine(
            1_000, "hello beautiful world",
            words = listOf(
                LyricWord(1_000, 1_400, "hello"),
                // Syllables from the source: shown as one word, as Metrolist does.
                LyricWord(1_400, 2_200, "beautiful", listOf(LyricSyllable(1_400, 1_700, 0, 4), LyricSyllable(1_700, 2_200, 4, 9))),
                LyricWord(2_200, 2_900, "world"),
            ),
            background = LyricLine(2_000, "(oh)", words = listOf(LyricWord(2_000, 2_600, "(oh)"))),
        ),
        LyricLine(4_000, "second voice", words = listOf(LyricWord(4_000, 4_500, "second"), LyricWord(4_500, 5_000, "voice")), alignment = LyricAlignment.End),
    )

    @Test
    fun wordTimingComesThroughMetrolistsParser() {
        val (entries, synced) = lines.toLyricsEntries()
        assertTrue(synced)
        // Metrolist's blank head entry, then the lines in time order.
        assertEquals("", entries.first().text)
        val first = entries.first { it.text == "hello beautiful world" }
        assertEquals(listOf("hello", "beautiful", "world"), first.words!!.map { it.text })
        assertEquals(1.4, first.words!![1].startTime, 0.001)
        assertEquals(2.2, first.words!![1].endTime, 0.001)
        // The closing stamp: the last word ends when the source says it does.
        assertEquals(2.9, first.words!!.last().endTime, 0.001)
        assertTrue(first.words!![0].hasTrailingSpace)
        assertFalse(first.words!!.last().hasTrailingSpace)
        assertFalse(first.isBackground)
    }

    @Test
    fun aWordBeforeAPauseEndsWhenTheSingerStops() {
        // "love," is sung for 0.5s, then nothing until "maybe": the highlight must stop at 35.38s,
        // not crawl on to 35.73s, as it did when a word lasted until the next one's start.
        val line = LyricLine(
            32_873, "show me love, maybe",
            words = listOf(
                LyricWord(32_873, 33_300, "show"),
                LyricWord(33_346, 33_600, "me"),
                LyricWord(34_800, 35_382, "love,"),
                LyricWord(35_729, 37_394, "maybe"),
            ),
        )
        val words = listOf(line).toLyricsEntries().first.first { it.text.isNotBlank() }.words!!
        assertEquals(listOf("show", "me", "love,", "maybe"), words.map { it.text })
        assertEquals(35.382, words[2].endTime, 0.001)
        assertEquals(35.729, words[3].startTime, 0.001)
        // A gap under 60ms isn't marked: "show" runs on to "me".
        assertEquals(33.346, words[0].endTime, 0.001)
        assertTrue(words[2].hasTrailingSpace)
        assertEquals("show me love, maybe", listOf(line).toLyricsEntries().first.first { it.text.isNotBlank() }.text)
    }

    @Test
    fun backgroundAndDuetLinesKeepTheirRoles() {
        val (entries, _) = lines.toLyricsEntries()
        val bg = entries.first { it.text.contains("oh") }
        assertTrue(bg.isBackground)
        val second = entries.first { it.text == "second voice" }
        assertEquals("v2", second.agent)
        assertNull(entries.first { it.text == "hello beautiful world" }.agent)
    }

    @Test
    fun lineSyncedLyricsParseAsLines() {
        val (entries, synced) = listOf(LyricLine(500, "one"), LyricLine(65_250, "two")).toLyricsEntries()
        assertTrue(synced)
        assertEquals(listOf("", "one", "two"), entries.map { it.text })
        assertEquals(65_250L, entries.last().time)
        assertTrue(entries.last().words.isNullOrEmpty())
    }

    @Test
    fun aMusicVideosArtistComesFromItsTitle() {
        val q = LyricsQuery.of("Drake - Hotline Bling (Official Video)", "DrakeVEVO", isVideo = true)
        assertEquals(LyricsQuery("Hotline Bling", "Drake"), q)
    }

    @Test
    fun aChannelNameLosesItsSuffix() {
        assertEquals("Drake", LyricsQuery.channelName("Drake - Topic"))
        assertEquals("Drake", LyricsQuery.channelName("DrakeVEVO"))
        val q = LyricsQuery.of("Hotline Bling", "DrakeVEVO", isVideo = true)
        assertEquals(LyricsQuery("Hotline Bling", "Drake"), q)
    }

    @Test
    fun aSongKeepsItsArtistAndLosesBrackets() {
        val q = LyricsQuery.of("Life Is Good (feat. Drake)", "Future", isVideo = false)
        assertEquals(LyricsQuery("Life Is Good", "Future"), q)
    }
}
