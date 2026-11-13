package com.music.bitchord.ui.glow

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.playback.PlaybackService
import com.music.bitchord.playback.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.File

/**
 * The player at the owner's TV size (960×540dp), in each pane, to PNGs for checking the layout.
 * Opt-in like the other screenshot tests: `GLOW_SHOTS=/dir`. CI runs it in tv-shots.yml.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvPlayerShotsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val song = Song(
        videoId = "kJQP7kiw5Fk",
        title = "Despacito",
        artist = "Luis Fonsi, Daddy Yankee",
        thumbnailUrl = "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg",
        durationText = "4:42",
        albumName = "VIDA",
        playbackSource = "Quick picks",
    )

    private fun launch() {
        assumeTrue("Set GLOW_SHOTS to render screenshots", System.getenv("GLOW_SHOTS") != null)
        val app = RuntimeEnvironment.getApplication()
        coil3.SingletonImageLoader.setUnsafe(coil3.ImageLoader.Builder(app).components { add(coil3.decode.BitmapFactoryDecoder.Factory()) }.build())
        shadowOf(app).declareComponentUnbindable(ComponentName(app, PlaybackService::class.java))
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, caps)
        AppSettings.setOnboardingComplete()
        AppSettings.setReduceAnimation(true)
        AppSettings.setThemeMode(ThemeMode.DARK)
        GlowPrefs.init(app)
        GlowPrefs.setNotificationsAsked()
        @Suppress("UNCHECKED_CAST")
        (com.music.bitchord.data.Connectivity::class.java.getDeclaredField("_online").apply { isAccessible = true }
            .get(com.music.bitchord.data.Connectivity) as MutableStateFlow<Boolean>).value = true
        val player = PlayerState(song = song, isPlaying = true, durationMs = 282_000, queue = listOf(song), queueIndex = 0)
            .also { it.position.report(102_000) }
        rule.setContent { GlowTheme { GlowApp(playerOverride = player) } }
        settle(16)
    }

    private fun settle(halfSeconds: Int = 6) {
        repeat(halfSeconds) {
            rule.mainClock.advanceTimeBy(500)
            Thread.sleep(500)
        }
    }

    private fun click(text: String) {
        val matcher = hasText(text) or hasContentDescription(text)
        rule.waitUntil(20_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(matcher)[0].performClick()
        settle()
    }

    private fun shot(name: String) {
        val dir = File(System.getenv("GLOW_SHOTS")!!).apply { mkdirs() }
        val view = rule.activity.window.decorView
        repeat(3) {
            rule.runOnUiThread { view.invalidate() }
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(150)
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun tvPlayer() {
        launch()
        shot("tv-00-home")
        click("Despacito")
        settle(6)
        shot("tv-01-player")
        click("Up next")
        settle(4)
        shot("tv-02-up-next")
        click("Lyrics")
        settle(12)
        shot("tv-03-lyrics")
    }
}
