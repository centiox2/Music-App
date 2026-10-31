package com.music.bitchord.ui.glow

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.StatsPeriod
import com.music.bitchord.data.stats.ViViImport
import com.music.bitchord.data.stats.report
import com.music.bitchord.playback.PlaybackService
import com.music.bitchord.playback.PlayerState
import com.music.bitchord.ui.MainViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
 * Renders the screens added in the Library / Statistics / backup round, for review.
 * Opt-in: `REVIEW_SHOTS=/dir` (and `VIVI_BACKUP=/path/to.backup` for real statistics).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
class ReviewScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val song = Song(
        videoId = "yX0_s1-ExwE",
        title = "Kilimani Nights",
        artist = "Amani Wave",
        thumbnailUrl = "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg",
        durationText = "2:25",
    )

    private fun dir() = File(System.getenv("REVIEW_SHOTS")!!).apply { mkdirs() }

    private fun launch(light: Boolean = false, route: Route? = null, signedIn: Boolean = true) {
        assumeTrue(System.getenv("REVIEW_SHOTS") != null)
        val app = RuntimeEnvironment.getApplication()
        // Robolectric has no native ImageDecoder; BitmapFactory decodes covers there.
        coil3.SingletonImageLoader.setUnsafe(coil3.ImageLoader.Builder(RuntimeEnvironment.getApplication()).components { add(coil3.decode.BitmapFactoryDecoder.Factory()) }.build())
        shadowOf(app).declareComponentUnbindable(ComponentName(app, PlaybackService::class.java))
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, caps)
        AppSettings.setOnboardingComplete()
        AppSettings.setReduceAnimation(true)
        AppSettings.setThemeMode(if (light) ThemeMode.LIGHT else ThemeMode.DARK)
        GlowPrefs.init(app)
        GlowPrefs.setNotificationsAsked()
        val player = PlayerState(song = song, isPlaying = true, durationMs = 145_000, queue = listOf(song), queueIndex = 0)
        rule.setContent { GlowTheme { GlowApp(playerOverride = player, startRoute = route) } }
        if (signedIn) fakeLibrary()
        settle(20)
    }

    @Suppress("UNCHECKED_CAST")
    private fun fakeLibrary() {
        val vm = ViewModelProvider(rule.activity)[MainViewModel::class.java]
        fun field(name: String) = MainViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }
        field("libraryStarted").setBoolean(vm, true)
        (field("_signedIn").get(vm) as MutableStateFlow<Boolean>).value = true
        val covers = listOf(
            "https://i.ytimg.com/vi/fHI8X4OXluQ/hqdefault.jpg",
            "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg",
            "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg",
            "https://i.ytimg.com/vi/RgKAFK5djSk/hqdefault.jpg",
            "https://i.ytimg.com/vi/OPf0YbXqDm0/hqdefault.jpg",
            "https://i.ytimg.com/vi/09R8_2nJtjg/hqdefault.jpg",
        )
        val names = listOf("90s Hip Hop" to "44 songs", "Afro Main" to "142 songs", "Main Rap" to "108 songs",
            "Atmospheric Night" to "90 songs", "My Songs" to "164 songs", "Hip-hop Chill" to "37 songs",
            "All My R&B" to "2 songs", "A playlist with a rather long name that should be cut short" to "12 songs")
        val playlists = names.mapIndexed { i, (t, s) -> ShelfItem(t, s, covers[i % covers.size], null, "VLPL$i") }
        GlowPrefs.togglePinned("VLPL2"); GlowPrefs.togglePinned("VLPL1"); GlowPrefs.togglePinned("VLPL0")
        LikedSongsCache.songs.value = List(574) { song.copy(videoId = "id$it") }
        LikedSongsCache.complete.value = true
        LikedSongsCache.refreshedThisSession = true
        (field("_library").get(vm) as MutableStateFlow<UiState<LibraryPage>>).value = UiState.Success(
            LibraryPage(likedSongs = emptyList(), librarySongs = emptyList(), shelves = listOf(HomeShelf(YtMusicRepository.PLAYLISTS_SHELF, playlists))),
        )
    }

    private fun settle(halfSeconds: Int = 6) = repeat(halfSeconds) {
        rule.mainClock.advanceTimeBy(500)
        Thread.sleep(500)
    }

    private fun click(text: String) {
        val matcher = hasText(text) or hasContentDescription(text)
        rule.waitUntil(20_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(matcher)[0].performClick()
        settle()
    }

    private fun shot(name: String) {
        val view = rule.activity.window.decorView
        repeat(3) {
            rule.runOnUiThread { view.invalidate() }
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(150)
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        File(dir(), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun importVivi() {
        val path = System.getenv("VIVI_BACKUP") ?: return
        val app = RuntimeEnvironment.getApplication()
        // ListeningStats is a process-wide object and outlives a test, so measure the change.
        val before = runBlocking { ListeningStats.report(StatsPeriod.LIFETIME) }
        val first = runBlocking { ViViImport.import(app, Uri.fromFile(File(path))).getOrThrow() }
        val again = runBlocking { ViViImport.import(app, Uri.fromFile(File(path))).getOrThrow() }
        val report = runBlocking { ListeningStats.report(StatsPeriod.LIFETIME) }
        File(dir(), "import.txt").writeText(
            "before: plays=${before.plays} ms=${before.totalMs}\nfirst: $first\nagain: $again\nlifetime: plays=${report.plays} ms=${report.totalMs} " +
                "tracks=${report.tracks} albums=${report.albums} artists=${report.artists} " +
                "busiest=${report.busiestWeekday} streak=${report.longestStreak} top=${report.topArtists.take(3).map { it.title }}\n",
        )
        assertEquals("a second import must add nothing", 0, again.plays)
        assertEquals("the import adds exactly its plays", before.plays + first.plays, report.plays)
        assertEquals("and exactly its time", before.totalMs + first.totalMs, report.totalMs)
    }

    @Test
    fun library() {
        launch()
        click("Library")
        shot("r01-library")
        click("Pinned")
        shot("r02-library-pinned")
        click("Pinned")
        click("All")
        rule.onAllNodes(hasContentDescription("More options"))[0].performClick()
        settle()
        // The sheet is its own window, so it is checked in the semantics tree rather than drawn.
        val menu = listOf("Play", "Shuffle play", "Unpin from library", "Share").map { label ->
            label to rule.onAllNodes(hasText(label), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        File(dir(), "menu.txt").writeText(menu.joinToString("\n"))
        assertEquals("playlist menu items", menu.map { it.first to true }, menu)
    }

    @Test
    fun libraryLight() {
        launch(light = true)
        click("Library")
        shot("r04-library-light")
    }

    @Test
    fun libraryGrid() {
        launch()
        click("Library")
        click("Show as a grid")
        shot("r05-library-grid")
        click("Show as a list")
    }

    @Test
    fun statistics() {
        importVivi()
        launch(route = Route.Statistics)
        settle(10)
        shot("r10-stats-overview")
        rule.onRoot().performTouchInput { swipeUp() }
        settle()
        shot("r11-stats-overview-2")
        click("Detailed")
        settle(10)
        shot("r12-stats-detailed")
        click("30 days")
        settle(6)
        shot("r13-stats-detailed-30")
    }

    @Test
    fun statisticsLight() {
        importVivi()
        launch(light = true, route = Route.Statistics)
        settle(10)
        shot("r14-stats-light")
    }

    @Test
    fun backup() {
        launch(route = Route.SettingsBackup)
        shot("r20-backup")
    }

    @Test
    fun downloads() {
        launch(route = Route.Downloads)
        shot("r21-downloads")
    }
}
