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
    data object Settings : Route
    data object SettingsTheme : Route
    data object SettingsLyrics : Route
    data object SettingsStorage : Route
    data object SettingsLastFm : Route
    data object SettingsPlayback : Route
    data object Equalizer : Route
    data object JoinSession : Route
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
    val stack = mutableStateListOf<Route>()

    var playerOpen by mutableStateOf(false)
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
        stack.add(route)
    }

    fun selectTab(index: Int) {
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
        val id = song.albumId ?: return
        openDetail(id, song.albumName ?: song.title, song.artist, song.thumbnailUrl, BrowseType.ALBUM)
    }

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

    fun openSongMenu(song: Song, fromPlayer: Boolean = false) {
        songMenuFromPlayer = fromPlayer
        songMenu = song
        vm.loadSongMenu(song.videoId)
    }

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

val LocalGlow = staticCompositionLocalOf<GlowState> { error("GlowState not provided") }
