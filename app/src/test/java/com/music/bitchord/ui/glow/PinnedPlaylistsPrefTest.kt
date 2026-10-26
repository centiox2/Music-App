package com.music.bitchord.ui.glow

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** A build that stored pinned playlists as a string set crashed the next one at launch. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [35])
class PinnedPlaylistsPrefTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("pinned_test", Context.MODE_PRIVATE)

    @Test
    fun `pins stored as a set are read and rewritten as text`() {
        prefs.edit().putStringSet("pinned_playlists", setOf("VLabc")).commit()
        assertEquals(listOf("VLabc"), GlowPrefs.readPinned(prefs))
        assertEquals("VLabc", prefs.getString("pinned_playlists", null))
        assertEquals(listOf("VLabc"), GlowPrefs.readPinned(prefs))
    }

    @Test
    fun `pins stored as text keep their order`() {
        prefs.edit().putString("pinned_playlists", "VLb\nVLa").commit()
        assertEquals(listOf("VLb", "VLa"), GlowPrefs.readPinned(prefs))
    }

    @Test
    fun `nothing stored is no pins`() {
        prefs.edit().clear().commit()
        assertEquals(emptyList<String>(), GlowPrefs.readPinned(prefs))
    }
}
