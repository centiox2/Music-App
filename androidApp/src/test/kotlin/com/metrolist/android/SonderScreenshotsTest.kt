package com.metrolist.android

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.metrolist.shared.model.*
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.player.*
import com.metrolist.shared.viewmodels.PlayerViewModel
import com.metrolist.shared.ui.components.images.AsyncImage
import androidx.compose.ui.focus.FocusRequester
import com.metrolist.shared.ui.components.AlbumHeader
import com.metrolist.shared.ui.screens.*
import com.metrolist.shared.ui.settings.*
import com.metrolist.shared.ui.sonder.*
import com.metrolist.shared.ui.theme.MetrolistTheme
import com.russhwolf.settings.MapSettings
import org.junit.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.junit.runner.RunWith
import java.io.File

/** Render the same presenters shipped by Android with deterministic local artwork and data. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class SonderScreenshotsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var songs: List<SongItem>
    private lateinit var album: AlbumItem
    @Before fun setup() {
        Settings.replaceSharedForTest(Settings(MapSettings()))
        Settings.shared.animatedGradient = false
        val art = File(compose.activity.cacheDir, "sonder-fixture.png")
        val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xff254b54.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xffd7bc90.toInt(); canvas.drawCircle(430f, 230f, 150f, paint)
        paint.color = 0xff18313b.toInt(); canvas.drawRect(0f, 400f, 640f, 640f, paint)
        art.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val artists = listOf(ArtistTiny("artist", "Luna Vale"))
        songs = listOf("After Hours", "Soft Light", "Stay Awhile", "Blue Skies", "Moonlit", "Far Away", "Breathe", "Home Again")
            .mapIndexed { i, title -> SongItem("song$i", title, art.toURI().toString(), artists, null, duration = 205) }
        album = AlbumItem("album", "Quiet Evenings", art.toURI().toString(), artists, year = "2026", songCount = 8)
    }
    @After fun teardown() { Settings.replaceSharedForTest(null) }
    private fun render(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent { MetrolistTheme(darkTheme = dark) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { content() }
        } }
        compose.waitForIdle()
        // Coil completes outside Compose's test clock. Allow its local file requests to finish.
        Thread.sleep(1000)
        compose.mainClock.advanceTimeBy(1000)
        val file = File("build/reports/sonder-screenshots/$name.png").apply { parentFile.mkdirs() }
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun homeDark() = home(true)
    @Test fun homeLight() = home(false)
    private fun home(dark: Boolean) = render("home-${if (dark) "dark" else "light"}", dark) {
        val text = HomeContentText("Moods & genres", "For you", "New releases", "Similar to", "Podcasts", "Charts", "Albums", "Made for you", "Recap", "Shuffle", "Play all", "Keep listening", "Quick picks", "Favorites", "Loading", "Try again", "Unavailable")
        SonderHome(listOf(HomeShelf("featured", "Made for you", listOf(album), HomeShelfStyle.CAROUSEL), HomeShelf("quick", "Quick picks", songs, HomeShelfStyle.SONG_LIST)),
            text, {}, {}, {}, remember { mutableStateOf(null) }, { true }, rememberLazyListState(), PaddingValues(bottom = 12.dp), Modifier,
            null, false, { item { SonderTopBar("Home"); Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { SonderPill("For you", true) {}; SonderPill("Relax", false) {}; SonderPill("Focus", false) {} } } })
    }
    @Test fun album() = render("album-dark") {
        Column { SonderTopBar("Album", {}); AlbumHeader(album, songs, false, onArtistClick = {}, description = "A little room to slow down.", showMoreLabel = "More", showLessLabel = "Less"); songs.take(3).forEach { SonderMediaRow(it, {}, {}) } }
    }
    @Test fun artist() = render("artist-dark") {
        ArtistScreen(artist = ArtistItem("artist", "Luna Vale", album.thumbnail, banner = album.thumbnail, monthlyListeners = "1.2M monthly listeners"), artistId = "artist", sections = mapOf("Songs" to songs), isLoading = false, errorMessage = null,
            onRefresh = {}, onItemClick = {}, onItemLongClick = {}, onBackClick = {}, onPlayAllClick = {}, onShuffleClick = {}, onMixClick = {}, showSubscribe = true)
    }
    @Test fun settings() = settings(null)
    @Test fun appearance() = settings(SettingsPageId.APPEARANCE)
    @Test fun playback() = settings(SettingsPageId.PLAYER)
    @Test fun lyrics() = settings(SettingsPageId.LYRICS)
    @Test fun privacy() = settings(SettingsPageId.PRIVACY)
    @Test fun content() = settings(SettingsPageId.CONTENT)
    private fun settings(page: SettingsPageId?) = render("settings-${page?.name?.lowercase() ?: "home"}") {
        SharedSettingsScreen(SharedSettingsHost(platform = SettingsPlatform.ANDROID, onClose = {}, initialPage = page, singlePane = true))
    }
    @Test fun player() = playerScreen()
    @Test @Config(qualifiers = "w844dp-h390dp-xhdpi-land") fun playerLandscape() = playerScreen("player-landscape")
    private fun playerScreen(name: String = "player-dark") = render(name) {
        val song = songs.first()
        SonderPlayerPresentation(
            PlayerViewModel.PlayerUiState(currentSong = PlayerViewModel.SongUiModel(song.id, song.title, song.artists, song.thumbnail, song.duration), isPlaying = true, duration = 205000, queueTitle = "Quiet evenings"),
            SonderPlayerPane.COVER, {}, false, remember { FocusRequester() }, Modifier, {}, {},
            artwork = { AsyncImage(song.thumbnail, it) }, lyrics = {}, queue = {},
            timeline = { SonderSeekBar(68000, 205000, true, {}) },
        )
    }
    @Test fun rowsAndControls() = render("rows-and-controls") {
        Column { SonderTopBar("Your library", {}); Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { SonderPill("Songs", true) {}; SonderPill("Albums", false) {}; SonderPill("Playlists", false) {} }; songs.forEach { SonderMediaRow(it, {}, {}) }; SonderGroup(Modifier.padding(20.dp)) { Text("Playback", Modifier.padding(16.dp)); Row(Modifier.padding(16.dp)) { Text("Autoplay", Modifier.weight(1f)); com.metrolist.shared.ui.components.ExpressiveSwitch(true, {}) } } }
    }
}
