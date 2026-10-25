package com.music.bitchord.ui.glow

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The glow was drawn from the cover's own colours, so a black cover glowed
 * black on a near-black page — invisible — while a white one glowed white.
 * These pin down that both ends of the range come out visible.
 */
class GlowTintTest {

    private val nearBlack = Color(0xFF050505)
    private val white = Color(0xFFFFFFFF)
    private val deepRed = Color(0xFF7A0D12)
    private val paleYellow = Color(0xFFF4E3A1)

    @Test
    fun darkCoversGlowInTheDark() {
        // The page behind is #0A1416, about 0.0065 luminance: the bloom has to be a
        // different world from that, not a slightly-less-black patch of it.
        listOf(nearBlack, deepRed).forEach { cover ->
            val glow = glowTint(cover, dark = true)
            assertTrue("a dark cover should still glow", glow.luminance() > 0.15f)
        }
    }

    @Test
    fun theHueSurvivesTheLift() {
        val glow = glowTint(deepRed, dark = true)
        assertTrue("red stays red", glow.red > glow.green && glow.red > glow.blue)
    }

    @Test
    fun whiteCoversStillGlowInTheDark() {
        assertTrue(glowTint(white, dark = true).luminance() > glowTint(nearBlack, dark = true).luminance())
    }

    @Test
    fun lightThemeCoversStayGentle() {
        // On a near-white page a white bloom is invisible; the tint is pulled down
        // far enough to read, and never becomes a dark smudge.
        listOf(white, paleYellow).forEach { cover ->
            val glow = glowTint(cover, dark = false)
            assertTrue("light cover should tint the page", glow.luminance() < 0.75f)
        }
        assertTrue(glowTint(nearBlack, dark = false).luminance() > 0.1f)
    }

    @Test
    fun miniPlayerTakesTheCoverColour() {
        val cover = CoverPalette(listOf(deepRed, Color(0xFF1B1B1B)), listOf(0.7f, 0.3f))
        val tint = miniPlayerTint(cover, Color(0xFF1A3A3A), isDark = true)
        assertTrue("the bar keeps the cover's red", tint.red > tint.green && tint.red > tint.blue)
        // Legible: white text sits on it.
        assertTrue(tint.luminance() < 0.2f)
    }

    @Test
    fun aGreyDominantGivesWayToAStrongerColour() {
        // Mostly a black cover with a red corner: the bar should read red, not grey.
        val cover = CoverPalette(
            listOf(Color(0xFF0A0A0A), Color(0xFFB3121A), Color(0xFF1A1A1A)),
            listOf(0.6f, 0.25f, 0.15f),
        )
        val tint = miniPlayerTint(cover, Color.Black, isDark = true)
        assertTrue(tint.red > tint.green * 1.4f)
    }

    @Test
    fun noPaletteFallsBackToTheGivenColour() {
        val fallback = Color(0xFF1A3A3A)
        assertTrue(miniPlayerTint(null, fallback, isDark = true) == fallback)
    }
}
