package com.music.bitchord.ui.glow

import com.music.bitchord.data.LyricsCache
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsOrderTest {
    private val all = LyricsSource.entries

    @Test
    fun `moving up swaps with the source shown above, not a hidden one`() {
        val a = all[0]
        val hidden = all[1]
        val b = all[2]
        val c = all[3]
        val order = listOf(a, hidden, b, c)
        val shown = listOf(a, b, c)
        assertEquals(listOf(b, a, c, hidden), moveSource(shown, order, 1, 0))
        assertEquals(listOf(a, c, b, hidden), moveSource(shown, order, 1, 2))
    }

    @Test
    fun `a move off either end changes nothing`() {
        val order = all.take(3)
        assertEquals(order, moveSource(order, order, 0, -1))
        assertEquals(order, moveSource(order, order, 2, 3))
    }

    @Test
    fun `the cache remembers the order lyrics were found under`() {
        val result = LyricsRepository.Result(all[0], listOf(LyricLine(1000L, "hello")))
        val text = LyricsCache.encode(result, "A,B")
        assertEquals("hello", LyricsCache.decode(text)!!.lines.single().text)
        // Entries written before the order was recorded still read.
        assertEquals(all[0], LyricsCache.decode(LyricsCache.encode(result))!!.source)
    }
}
