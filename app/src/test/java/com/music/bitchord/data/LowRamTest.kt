package com.music.bitchord.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowRamTest {
    @Test
    fun `android low ram flag always recommends low memory mode`() {
        assertTrue(LowRam.recommendedFor(systemLowRam = true, totalBytes = 8L * GIB))
    }

    @Test
    fun `one gigabyte device recommends low memory mode`() {
        assertTrue(LowRam.recommendedFor(systemLowRam = false, totalBytes = GIB))
    }

    @Test
    fun `ordinary device keeps normal mode by default`() {
        assertFalse(LowRam.recommendedFor(systemLowRam = false, totalBytes = 4L * GIB))
    }

    private companion object {
        const val GIB = 1024L * 1024 * 1024
    }
}
