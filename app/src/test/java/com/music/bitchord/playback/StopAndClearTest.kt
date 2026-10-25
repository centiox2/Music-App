package com.music.bitchord.playback

import android.os.Looper
import androidx.media3.session.MediaLibraryService
import com.music.bitchord.data.model.Song
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** The mini player's swipe-away: stop() then clearMediaItems() on the session's player. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@Config(sdk = [35])
class StopAndClearTest {
    @Test
    fun stoppingAndClearingTheQueueDoesNotCrash() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        val uncaught = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        val service = controller.get()
        val field = PlaybackService::class.java.getDeclaredField("mediaSession").apply { isAccessible = true }
        val session = field.get(service) as MediaLibraryService.MediaLibrarySession
        val player = session.player
        val songs = listOf(
            Song(videoId = "kJQP7kiw5Fk", title = "Despacito", artist = "Luis Fonsi", thumbnailUrl = null, fromAutoplay = false),
            Song(videoId = "fHI8X4OXluQ", title = "Blinding Lights", artist = "The Weeknd", thumbnailUrl = null, fromAutoplay = false),
        )
        player.setMediaItems(songs.map { it.toMediaItem() })
        player.prepare()
        player.play()
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
        player.stop()
        player.clearMediaItems()
        // Let everything the service does in response run.
        repeat(20) { shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS) }
        controller.destroy()
        Thread.setDefaultUncaughtExceptionHandler(previous)
        uncaught.forEach { it.printStackTrace() }
        org.junit.Assert.assertTrue("crashed: ${uncaught.firstOrNull()}", uncaught.isEmpty())
    }
}
