package com.music.bitchord.ui.glow.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsClockTest {
    private val frame = 16_666_667L

    /** Runs [frames] frames at 60fps, the player reporting [reported] for each, and returns what was shown. */
    private fun run(clock: LyricsClock, frames: Int, startNanos: Long = 0L, reported: (Long) -> Long): List<Long> =
        (0 until frames).map { i -> val t = startNanos + i * frame; clock.next(reported(t / 1_000_000), true, t) }

    @Test
    fun `a position reported in steps is shown moving smoothly`() {
        val clock = LyricsClock()
        // The player only updates its answer every 250ms, so read directly it stalls and leaps.
        val shown = run(clock, 240) { ms -> 10_000 + ms / 250 * 250 }
        val steps = shown.zipWithNext { a, b -> b - a }.drop(30)
        assertTrue("never backwards: $steps", steps.all { it >= 0 })
        assertTrue("no stalls or leaps: ${steps.minOrNull()}..${steps.maxOrNull()}", steps.all { it in 5..40 })
        assertTrue(kotlin.math.abs(shown.last() - (10_000 + 239 * 1000 / 60)) < 260)
    }

    @Test
    fun `a lead is closed by slowing down, not by stopping`() {
        val clock = LyricsClock()
        run(clock, 10) { ms -> 20_000 + ms }
        // The player corrects itself 400ms back.
        val shown = run(clock, 120, 10 * frame) { ms -> 20_000 + ms - 400 }
        val steps = shown.zipWithNext { a, b -> b - a }
        assertTrue(steps.all { it >= 7 })
        assertTrue(kotlin.math.abs(shown.last() - (20_000 + 129 * 1000 / 60 - 400)) < 40)
    }

    @Test
    fun `a seek is followed at once`() {
        val clock = LyricsClock()
        run(clock, 10) { ms -> 5_000 + ms }
        val t = 10 * frame
        assertEquals(90_000L, clock.next(90_000, true, t))
        assertTrue(clock.sought)
    }
}
