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
import androidx.lifecycle.ViewModelProvider
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.playback.PlaybackService
import com.music.bitchord.playback.PlayerState
import com.music.bitchord.ui.MainViewModel
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
 * Renders the real app — real view model, real YouTube Music responses — to
 * PNGs for eyeballing against the design. Not an assertion suite, and it
 * needs the network, so it only runs when asked:
 * `GLOW_SHOTS=/some/dir ./gradlew :app:testDevDebugUnitTest --tests '*GlowScreenshotTest*'`
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
class GlowScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val song = Song(
        videoId = "kJQP7kiw5Fk",
        title = "Despacito",
        artist = "Luis Fonsi",
        thumbnailUrl = "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg",
        durationText = "4:42",
        albumName = "VIDA",
        playbackSource = "Quick picks",
    )

    private fun launch(playing: Boolean = false, light: Boolean = false) {
        assumeTrue("Set GLOW_SHOTS to render screenshots", System.getenv("GLOW_SHOTS") != null)
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).declareComponentUnbindable(ComponentName(app, PlaybackService::class.java))
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, caps)
        AppSettings.setOnboardingComplete()
        AppSettings.setThemeMode(if (light) ThemeMode.LIGHT else ThemeMode.DARK)
        GlowPrefs.init(app)
        GlowPrefs.setNotificationsAsked()
        val player = if (playing) {
            PlayerState(song = song, isPlaying = true, durationMs = 282_000L, queue = listOf(song), queueIndex = 0)
        } else {
            null
        }
        rule.setContent { GlowTheme { GlowApp(playerOverride = player) } }
        settle(24)
    }

    private fun settle(halfSeconds: Int = 6) {
        repeat(halfSeconds) {
            rule.mainClock.advanceTimeBy(500)
            Thread.sleep(500)
        }
    }

    private fun vm() = ViewModelProvider(rule.activity)[MainViewModel::class.java]

    private fun click(text: String) {
        val matcher = hasText(text) or hasContentDescription(text)
        rule.waitUntil(20_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(matcher)[0].performClick()
        settle()
    }

    private fun shot(name: String) {
        val dir = File(System.getenv("GLOW_SHOTS")!!).apply { mkdirs() }
        val view = rule.activity.window.decorView
        // Force a fresh frame so the picture isn't the one before the last action.
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
    fun home() {
        launch(playing = true)
        shot("01-home")
    }

    @Test
    fun player() {
        launch(playing = true)
        click("Despacito")
        shot("02-player")
        click("Lyrics")
        settle(10)
        shot("03-lyrics")
        click("Up next")
        shot("04-up-next")
        click("More options")
        shot("05-song-menu")
    }

    @Test
    fun search() {
        launch(playing = true)
        click("Search")
        settle(8)
        shot("07-search")
        vm().searchFor("daft punk")
        settle(14)
        shot("08-search-results")
    }

    @Test
    fun artist() {
        launch(playing = true)
        click("Search")
        vm().searchFor("daft punk")
        settle(14)
        click("Daft Punk")
        settle(16)
        shot("12-artist")
    }

    @Test
    fun library() {
        launch(playing = true)
        click("Library")
        shot("10-library-signed-out")
    }

    @Test
    fun settings() {
        launch(playing = true)
        click("Account and settings")
        shot("15-settings")
        click("Equalizer & audio")
        shot("13-equalizer")
    }

    @Test
    fun light() {
        launch(playing = true, light = true)
        shot("45-light-home")
        click("Despacito")
        shot("46-light-player")
    }
}
