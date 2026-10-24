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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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

    private var song = Song(
        videoId = "kJQP7kiw5Fk",
        title = "Despacito",
        artist = "Luis Fonsi",
        thumbnailUrl = "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg",
        durationText = "4:42",
        albumName = "VIDA",
        playbackSource = "Quick picks",
    )

    private var lastPlayer: PlayerState? = null

    private fun launch(playing: Boolean = false, light: Boolean = false, positionMs: Long = 0L, route: Route? = null, paused: Boolean = false, durationMs: Long = 282_000L) {
        assumeTrue("Set GLOW_SHOTS to render screenshots", System.getenv("GLOW_SHOTS") != null)
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).declareComponentUnbindable(ComponentName(app, PlaybackService::class.java))
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, caps)
        AppSettings.setOnboardingComplete()
        // Still frames: the liquid background never stops moving otherwise, and Compose never idles.
        AppSettings.setReduceAnimation(true)
        AppSettings.setThemeMode(if (light) ThemeMode.LIGHT else ThemeMode.DARK)
        GlowPrefs.init(app)
        GlowPrefs.setNotificationsAsked()
        val player = if (playing) {
            PlayerState(song = song, isPlaying = !paused, durationMs = durationMs, queue = listOf(song), queueIndex = 0)
                .also { it.position.report(positionMs); lastPlayer = it }
        } else {
            null
        }
        rule.setContent { GlowTheme { GlowApp(playerOverride = player, startRoute = route) } }
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
        click("Up next")
        shot("04-up-next")
        // Last: while lyrics play, their frame loop keeps Compose from ever idling.
        click("Lyrics")
        settle(10)
        shot("03-lyrics")
    }

    @Test
    fun songMenu() {
        launch(playing = true)
        click("Despacito")
        click("More options")
        shot("05-song-menu")
    }

    @Test
    fun lyrics() {
        launch(playing = true, positionMs = 62_000L)
        val player = lastPlayer!!
        click("Despacito")
        click("Lyrics")
        settle(12)
        shot("03b-lyrics-mid-song")
        player.position.report(66_400L)
        settle(4)
        shot("03c-lyrics-later")
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
    fun wordSync() {
        song = Song(videoId = "fHI8X4OXluQ", title = "Blinding Lights", artist = "The Weeknd", thumbnailUrl = "https://i.ytimg.com/vi/fHI8X4OXluQ/hqdefault.jpg", durationText = "3:20")
        launch(playing = true, positionMs = 30_600L, paused = true, durationMs = 200_000L)
        click("Blinding Lights")
        click("Lyrics")
        settle(30)
        shot("75-word-sync")
    }

    @Test
    fun wordSyncIsolated() {
        assumeTrue(System.getenv("GLOW_SHOTS") != null)
        val found = kotlinx.coroutines.runBlocking {
            com.music.bitchord.data.lyrics.LyricsRepository.lyrics(
                "fHI8X4OXluQ", "Blinding Lights", "The Weeknd", 200_000L, null,
                setOf(com.music.bitchord.data.lyrics.LyricsSource.LRC_RED), listOf(com.music.bitchord.data.lyrics.LyricsSource.LRC_RED), true,
            )
        }!!
        var position by androidx.compose.runtime.mutableLongStateOf(30_000L)
        GlowPrefs.init(RuntimeEnvironment.getApplication())
        AppSettings.setThemeMode(ThemeMode.DARK)
        rule.setContent {
            GlowTheme {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize().background(Glow.colors.base).padding(horizontal = 20.dp, vertical = 40.dp),
                ) {
                    com.music.bitchord.ui.glow.lyrics.GlowLyrics(
                        lyrics = found.lines, checked = true,
                        positionProvider = { position }, isPlayingProvider = { false },
                        lyricsOffsetMs = 0, onSeek = {}, onShare = {},
                    )
                }
            }
        }
        listOf(30_150L to "76a-word-start", 30_900L to "76b-word-mid", 31_700L to "76c-word-late", 33_000L to "76d-next-line").forEach { (ms, name) ->
            position = ms
            settle(8)
            shot(name)
        }
    }

    @Test
    fun meshAccuracy() {
        assumeTrue(System.getenv("GLOW_SHOTS") != null)
        GlowPrefs.init(RuntimeEnvironment.getApplication())
        rule.setContent {
            GlowTheme {
                androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxSize()) {
                    GlowMesh(
                        CoverPalette(listOf(androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.graphics.Color(0xFFD01010)), listOf(0.9f, 0.1f)),
                        androidx.compose.ui.Modifier.weight(1f), moving = false,
                    )
                    GlowMesh(
                        CoverPalette(
                            listOf(androidx.compose.ui.graphics.Color(0xFF1F3A5F), androidx.compose.ui.graphics.Color(0xFFF2A541), androidx.compose.ui.graphics.Color.White),
                            listOf(0.6f, 0.3f, 0.1f),
                        ),
                        androidx.compose.ui.Modifier.weight(1f), moving = false,
                    )
                }
            }
        }
        settle(4)
        shot("85-mesh")
    }

    @Test
    fun homeLate() {
        launch(playing = true)
        shot("80-home-a")
        settle(10)
        shot("80-home-b")
        rule.onRoot().performTouchInput { swipeUp() }
        settle(6)
        shot("80-home-c")
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        settle(3)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h1900dp-xhdpi")
    fun pageSettingsPlaybackOptions() {
        launch(playing = true, route = Route.SettingsPlaybackOptions)
        shot("61-playback")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h1900dp-xhdpi")
    fun pageSettingsTranslation() {
        launch(playing = true, route = Route.SettingsTranslation)
        shot("62-translation")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h1900dp-xhdpi")
    fun pageSettingsPrivacy() {
        launch(playing = true, route = Route.SettingsPrivacy)
        shot("63-privacy")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h1900dp-xhdpi")
    fun pageSettingsBackup() {
        launch(playing = true, route = Route.SettingsBackup)
        shot("64-backup")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h1900dp-xhdpi")
    fun pageSettingsLyrics() {
        launch(playing = true, route = Route.SettingsLyrics)
        shot("65-lyrics-sources")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h1900dp-xhdpi")
    fun pageSettingsTheme() {
        launch(playing = true, route = Route.SettingsTheme)
        shot("66-theme")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h1900dp-xhdpi")
    fun pageSettingsLastFm() {
        launch(playing = true, route = Route.SettingsLastFm)
        shot("67-scrobbling")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h1900dp-xhdpi")
    fun pageSettingsStorage() {
        launch(playing = true, route = Route.SettingsStorage)
        shot("68-storage")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h1900dp-xhdpi")
    fun pageSettings() {
        launch(playing = true, route = Route.Settings)
        shot("60-settings-tall")
    }

    @Test
    fun lyricsTranslated() {
        val app = RuntimeEnvironment.getApplication()
        com.music.bitchord.ui.glow.lyrics.TranslationPrefs.init(app)
        com.music.bitchord.ui.glow.lyrics.TranslationPrefs.setShowTranslation(true)
        com.music.bitchord.data.settings.AppSettings.setTranslationLanguage("en")
        try {
            launch(playing = true, positionMs = 62_000L)
            click("Despacito")
            click("Lyrics")
            settle(16)
            shot("70-lyrics-translated")
            settle(12)
            shot("70b-lyrics-translated")
        } finally {
            com.music.bitchord.ui.glow.lyrics.TranslationPrefs.setShowTranslation(false)
        }
    }

    @Test
    fun light() {
        launch(playing = true, light = true)
        shot("45-light-home")
        click("Despacito")
        shot("46-light-player")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1180dp-h820dp-land-xhdpi")
    fun tablet() {
        launch(playing = true)
        shot("49-tablet-home")
        click("Despacito")
        settle(10)
        shot("49-tablet-player")
        settle(6)
        shot("49-tablet-player")
    }

    @Test
    fun launcherIcon() {
        assumeTrue(System.getenv("GLOW_SHOTS") != null)
        val app = RuntimeEnvironment.getApplication()
        val icon = app.getDrawable(com.music.bitchord.R.mipmap.ic_launcher)!!
        val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 432, 432)
        icon.draw(Canvas(bitmap))
        File(File(System.getenv("GLOW_SHOTS")!!).apply { mkdirs() }, "00-icon.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
