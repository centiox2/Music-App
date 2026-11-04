package com.music.bitchord.ui.glow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Plain checks for this round's fixes that need no device. */
class ReviewFixesTest {
    private fun split(credit: String) = credit.split(CREDIT_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }

    @Test
    fun aCollaborationCreditIsEveryArtist() {
        assertEquals(listOf("Drake", "Future"), split("Drake & Future"))
        assertEquals(listOf("Drake", "21 Savage"), split("Drake, 21 Savage"))
        assertEquals(listOf("PARTYNEXTDOOR", "Drake"), split("PARTYNEXTDOOR feat. Drake"))
        assertEquals(listOf("Travis Scott", "Drake"), split("Travis Scott x Drake"))
    }

    @Test
    fun aBandNameWithAndOrWithStaysWhole() {
        assertEquals(listOf("Florence and the Machine"), split("Florence and the Machine"))
        assertEquals(listOf("Bryson Tiller"), split("Bryson Tiller"))
    }

    @Test
    fun theSameCoverInAnotherSizeIsTheSameArtwork() {
        // The liquid background no longer restarts its fade for a variant of the same picture.
        assertEquals(
            LiquidCovers.identity("https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg"),
            LiquidCovers.identity("https://i.ytimg.com/vi/kJQP7kiw5Fk/maxresdefault.jpg"),
        )
        assertEquals(
            LiquidCovers.identity("https://lh3.googleusercontent.com/abc=w544-h544-l90-rj"),
            LiquidCovers.identity("https://lh3.googleusercontent.com/abc=w1200-h1200-l90-rj"),
        )
        assertNotEquals(
            LiquidCovers.identity("https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg"),
            LiquidCovers.identity("https://i.ytimg.com/vi/fHI8X4OXluQ/hqdefault.jpg"),
        )
    }
}
