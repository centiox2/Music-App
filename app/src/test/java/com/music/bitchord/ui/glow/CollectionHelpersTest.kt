package com.music.bitchord.ui.glow

import com.music.bitchord.data.model.ShelfItem
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionHelpersTest {
    private fun release(title: String, subtitle: String) = ShelfItem(title, subtitle, null, null, title)

    @Test
    fun `a newer album beats an older single`() {
        val album = release("New album", "Album • 2025")
        val single = release("Old single", "Single • 2023")
        assertEquals(album, latestRelease(listOf(album), listOf(single)))
    }

    @Test
    fun `a newer single beats an older album`() {
        val album = release("Old album", "2021")
        val single = release("New single", "Single • 2024")
        assertEquals(single, latestRelease(listOf(album), listOf(single)))
    }

    @Test
    fun `the album goes first on the same year or with no years`() {
        val album = release("Album", "Album • 2024")
        val single = release("Single", "Single • 2024")
        assertEquals(album, latestRelease(listOf(album), listOf(single)))
        assertEquals(release("Album", ""), latestRelease(listOf(release("Album", "")), listOf(release("Single", ""))))
    }

    @Test
    fun `only one shelf is enough`() {
        val single = release("Single", "Single • 2024")
        assertEquals(single, latestRelease(emptyList(), listOf(single)))
    }

    @Test
    fun `counts and durations leave the subtitle`() {
        assertEquals(listOf("Sam"), subtitleWithoutCounts("Playlist • Sam • 2 tracks • 7 minutes"))
        assertEquals(listOf("Sam", "1.2K views"), subtitleWithoutCounts("Sam · 120 songs · 1.2K views · 6+ hours"))
        assertEquals(listOf("Sam"), subtitleWithoutCounts("Sam • 1 hour, 12 minutes"))
    }
}
