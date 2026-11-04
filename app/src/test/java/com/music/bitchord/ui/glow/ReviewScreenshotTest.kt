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

    private var song = Song(
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
        // Robolectric never delivers the startup network callback; say what the test means.
        setOnline(true)
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
        // Toggles, and the pins outlive a test in-process, so only pin what isn't pinned yet.
        listOf("VLPL2", "VLPL1", "VLPL0").forEach { if (!GlowPrefs.isPinned(it)) GlowPrefs.togglePinned(it) }
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

    // ---- 1.9.1 review ----

    @Suppress("UNCHECKED_CAST")
    private fun fakeDownloads(vararg ids: String) {
        val f = com.music.bitchord.download.Downloads::class.java.getDeclaredField("_saved").apply { isAccessible = true }
        (f.get(com.music.bitchord.download.Downloads) as MutableStateFlow<Map<String, String>>).value =
            ids.associateWith { "file:///nowhere/$it.webm" }
    }

    @Suppress("UNCHECKED_CAST")
    private fun setOnline(online: Boolean) {
        val f = com.music.bitchord.data.Connectivity::class.java.getDeclaredField("_online").apply { isAccessible = true }
        (f.get(com.music.bitchord.data.Connectivity) as MutableStateFlow<Boolean>).value = online
    }

    private fun vm() = ViewModelProvider(rule.activity)[MainViewModel::class.java]

    /** A result row, never the search field that holds the same words. */
    private fun clickResult(text: String, substring: Boolean = false) {
        val matcher = hasText(text, substring = substring) and !androidx.compose.ui.test.hasSetTextAction()
        rule.waitUntil(30_000) { rule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(matcher, useUnmergedTree = true)[0].performClick()
        settle()
    }

    private fun openArtistBySearch(query: String, name: String, shotName: String) {
        click("Search")
        vm().searchFor(query)
        settle(14)
        clickResult(name)
        settle(16)
        shot(shotName)
    }

    @Test
    fun artistLongName() {
        launch()
        openArtistBySearch("PARTYNEXTDOOR", "PARTYNEXTDOOR", "v01-artist-partynextdoor")
    }

    @Test
    fun artistTwoWords() {
        launch()
        openArtistBySearch("Brent Faiyaz", "Brent Faiyaz", "v02-artist-brent")
    }

    @Test
    fun albumPage() {
        launch()
        click("Search")
        vm().searchFor("If You're Reading This It's Too Late Drake album")
        settle(14)
        clickResult("If You're Reading This It's Too Late", substring = true)
        settle(18)
        shot("v03-playlist-page")
    }

    @Test
    fun realAlbumPage() {
        // YouTube gives this test server no album data, so the page is given one: the real
        // cover and track names of If You're Reading This It's Too Late.
        val id = "MPREb_eGx1fAyRxNl"
        val cover = "https://yt3.googleusercontent.com/SlmTZt3dmf2H4G92zV34XKiQACYdDKwve16hSMK6UZ63Gas7WGYYoKq6M83KL_MXhA2r8HYLwp4OqZI=w544-h544-l90-rj"
        val drake = com.music.bitchord.data.model.ArtistRef("Drake", "UCByOQJjav0CUDwxCk-jVNRQ")
        val tracks = listOf("Legend" to "4:01", "Energy" to "3:01", "10 Bands" to "2:57", "Know Yourself" to "4:35", "No Tellin'" to "5:10", "Madonna" to "2:57", "6 God" to "3:00", "Star67" to "4:54")
            .mapIndexed { i, (t, d) -> Song(videoId = "iyrtitl$i", title = t, artist = "Drake", thumbnailUrl = cover, durationText = d, artists = listOf(drake), albumId = id) }
        fakeDownloads("iyrtitl1", "iyrtitl3")
        val page = com.music.bitchord.data.model.DetailPage(
            browseId = id,
            title = "If You're Reading This It's Too Late",
            subtitle = "Drake • Album • 2015",
            thumbnailUrl = cover,
            songs = UiState.Success(tracks),
            type = com.music.bitchord.data.model.BrowseType.ALBUM,
        )
        launch(route = Route.Detail(id, com.music.bitchord.data.model.BrowseType.ALBUM), signedIn = false)
        @Suppress("UNCHECKED_CAST")
        val stack = MainViewModel::class.java.getDeclaredField("_detailStack").apply { isAccessible = true }.get(vm()) as MutableStateFlow<List<com.music.bitchord.data.model.DetailPage>>
        rule.runOnUiThread { stack.value = listOf(page) }
        settle(16)
        shot("v03-album")
        rule.onRoot().performTouchInput { swipeUp() }
        settle(6)
        shot("v04-album-tracks")
    }

    @Test
    fun listenTogether() {
        launch(route = Route.ListenTogether)
        shot("v05-listen-together")
    }

    @Test
    fun playerTwoArtists() {
        song = Song(
            videoId = "fHI8X4OXluQ",
            title = "Mob Ties",
            artist = "Drake, Future",
            thumbnailUrl = "https://i.ytimg.com/vi/fHI8X4OXluQ/hqdefault.jpg",
            durationText = "3:24",
            artists = listOf(
                com.music.bitchord.data.model.ArtistRef("Drake", "UCByOQJjav0CUDwxCk-jVNRQ"),
                com.music.bitchord.data.model.ArtistRef("Future", "UCSDvKdIQOwTfcyOimSi9oYA"),
            ),
        )
        launch()
        click("Mob Ties")
        settle(6)
        shot("v06-player-two-artists")
    }

    @Test
    fun likedWithMarks() {
        launch()
        fakeDownloads("id1", "id2", "id5")
        click("Library")
        click("Liked songs")
        settle(6)
        shot("v07-liked-marks")
    }

    @Test
    fun libraryMenusAndOffline() {
        launch()
        setOnline(false)
        click("Library")
        settle(4)
        shot("v08-library-offline")
        rule.onAllNodes(hasContentDescription("More options"))[0].performClick()
        settle()
        val items = listOf("Play", "Shuffle play", "Play next", "Add to queue", "Save to playlist", "Download", "Unpin from library", "Share", "Remove from library")
        val present = items.map { it to rule.onAllNodes(hasText(it), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        File(dir(), "menu-v2.txt").writeText(present.joinToString("\n"))
        setOnline(true)
    }

    @Test
    fun searchDiagnostic() {
        launch()
        click("Search")
        val out = StringBuilder()
        vm().searchFor("drake")
        repeat(10) { n ->
            settle(4)
            val results = vm().results.value
            val chips = rule.onAllNodes(hasText("Songs"), useUnmergedTree = true).fetchSemanticsNodes().size
            out.append("t=${n * 2}s results=${results?.let { it::class.simpleName }} ${if (results is UiState.Error) (results as UiState.Error).message else ""} songsChip=$chips query=${vm().query.value}\n")
        }
        File(dir(), "search-diag.txt").writeText(out.toString())
        shot("v09-search-results")
    }
}
