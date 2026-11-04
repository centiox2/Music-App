package com.music.bitchord.ui.glow

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.playback.PlaybackService
import com.music.bitchord.playback.PlayerState
import com.music.bitchord.ui.MainViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
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
 * The app on a 1080p TV, driven only by the remote's D-pad: focus must land on something with the
 * first press, move between pages, open things with OK, and stay inside the player once it is open.
 * `REVIEW_SHOTS=/dir` also saves pictures of the focus ring.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvDpadTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val song = Song(videoId = "yX0_s1-ExwE", title = "Kilimani Nights", artist = "Amani Wave", thumbnailUrl = null, durationText = "2:25")

    private fun launch() {
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
        val player = PlayerState(song = song, isPlaying = true, durationMs = 145_000, queue = listOf(song), queueIndex = 0)
        rule.setContent { GlowTheme { GlowApp(playerOverride = player) } }
        fakeLibrary()
        settle(12)
        // A TV starts out of touch mode, and the window hands focus to the first focusable view
        // (here, Compose's) on its first layout. Robolectric does neither by itself.
        rule.runOnUiThread {
            val root = android.view.View::class.java.getMethod("getViewRootImpl").invoke(rule.activity.window.decorView)!!
            root.javaClass.getDeclaredMethod("ensureTouchMode", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(root, false)
            val content = rule.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            content.getChildAt(0).requestFocus()
        }
        settle(2)
    }

    @Suppress("UNCHECKED_CAST")
    private fun fakeLibrary() {
        val vm = ViewModelProvider(rule.activity)[MainViewModel::class.java]
        fun field(name: String) = MainViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }
        field("libraryStarted").setBoolean(vm, true)
        (field("_signedIn").get(vm) as MutableStateFlow<Boolean>).value = true
        val playlists = List(6) { ShelfItem("Playlist $it", "${10 + it} songs", null, null, "VLPL$it") }
        LikedSongsCache.songs.value = List(40) { song.copy(videoId = "id$it", title = "Song $it") }
        LikedSongsCache.complete.value = true
        LikedSongsCache.refreshedThisSession = true
        (field("_library").get(vm) as MutableStateFlow<UiState<LibraryPage>>).value = UiState.Success(
            LibraryPage(likedSongs = emptyList(), librarySongs = emptyList(), shelves = listOf(HomeShelf(YtMusicRepository.PLAYLISTS_SHELF, playlists))),
        )
    }

    private fun settle(halfSeconds: Int = 4) = repeat(halfSeconds) {
        rule.mainClock.advanceTimeBy(500)
        Thread.sleep(200)
    }

    private fun press(keyCode: Int, times: Int = 1) = repeat(times) {
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            v.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            v.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        settle(1)
    }

    private fun focused(): SemanticsNodeInteraction? =
        rule.onAllNodes(isFocused(), useUnmergedTree = false).fetchSemanticsNodes().firstOrNull()?.let {
            rule.onAllNodes(isFocused())[0]
        }

    private fun focusedLabel(): String {
        val node = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull() ?: return "<nothing>"
        val texts = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        val desc = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        return listOfNotNull(desc, texts).joinToString(" / ").ifEmpty { "<unlabelled ${node.boundsInRoot}>" }
    }

    private fun shot(name: String) {
        val out = System.getenv("REVIEW_SHOTS") ?: return
        val view = rule.activity.window.decorView
        repeat(3) { rule.runOnUiThread { view.invalidate() }; rule.mainClock.advanceTimeByFrame(); Thread.sleep(100) }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        File(out).apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun remoteReachesEverything() {
        launch()
        // First press with nothing focused puts focus on the screen.
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        println("TV first focus: ${focusedLabel()}")
        rule.runOnUiThread {
            val d = rule.activity.window.decorView
            println("TV debug: touchMode=${d.isInTouchMode} findFocus=${d.findFocus()} windowFocus=${d.hasWindowFocus()}")
        }
        println("TV debug focusables: " + rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused)).fetchSemanticsNodes().size)
        assertTrue("a D-pad press focuses something", focused() != null)
        shot("tv01-first-focus")

        // Walk to the rail and down to Library, then open it with OK.
        press(KeyEvent.KEYCODE_DPAD_LEFT, 4)
        println("TV after left: ${focusedLabel()}")
        var guard = 0
        while (!focusedLabel().contains("Library") && guard++ < 6) press(KeyEvent.KEYCODE_DPAD_DOWN)
        println("TV on: ${focusedLabel()}")
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        settle(4)
        println("TV after OK on Library: ${focusedLabel()}")
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        println("TV in library: ${focusedLabel()}")
        shot("tv02-library")
        val path = mutableListOf<String>()
        repeat(6) { press(KeyEvent.KEYCODE_DPAD_DOWN); path += focusedLabel() }
        println("TV library walk: $path")
        shot("tv03-library-down")

        // Down past the last playlist reaches the mini player (it must not hide under it), and
        // OK opens the player.
        guard = 0
        while (!focusedLabel().contains("Kilimani Nights") && guard++ < 20) press(KeyEvent.KEYCODE_DPAD_DOWN)
        println("TV bottom: ${focusedLabel()}")
        assertTrue("the mini player is reachable from the list", focusedLabel().contains("Kilimani Nights"))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        settle(4)
        val inPlayer = mutableListOf<String>()
        listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN)
            .forEach { press(it); inPlayer += focusedLabel() }
        assertTrue("focus is inside the player: $inPlayer", inPlayer.any { it.contains("Close player") || it.contains("Repeat") })
        println("TV player walk: $inPlayer")
        shot("tv04-player")
        assertTrue("focus stays out of the rail behind the player: $inPlayer",
            inPlayer.none { it == "Home" || it == "Search" || it == "Library" })
        press(KeyEvent.KEYCODE_BACK)
        settle(4)
        press(KeyEvent.KEYCODE_DPAD_UP)
        println("TV after back: ${focusedLabel()}")
        assertTrue(focused() != null)
    }
}
