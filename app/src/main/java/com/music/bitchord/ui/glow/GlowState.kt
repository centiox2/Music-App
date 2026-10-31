package com.music.bitchord.ui.glow

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.playback.PlaybackActions
import com.music.bitchord.playback.PlayerState
import com.music.bitchord.playback.QueueSource
import com.music.bitchord.playback.toggleShuffle
import com.music.bitchord.ui.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce

/** Pages pushed over the three tabs. */
sealed interface Route {
    data class Detail(val browseId: String, val type: BrowseType) : Route
    data class Discography(val browseId: String, val artist: String) : Route
    data class Mood(val title: String) : Route
    data class EditPlaylist(val browseId: String) : Route
    data object Explore : Route
    data object Liked : Route
    data object Downloads : Route
    data object History : Route
    data object Statistics : Route
    data class StatsList(val kind: StatsListKind, val period: Int) : Route
    data object Settings : Route
    data object SettingsTheme : Route
    data object SettingsLyrics : Route
    data object SettingsTranslation : Route
    data object SettingsPlaybackOptions : Route
    data object SettingsPrivacy : Route
    data object SettingsBackup : Route
    data object SettingsLogs : Route
    data class LogView(val path: String, val title: String) : Route
    data object SettingsStorage : Route
    data object SettingsLastFm : Route
    data object SettingsPlayback : Route
    data object Equalizer : Route
    data object JoinSession : Route
    data object ListenTogether : Route
}

enum class PlayerPane { NOW_PLAYING, QUEUE, LYRICS }

/** A confirmation shown above the mini player. */
data class GlowToast(
    val id: Int,
    val message: String,
    val detail: String? = null,
    val artworkUrl: String? = null,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
    val secondaryLabel: String? = null,
    val secondaryAction: (() -> Unit)? = null,
)

/**
 * Everything the screens share: the view model, the player, navigation, and
 * which sheet is up. Provided through [LocalGlow] so a screen deep in the
 * tree can open a page or a song menu without threading callbacks.
 */
@Stable
class GlowState(
    val context: Context,
    val vm: MainViewModel,
    val actions: PlaybackActions,
    val scope: CoroutineScope,
) {
    var controller: MediaController? by mutableStateOf(null)
    var player: PlayerState by mutableStateOf(PlayerState())

    var tab by mutableStateOf(0)

    /** Tablet-wide window: a side rail instead of bottom tabs, two-column player. */
    var wide by mutableStateOf(false)
    val stack = mutableStateListOf<Route>()

    var playerOpen by mutableStateOf(false)
    var lyricsSearchOpen by mutableStateOf(false)
    var lyricsMenuOpen by mutableStateOf(false)
    var lyricsEditorOpen by mutableStateOf(false)

    /** How far the open player has been pulled down, in px; it follows the finger. */
    var playerDrag by androidx.compose.runtime.mutableFloatStateOf(0f)

    /** Pull-to-close: past a third of the way (or flung), the player closes; otherwise it springs back. */
    fun releasePlayerDrag(screenHeight: Float, velocity: Float = 0f) {
        if (playerDrag > screenHeight * 0.22f || velocity > 1800f) {
            playerOpen = false
        } else {
            val from = playerDrag
            scope.launch {
                androidx.compose.animation.core.Animatable(from).animateTo(
                    0f,
                    androidx.compose.animation.core.spring(dampingRatio = 0.75f, stiffness = 400f),
                ) { playerDrag = value }
            }
        }
    }
    var playerPane by mutableStateOf(PlayerPane.NOW_PLAYING)
    var devicesOpen by mutableStateOf(false)
    var signInOpen by mutableStateOf(false)
    var switchChannelOpen by mutableStateOf(false)

    var songMenu by mutableStateOf<Song?>(null)
    var songMenuFromPlayer by mutableStateOf(false)
    var saveToPlaylist by mutableStateOf<List<Song>?>(null)
    var newPlaylistFor by mutableStateOf<List<Song>?>(null)
    var newPlaylistOpen by mutableStateOf(false)
    var sleepTimerOpen by mutableStateOf(false)
    var songDetails by mutableStateOf<Song?>(null)

    var toast by mutableStateOf<GlowToast?>(null)
    private var toastId = 0

    fun notify(
        message: String,
        detail: String? = null,
        artworkUrl: String? = null,
        actionLabel: String? = null,
        action: (() -> Unit)? = null,
        secondaryLabel: String? = null,
        secondaryAction: (() -> Unit)? = null,
    ) {
        toast = GlowToast(++toastId, message, detail, artworkUrl, actionLabel, action, secondaryLabel, secondaryAction)
    }

    // ---- navigation ----

    val top: Route? get() = stack.lastOrNull()

    fun push(route: Route) {
        // Opening the page that's already showing does nothing (and two copies would share saved state).
        if (stack.lastOrNull() == route) return
        com.music.bitchord.data.AppLog.event("Open ${route.logName()}")
        stack.add(route)
    }

    fun selectTab(index: Int) {
        com.music.bitchord.data.AppLog.event("Tab $index")
        if (tab == index) {
            popToRoot()
        } else {
            popToRoot()
            tab = index
        }
    }

    fun popToRoot() {
        while (stack.isNotEmpty()) pop()
    }

    /** Pops one page; false when already at a tab root. */
    fun pop(): Boolean {
        val route = stack.removeLastOrNull() ?: return false
        when (route) {
            is Route.Detail -> vm.closeDetail()
            is Route.Mood -> vm.closeMoodGenre()
            else -> Unit
        }
        return true
    }

    fun openDetail(
        browseId: String,
        title: String,
        subtitle: String = "",
        thumbnailUrl: String? = null,
        type: BrowseType = BrowseType.OTHER,
    ) {
        val resolved = MainViewModel.browseTypeOf(browseId, type)
        vm.openDetail(browseId, title, subtitle, thumbnailUrl, resolved)
        push(Route.Detail(browseId, resolved))
    }

    fun openArtist(browseId: String?, name: String) {
        if (browseId != null) openDetail(browseId, name, "Artist", null, BrowseType.ARTIST)
    }

    fun openAlbumOf(song: Song) {
        val id = song.albumId
        if (id != null) {
            openDetail(id, song.albumName ?: song.title, song.artist, song.thumbnailUrl, BrowseType.ALBUM)
            return
        }
        // Songs from home cards, radio or a restored queue often arrive without their links;
        // the track's own watch entry always names them.
        scope.launch {
            val linked = links(song)
            val albumId = linked?.albumId
            if (albumId != null) {
                openDetail(albumId, linked.albumName ?: song.title, song.artist, song.thumbnailUrl, BrowseType.ALBUM)
            } else {
                notify("This song isn't on an album")
            }
        }
    }

    /** The artist page for [song], looked up when the song didn't carry it. */
    fun openArtistOf(song: Song) {
        song.artistId?.let { return openArtist(it, song.artist) }
        scope.launch {
            val id = links(song)?.artistId
                ?: com.music.bitchord.data.YtMusicRepository.findArtistPageId(song.artist.substringBefore(",").substringBefore(" & ").trim()).getOrNull()
            if (id != null) openArtist(id, song.artist) else notify("Couldn't find ${song.artist}'s page")
        }
    }

    /** Like or unlike, and show it on Liked songs at once rather than after YouTube's next read. */
    fun toggleLike(song: Song) {
        val nowLiked = vm.likeStatuses.value[song.videoId] != com.music.bitchord.data.model.LikeStatus.LIKE
        vm.toggleLike(song.videoId)
        if (nowLiked) LikedSongsCache.liked(song)
    }

    /**
     * Brings the library, playlists and likes up to date with the YouTube
     * account. Pulled down on Library, tapped in Settings, or run on its own
     * when the app comes back after a while (Settings → Account sync).
     */
    fun syncAccount(quiet: Boolean = false) {
        if (!vm.signedIn.value) return
        vm.refresh(com.music.bitchord.ui.MainViewModel.Feed.LIBRARY)
        vm.loadPlaylists()
        GlowPrefs.setLastSync(System.currentTimeMillis())
        scope.launch {
            // The full liked list is many pages; a background sync leaves it for when Library opens.
            if (!quiet || LikedSongsCache.songs.value != null) {
                com.music.bitchord.data.YtMusicRepository.allSongs(com.music.bitchord.data.YtMusicRepository.LIKED_MUSIC).onSuccess {
                    LikedSongsCache.songs.value = it
                    LikedSongsCache.refreshedThisSession = true
                    LikedSongsCache.complete.value = true
                }
            }
            if (!quiet) notify("Library synced with YouTube Music")
        }
    }

    private val linkCache = HashMap<String, Song>()

    private suspend fun links(song: Song): Song? =
        linkCache[song.videoId] ?: com.music.bitchord.data.YtMusicRepository.trackLinks(song.videoId).getOrNull()?.also { linkCache[song.videoId] = it }

    fun openMood(item: MoodGenre) {
        vm.openMoodGenre(item)
        push(Route.Mood(item.title))
    }

    fun openPlaylist(playlist: UserPlaylist) =
        openDetail(playlist.browseId, playlist.title, playlist.subtitle, playlist.thumbnailUrl, BrowseType.PLAYLIST)

    // ---- playback shortcuts ----

    fun source(title: String, type: PlaybackSourceType, id: String? = null) = QueueSource(title, type, id)

    fun playList(songs: List<Song>, index: Int, title: String, type: PlaybackSourceType, id: String? = null, bounded: Boolean = false) =
        actions.playFrom(songs, index, QueueSource(title, type, id), boundedContext = bounded)

    fun playSingle(song: Song, from: String) =
        actions.playRadio(
            song,
            QueueSource(
                from,
                when (from) {
                    "Search" -> PlaybackSourceType.SEARCH
                    "History" -> PlaybackSourceType.HISTORY
                    else -> if (tab == 0 && stack.isEmpty()) PlaybackSourceType.HOME else PlaybackSourceType.QUEUE
                },
            ),
        )

    fun openSongMenu(song: Song, fromPlayer: Boolean = false, playlist: String? = null) {
        com.music.bitchord.data.AppLog.event("Song menu: ${song.title} (${song.videoId})")
        songMenuFromPlayer = fromPlayer
        songMenuPlaylist = playlist
        songMenu = song
        vm.loadSongMenu(song.videoId)
    }

    /** The user's own playlist the open song menu was raised from, so it can offer removal. */
    var songMenuPlaylist by mutableStateOf<String?>(null)

    // ---- multi-select ----

    /** Songs picked by long-press; non-empty means selection mode. */
    val selection = mutableStateListOf<Song>()
    /** The owned playlist the selection was made in, for "Remove from playlist". */
    var selectionPlaylist by mutableStateOf<String?>(null)
    /** Everything selectable on the page the selection started on, for "Select all". */
    var selectionPool by mutableStateOf<List<Song>>(emptyList())

    val selecting get() = selection.isNotEmpty()

    fun startSelection(song: Song, pool: List<Song>, playlist: String?) {
        selection.clear()
        selection.add(song)
        selectionPool = pool
        selectionPlaylist = playlist
    }

    fun toggleSelected(song: Song) {
        val i = selection.indexOfFirst { it.videoId == song.videoId && it.setVideoId == song.setVideoId }
        if (i >= 0) selection.removeAt(i) else selection.add(song)
    }

    fun clearSelection() {
        selection.clear()
        selectionPool = emptyList()
        selectionPlaylist = null
    }

    fun isSelected(song: Song) = selection.any { it.videoId == song.videoId && it.setVideoId == song.setVideoId }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffle() {
        if (actions.refusedByHost()) return
        controller?.toggleShuffle()
    }

    fun share(song: Song) {
        val url = "https://music.youtube.com/watch?v=${song.videoId}"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, song.title)
            putExtra(Intent.EXTRA_TEXT, "${song.title} · ${song.artist}\n$url")
        }
        context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareUrl(title: String, url: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** A route as it reads in the log: its kind, without the data it carries. */
private fun Route.logName(): String = this::class.simpleName ?: toString()

val LocalGlow = staticCompositionLocalOf<GlowState> { error("GlowState not provided") }

/**
 * Every liked song, kept between visits to Liked songs: the page used to start
 * from the library's first 100 every time and only show the true count once
 * the rest had been read again.
 */
object LikedSongsCache {
    val songs = kotlinx.coroutines.flow.MutableStateFlow<List<Song>?>(null)
    val complete = kotlinx.coroutines.flow.MutableStateFlow(false)

    private var started = false

    /** Whether the full list has been read from YouTube since launch (the saved copy may be old). */
    @Volatile var refreshedThisSession = false

    /**
     * Loads the list saved last time and keeps the file up to date. Without it, offline (where
     * the full list can't be read again) Liked songs fell back to the library's first 100.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun init(context: android.content.Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        scope.launch {
            val saved = com.music.bitchord.data.LibrarySnapshot.loadLiked(app)
            if (saved != null && songs.value == null) {
                songs.value = saved
                complete.value = true
            }
            songs.debounce(1_000).collect { list ->
                if (list != null) com.music.bitchord.data.LibrarySnapshot.saveLiked(app, list)
            }
        }
    }

    fun liked(song: Song) {
        songs.value = songs.value?.let { list -> listOf(song) + list.filterNot { it.videoId == song.videoId } }
    }
}

/** The songs a page lists, so a long-press there can "Select all" of them. */
val LocalSelectionPool = androidx.compose.runtime.compositionLocalOf<List<Song>> { emptyList() }

/** Set by a page showing the user's own playlist: selections made there can be removed from it. */
val LocalSelectionPlaylist = androidx.compose.runtime.compositionLocalOf<String?> { null }
