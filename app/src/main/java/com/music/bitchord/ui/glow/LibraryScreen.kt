package com.music.bitchord.ui.glow

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.PlayHistory
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.download.DownloadProgress
import com.music.bitchord.download.DownloadSession
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.MainViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val LIBRARY_TABS = listOf("Playlists", "Songs", "Albums", "Artists", "Downloads")

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun LibraryScreen() {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(signedIn) {
        if (!signedIn) return@LaunchedEffect
        vm.ensureLibrary()
        vm.onLibraryShown()
        // The full liked list, read once in the background, so its count is right before Liked songs is opened.
        if (!LikedSongsCache.refreshedThisSession) {
            YtMusicRepository.allSongs(YtMusicRepository.LIKED_MUSIC).onSuccess {
                LikedSongsCache.refreshedThisSession = true
                LikedSongsCache.songs.value = it
                LikedSongsCache.complete.value = true
            }
        }
    }
    val likedAll by LikedSongsCache.songs.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val page = (library as? UiState.Success)?.data
    var songSort by rememberSaveable { mutableStateOf(SongSort.TITLE) }
    // Read only while the Downloads tab is open.
    val downloaded = if (tab == 4) rememberDownloadedSongs() else DownloadedSongs(emptyList(), emptyMap())
    var playlistFilter by rememberSaveable { mutableStateOf(0) }
    var playlistMenu by remember { mutableStateOf<ShelfItem?>(null) }
    val playlistsView = rememberPlaylistsView(page?.shelves.orEmpty(), playlistFilter)
    val playlistGrid by GlowPrefs.playlistGrid.collectAsStateWithLifecycle()
    val librarySongs = page?.librarySongs.orEmpty()
    // Sorted once per change, not on every recomposition of the page.
    val sortedLibrarySongs = remember(librarySongs, songSort) {
        when (songSort) {
            SongSort.RECENT -> librarySongs
            SongSort.TITLE -> librarySongs.sortedBy { it.title.lowercase() }
            SongSort.ARTIST -> librarySongs.sortedBy { it.artist.lowercase() }
        }
    }

    GlowScreen(glowUrl = g.player.song?.thumbnailUrl) {
        // Pull down to sync the library with the YouTube account.
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = MainViewModel.Feed.LIBRARY in refreshing,
            onRefresh = { g.syncAccount() },
            modifier = Modifier.fillMaxSize(),
        ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding()) {
            item {
                PageHeader("Library") {
                    GlassCircle(onClick = { g.push(Route.Statistics) }) {
                        Icon(Icons.Rounded.BarChart, "Statistics", tint = c.text, modifier = Modifier.size(21.dp))
                    }
                    GlassCircle(onClick = { g.push(Route.History) }) {
                        Icon(Icons.Rounded.History, "History", tint = c.text, modifier = Modifier.size(21.dp))
                    }
                    GlassCircle(onClick = { g.push(Route.Settings) }) {
                        Icon(Icons.Rounded.Settings, "Settings", tint = c.text, modifier = Modifier.size(21.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                ChipRow(LIBRARY_TABS, tab) { tab = it }
                Spacer(Modifier.height(10.dp))
            }
            if (tab == 4) {
                downloadsContent(g, inline = true, downloaded)
                return@LazyColumn
            }
            if (!signedIn) {
                item { SignedOutLibrary() }
                return@LazyColumn
            }
            when (val state = library) {
                is UiState.Loading -> item { SkeletonRows(7, Modifier.padding(top = 12.dp)) }
                is UiState.Error -> item {
                    EmptyState(Icons.Rounded.LibraryMusic, "Couldn't load your library", state.message) {
                        PillButton("Try again", onClick = { vm.loadLibrary() })
                    }
                }
                is UiState.Success -> when (tab) {
                    0 -> playlistsTab(
                        g,
                        playlistsView,
                        likedAll?.size ?: state.data.likedSongs.size,
                        likedExact = likedAll != null,
                        filter = playlistFilter,
                        grid = playlistGrid,
                        onFilter = { playlistFilter = it },
                        onMenu = { playlistMenu = it },
                    )
                    1 -> songsTab(g, sortedLibrarySongs, songSort) { songSort = it }
                    2 -> albumsTab(g, state.data.shelves.firstOrNull { it.title == "Albums" }?.items.orEmpty())
                    else -> artistsTab(
                        g,
                        state.data.shelves.filter { it.title == "Artists" || it.title == "Subscriptions" }
                            .flatMap { it.items }
                            .distinctBy { it.browseId ?: it.title },
                    )
                }
            }
            if (page == null && library !is UiState.Loading && library !is UiState.Error) {
                item { SkeletonRows(5) }
            }
        }
        }
    }
    playlistMenu?.let { item -> PlaylistMenuSheet(item) { playlistMenu = null } }
}

@Composable
private fun SignedOutLibrary() {
    val g = LocalGlow.current
    Column {
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CollectionCard("Downloads", "On this phone", Icons.Rounded.Download, Modifier.weight(1f), accent = true) {
                g.push(Route.Downloads)
            }
            CollectionCard("History", "On this phone", Icons.Rounded.History, Modifier.weight(1f), accent = true) {
                g.push(Route.History)
            }
        }
        EmptyState(
            Icons.Rounded.LibraryMusic,
            "Your library lives in your account",
            "Sign in with YouTube Music to see your playlists, liked songs, albums and artists here.",
            modifier = Modifier.padding(top = 30.dp),
        ) {
            PillButton("Sign in", onClick = { g.signInOpen = true })
        }
    }
}

/**
 * Liked songs and Downloads, side by side at the top of Playlists: an icon tile above the name
 * and count, so neither name is ever cut short.
 */
@Composable
private fun CollectionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Glow.colors
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier
            .pressScale(interaction, 0.97f)
            .clip(RoundedCornerShape(14.dp))
            .background(c.glass)
            .clickable(interaction, androidx.compose.material3.ripple(), onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(if (accent) c.miniPlayer else c.primary),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (accent) c.text else c.onPrimary, modifier = Modifier.size(20.dp)) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            GlowText(title, GlowType.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            GlowText(subtitle, GlowType.secondary.copy(fontSize = 12.sp), color = c.textSecondary, maxLines = 1)
        }
    }
}

private enum class PlaylistSort(val label: String) {
    RECENT("Recently added"),
    AZ("Name A–Z"),
    ZA("Name Z–A"),
    DOWNLOADED("Downloaded first"),
}

private val PLAYLIST_FILTERS = listOf("All", "Pinned", "Offline")

/** What the Playlists tab shows, worked out once per change at the top of the page. */
private class PlaylistsView(
    val all: List<ShelfItem>,
    val pinned: List<ShelfItem>,
    val rest: List<ShelfItem>,
    val pinnedIds: List<String>,
    val downloaded: (ShelfItem) -> Boolean,
    val covers: Map<String, String>,
)

@Composable
private fun rememberPlaylistsView(shelves: List<HomeShelf>, filter: Int): PlaylistsView {
    val saved by Downloads.saved.collectAsStateWithLifecycle()
    val collections by Downloads.collections.collectAsStateWithLifecycle()
    val sortIndex by GlowPrefs.playlistSort.collectAsStateWithLifecycle()
    val pinnedIds by GlowPrefs.pinnedPlaylists.collectAsStateWithLifecycle()
    val covers by com.music.bitchord.data.PlaylistCovers.local.collectAsStateWithLifecycle()
    return remember(shelves, filter, saved, collections, sortIndex, pinnedIds, covers) {
        val sort = PlaylistSort.entries.getOrElse(sortIndex) { PlaylistSort.RECENT }
        val playlists = shelves.firstOrNull { it.title == YtMusicRepository.PLAYLISTS_SHELF }?.items.orEmpty()
            .filterNot { it.browseId == YtMusicRepository.LIKED_MUSIC }
            .distinctBy { it.browseId ?: it.title }
        val isDownloaded = { item: ShelfItem -> isFullyDownloaded(item.browseId, collections, saved) }
        val ordered = when (sort) {
            PlaylistSort.RECENT -> playlists
            PlaylistSort.AZ -> playlists.sortedBy { it.title.lowercase() }
            PlaylistSort.ZA -> playlists.sortedByDescending { it.title.lowercase() }
            PlaylistSort.DOWNLOADED -> playlists.sortedByDescending(isDownloaded)
        }
        val shown = when (filter) {
            1 -> ordered.filter { it.browseId in pinnedIds }
            2 -> ordered.filter(isDownloaded)
            else -> ordered
        }
        val pinned = shown.filter { it.browseId in pinnedIds }
            .sortedBy { item -> pinnedIds.indexOf(item.browseId) }
        PlaylistsView(
            all = playlists,
            pinned = pinned,
            rest = shown.filterNot { it.browseId in pinnedIds },
            pinnedIds = pinnedIds,
            downloaded = isDownloaded,
            covers = covers,
        )
    }
}

/**
 * The Playlists tab, after the "Refined list" design: Liked songs and Downloads cards, one
 * toolbar (filter, sort, new playlist, grid/list), then pinned playlists and the rest, each
 * group under its own heading. Pinning is done from a playlist's ⋯ menu; there is no
 * long-press. Sort and layout are kept in [GlowPrefs], so a choice lasts.
 */
private fun LazyListScope.playlistsTab(
    g: GlowState,
    view: PlaylistsView,
    likedCount: Int,
    likedExact: Boolean,
    filter: Int,
    grid: Boolean,
    onFilter: (Int) -> Unit,
    onMenu: (ShelfItem) -> Unit,
) {
    item(key = "playlists-top") {
        val saved by Downloads.saved.collectAsStateWithLifecycle()
        val sortIndex by GlowPrefs.playlistSort.collectAsStateWithLifecycle()
        val sort = PlaylistSort.entries.getOrElse(sortIndex) { PlaylistSort.RECENT }
        Column {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CollectionCard(
                    "Liked songs",
                    if (likedCount > 0) (if (likedExact) "$likedCount songs" else "$likedCount+ songs") else "Songs you like",
                    Icons.Rounded.Favorite,
                    Modifier.weight(1f),
                ) { g.push(Route.Liked) }
                CollectionCard("Downloads", "${saved.size} songs", Icons.Rounded.Download, Modifier.weight(1f), accent = true) {
                    g.push(Route.Downloads)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(start = 20.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                CompactSegmented(PLAYLIST_FILTERS, filter, onFilter)
                Spacer(Modifier.weight(1f))
                SortMenu(PlaylistSort.entries, sort, label = { it.label }, iconOnly = true) { GlowPrefs.setPlaylistSort(it.ordinal) }
                IconTap(Icons.Rounded.Add, "New playlist", size = 40.dp, iconSize = 22.dp) { g.newPlaylistOpen = true }
                IconTap(
                    if (grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                    if (grid) "Show as a list" else "Show as a grid",
                    size = 40.dp,
                    iconSize = 20.dp,
                ) { GlowPrefs.setPlaylistGrid(!grid) }
            }
            if (view.pinned.isEmpty() && view.rest.isEmpty()) {
                Text(
                    when (filter) {
                        1 -> "Pin a playlist from its ⋯ menu to keep it at the top."
                        2 -> "Playlists downloaded in full show up here."
                        else -> "Playlists you make or save show up here."
                    },
                    style = GlowType.secondary,
                    color = Glow.colors.textSecondary,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
    if (view.pinned.isNotEmpty()) {
        item(key = "pinned-head") { GroupHeading("Pinned", pin = true, top = 16.dp) }
        playlistGroup(g, "pin", view.pinned, view, grid, onMenu)
    }
    if (view.rest.isNotEmpty()) {
        item(key = "rest-head") {
            GroupHeading(
                when (filter) {
                    2 -> "Offline · ${view.rest.size + view.pinned.size}"
                    else -> "All playlists · ${view.all.size}"
                },
                top = if (view.pinned.isNotEmpty()) 14.dp else 16.dp,
            )
        }
        playlistGroup(g, "all", view.rest, view, grid, onMenu)
    }
}

/** One group of playlists, as rows or as a two-column grid; one lazy item per row. */
private fun LazyListScope.playlistGroup(g: GlowState, prefix: String, items: List<ShelfItem>, view: PlaylistsView, grid: Boolean, onMenu: (ShelfItem) -> Unit) {
    if (grid) {
        items.chunked(2).forEachIndexed { i, pair ->
            item(key = "$prefix-g$i-${pair.first().browseId}") {
                Row(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { item -> PlaylistTile(g, item, view, Modifier.weight(1f)) { onMenu(item) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        itemsIndexed(items, key = { i, item -> "$prefix-$i-${item.browseId ?: item.title}" }) { _, item ->
            PlaylistRow(g, item, view) { onMenu(item) }
        }
    }
}

/** A small caps heading over a group: "PINNED", "ALL PLAYLISTS · 24". */
@Composable
private fun GroupHeading(text: String, pin: Boolean = false, top: androidx.compose.ui.unit.Dp) {
    val c = Glow.colors
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = top, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pin) {
            Icon(Icons.Rounded.PushPin, null, tint = c.textSecondary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text.uppercase(),
            style = GlowType.section.copy(fontSize = 12.sp, letterSpacing = 0.06.em),
            color = c.textSecondary,
            maxLines = 1,
        )
    }
}

/** A playlist row: cover, name, "Playlist · 44 songs" (with a mark when it's offline), ⋯. */
@Composable
private fun PlaylistRow(g: GlowState, item: ShelfItem, view: PlaylistsView, onMenu: () -> Unit) {
    val c = Glow.colors
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pressScale(interaction, 0.98f)
            .clickable(interaction, androidx.compose.material3.ripple()) { g.openShelfItem(item, "Library") }
            .padding(start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(com.music.bitchord.data.PlaylistCovers.coverFor(item, view.covers), size = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            GlowText(item.title, GlowType.body, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (view.downloaded(item)) {
                    Icon(Icons.Rounded.Download, "Downloaded", tint = c.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                }
                GlowText(
                    listOf("Playlist", item.subtitle).filter { it.isNotBlank() }.joinToString(" · "),
                    GlowType.secondary,
                    color = c.textSecondary,
                    maxLines = 1,
                )
            }
        }
        IconTap(Icons.Rounded.MoreHoriz, "More options", tint = c.textSecondary, size = 40.dp, iconSize = 20.dp, onClick = onMenu)
    }
}

/** The grid form of [PlaylistRow]. */
@Composable
private fun PlaylistTile(g: GlowState, item: ShelfItem, view: PlaylistsView, modifier: Modifier, onMenu: () -> Unit) {
    val c = Glow.colors
    Column(modifier.clip(RoundedCornerShape(10.dp)).clickable { g.openShelfItem(item, "Library") }) {
        SquareCover(com.music.bitchord.data.PlaylistCovers.coverFor(item, view.covers), radius = 10.dp, downloaded = view.downloaded(item))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.browseId in view.pinnedIds) PinMark()
                    GlowText(item.title, GlowType.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                }
                GlowText(item.subtitle, GlowType.secondary, color = c.textSecondary, maxLines = 1)
            }
            IconTap(Icons.Rounded.MoreHoriz, "More options", tint = c.textSecondary, size = 36.dp, iconSize = 20.dp, onClick = onMenu)
        }
    }
}

/** The small segmented filter in the Library toolbar: All · Pinned · Offline. */
@Composable
private fun CompactSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Glow.colors
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(c.glass).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by androidx.compose.animation.animateColorAsState(if (on) c.primary else Color.Transparent, label = "segment")
            Box(
                Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(horizontal = if (on) 12.dp else 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = GlowType.label.copy(fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (on) c.onPrimary else c.text.copy(alpha = 0.85f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** A playlist's ⋯ menu in the Library: play, shuffle, pin, share, delete. */
@Composable
private fun PlaylistMenuSheet(item: ShelfItem, onDismiss: () -> Unit) {
    val g = LocalGlow.current
    val c = Glow.colors
    val pinnedIds by GlowPrefs.pinnedPlaylists.collectAsStateWithLifecycle()
    val covers by com.music.bitchord.data.PlaylistCovers.local.collectAsStateWithLifecycle()
    val id = item.browseId
    val owned = g.vm.editablePlaylist(id)
    GlowSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(com.music.bitchord.data.PlaylistCovers.coverFor(item, covers), size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                GlowText(item.title, GlowType.titleSmall, maxLines = 1)
                GlowText(listOf("Playlist", item.subtitle).filter { it.isNotBlank() }.joinToString(" · "), GlowType.secondary, color = c.textSecondary, maxLines = 1)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (id != null) {
            MenuRow(Icons.Rounded.PlayArrow, "Play") { g.playBrowse(id, item.title); onDismiss() }
            MenuRow(Icons.Rounded.Shuffle, "Shuffle play") { g.playBrowse(id, item.title, shuffle = true); onDismiss() }
            val isPinned = id in pinnedIds
            MenuRow(Icons.Rounded.PushPin, if (isPinned) "Unpin from library" else "Pin to library") {
                GlowPrefs.togglePinned(id)
                g.notify(if (isPinned) "Unpinned ${item.title}" else "Pinned ${item.title}")
                onDismiss()
            }
            MenuRow(Icons.Rounded.IosShare, "Share") {
                g.shareUrl(item.title, "https://music.youtube.com/" + if (id.startsWith("VL")) "playlist?list=${id.removePrefix("VL")}" else "browse/$id")
                onDismiss()
            }
        }
        if (owned != null) {
            MenuRow(Icons.Rounded.Edit, "Edit playlist") { id?.let { g.push(Route.EditPlaylist(it)) }; onDismiss() }
            MenuRow(Icons.Rounded.Delete, "Delete playlist", danger = true) {
                g.vm.deletePlaylist(owned)
                onDismiss()
            }
        }
    }
}

@Composable
private fun PinMark() {
    Icon(
        Icons.Rounded.PushPin,
        "Pinned",
        tint = Glow.colors.textSecondary,
        modifier = Modifier.padding(end = 4.dp).size(15.dp),
    )
}

private enum class SongSort(val label: String) { RECENT("Recently added"), TITLE("Title A–Z"), ARTIST("Artist A–Z") }

/**
 * One list item per song, so only the rows on screen are built; the whole library used to
 * be composed as a single item, every row at once. The sort lives with the caller, since a
 * lazy list's builder can't hold state.
 */
private fun LazyListScope.songsTab(g: GlowState, sorted: List<Song>, sort: SongSort, onSort: (SongSort) -> Unit) {
    item(key = "songs-header") {
        val c = Glow.colors
        Column {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${sorted.size} songs", style = GlowType.bodyRegular, color = c.textSecondary, modifier = Modifier.weight(1f))
                SortMenu(SongSort.entries, sort, label = { it.label }, onSelect = onSort)
            }
            if (sorted.isEmpty()) {
                Text("Songs you add to your library show up here.", style = GlowType.secondary, color = c.textSecondary, modifier = Modifier.padding(20.dp))
            } else {
                SongRow(
                    "Shuffle all",
                    "",
                    null,
                    leading = {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Shuffle, null, tint = c.onPrimary, modifier = Modifier.size(22.dp))
                        }
                    },
                    onClick = { g.actions.shuffle(sorted, g.source("Library", PlaybackSourceType.BROWSE)) },
                )
            }
        }
    }
    itemsIndexed(sorted, key = { i, song -> "song-$i-${song.videoId}" }) { i, song ->
        androidx.compose.runtime.CompositionLocalProvider(LocalSelectionPool provides sorted) {
            TrackRow(
                song,
                onClick = { g.playList(sorted, i, "Library", PlaybackSourceType.BROWSE) },
                onMore = { g.openSongMenu(song) },
                active = g.player.song?.videoId == song.videoId,
            )
        }
    }
}

private fun LazyListScope.albumsTab(g: GlowState, albums: List<ShelfItem>) {
    item {
        Text(
            "${albums.size} albums",
            style = GlowType.bodyRegular,
            color = Glow.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (albums.isEmpty()) {
            Text("Albums you save show up here.", style = GlowType.secondary, color = Glow.colors.textSecondary, modifier = Modifier.padding(20.dp))
        }
    }
    albums.chunked(2).forEachIndexed { i, pair ->
        item(key = "al$i") {
            Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { item ->
                    Column(
                        Modifier.weight(1f).clickable {
                            item.browseId?.let { g.openDetail(it, item.title, item.subtitle, item.thumbnailUrl, BrowseType.ALBUM) }
                        },
                    ) {
                        SquareCover(item.thumbnailUrl)
                        Spacer(Modifier.height(8.dp))
                        GlowText(item.title, GlowType.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                        GlowText(item.subtitle, GlowType.secondary, color = Glow.colors.textSecondary, maxLines = 1)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun LazyListScope.artistsTab(g: GlowState, artists: List<ShelfItem>) {
    item {
        Text(
            "${artists.size} artists · following",
            style = GlowType.bodyRegular,
            color = Glow.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (artists.isEmpty()) {
            Text("Artists you follow show up here.", style = GlowType.secondary, color = Glow.colors.textSecondary, modifier = Modifier.padding(20.dp))
        }
    }
    items(artists, key = { "ar" + (it.browseId ?: it.title) }) { item ->
        SongRow(
            item.title,
            item.subtitle,
            item.thumbnailUrl,
            coverSize = 52.dp,
            circleCover = true,
            onClick = { g.openArtist(item.browseId, item.title) },
            trailing = { ChevronRight(); Spacer(Modifier.width(8.dp)) },
        )
    }
}

// ---- Liked songs ----

@Composable
fun LikedSongsScreen() {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val library by vm.library.collectAsStateWithLifecycle()
    val likes by vm.likeStatuses.collectAsStateWithLifecycle()
    val saved by Downloads.saved.collectAsStateWithLifecycle()
    val firstPage = (library as? UiState.Success)?.data?.likedSongs.orEmpty()
    val cached by LikedSongsCache.songs.collectAsStateWithLifecycle()
    val complete by LikedSongsCache.complete.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        // Refreshed each visit, but what was read last time shows meanwhile.
        YtMusicRepository.allSongs(YtMusicRepository.LIKED_MUSIC).onSuccess {
            LikedSongsCache.songs.value = it
            LikedSongsCache.refreshedThisSession = true
            LikedSongsCache.complete.value = true
        }
    }
    val all = cached ?: firstPage
    val songs = all.filter { likes[it.videoId] != LikeStatus.INDIFFERENT && likes[it.videoId] != LikeStatus.DISLIKE }
    var order by rememberSaveable { mutableStateOf(SongOrder.DEFAULT) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("") }
    val sorted = remember(songs, order, filter) { order.apply(songs).matching(filter) }
    val totalMs = songs.sumOf { it.durationMillis() }
    val allDownloaded = songs.isNotEmpty() && songs.all { it.videoId in saved }

    androidx.compose.runtime.CompositionLocalProvider(LocalSelectionPool provides sorted, LocalShowLikedMark provides false) {
    // The page's cover is the white heart tile, so it glows with that, not the first song's art.
    GlowScreen(glowColor = c.primary) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding()) {
            item {
                if (searching) {
                    Row(
                        Modifier.statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTap(Icons.AutoMirrored.Rounded.ArrowBack, "Close search") { searching = false; filter = "" }
                        SearchField(filter, { filter = it }, "Find in Liked songs", Modifier.weight(1f))
                    }
                } else {
                    TopBar(onBack = { g.pop() }) {
                        IconTap(Icons.Rounded.Search, "Find in Liked songs") { searching = true }
                    }
                }
                CollectionHeader(
                    cover = {
                        Box(
                            Modifier.size(120.dp).clip(RoundedCornerShape(14.dp)).background(c.primary),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Favorite, null, tint = c.onPrimary, modifier = Modifier.size(48.dp)) }
                    },
                    title = "Liked songs",
                    lines = listOf(
                        // Until every page is read, the count is a floor, and says so.
                        (if (complete || cached != null) "${songs.size} songs" else "${songs.size}+ songs") +
                            if (totalMs > 0 && (complete || cached != null)) " · ${formatLongDuration(totalMs)}" else "",
                    ),
                    downloaded = allDownloaded,
                    sort = {
                        SortMenu(SongOrder.entries, order, label = { if (it == SongOrder.DEFAULT) "Recently liked" else it.label }) { order = it }
                    },
                ) {
                    if (!allDownloaded) {
                        IconTap(Icons.Rounded.Download, "Download all") { g.actions.download(songs) }
                    }
                    IconTap(Icons.Rounded.Shuffle, "Shuffle") {
                        g.actions.shuffle(sorted, g.source("Liked songs", PlaybackSourceType.BROWSE, YtMusicRepository.LIKED_MUSIC))
                    }
                    Spacer(Modifier.width(8.dp))
                    PrimaryCircle(Icons.Rounded.PlayArrow, "Play", size = 52.dp, enabled = sorted.isNotEmpty()) {
                        g.playList(sorted, 0, "Liked songs", PlaybackSourceType.BROWSE, YtMusicRepository.LIKED_MUSIC)
                    }
                }
            }
            if (filter.isNotBlank() && sorted.isEmpty() && songs.isNotEmpty()) item {
                Text("No liked songs match \"$filter\"", style = GlowType.secondary, color = c.textSecondary, modifier = Modifier.padding(20.dp))
            }
            if (songs.isEmpty() && library is UiState.Loading) item { SkeletonRows(6) }
            // The position is part of the key: a song liked twice (it happens) must not crash the list.
            itemsIndexed(sorted, key = { i, s -> "$i-${s.videoId}" }) { i, song ->
                TrackRow(
                    song,
                    onClick = { g.playList(sorted, i, "Liked songs", PlaybackSourceType.BROWSE, YtMusicRepository.LIKED_MUSIC) },
                    onMore = { g.openSongMenu(song) },
                    active = g.player.song?.videoId == song.videoId,
                )
            }
        }
    }
    }
}

fun formatLongDuration(ms: Long): String {
    val minutes = ms / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "$h hr $m min" else "$m min"
}

// ---- Downloads ----

@Composable
fun DownloadsScreen() {
    val g = LocalGlow.current
    var selecting by remember { mutableStateOf(false) }
    val downloaded = rememberDownloadedSongs()
    GlowScreen(glowUrl = g.player.song?.thumbnailUrl) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding()) {
            item {
                TopBar(onBack = { g.pop() })
                Text("Downloads", style = GlowType.display.copy(fontSize = 34.sp), color = Glow.colors.text, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(14.dp))
            }
            downloadsContent(g, inline = false, downloaded)
        }
    }
}

/** The downloaded songs and each one's size on disk, read again whenever the downloads change. */
private class DownloadedSongs(val songs: List<Song>, val sizes: Map<String, Long>)

@Composable
private fun rememberDownloadedSongs(): DownloadedSongs {
    val context = LocalContext.current
    val saved by Downloads.saved.collectAsStateWithLifecycle()
    val songs by produceState(emptyList<Song>(), saved) { value = Downloads.getDownloadedSongs(context) }
    val sizes by produceState(emptyMap<String, Long>(), saved) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            saved.mapValues { (_, uri) -> fileSize(context, uri) }
        }
    }
    return remember(songs, sizes) { DownloadedSongs(songs, sizes) }
}

/**
 * The downloads page. The summary and the songs in progress are one item; each downloaded
 * song is an item of its own, so only the rows on screen are built.
 */
private fun LazyListScope.downloadsContent(g: GlowState, inline: Boolean, downloaded: DownloadedSongs) {
    val songs = downloaded.songs
    val sizes = downloaded.sizes
    item(key = "downloads") {
        val c = Glow.colors
        val scope = rememberCoroutineScope()
        val session by DownloadSession.state.collectAsStateWithLifecycle()
        val pending = session.items.filter { !it.progress.settled }
        val totalBytes = sizes.values.sum()
        val free = remember { runCatching { StatFs(Environment.getDataDirectory().path).availableBytes }.getOrDefault(0L) }

        if (songs.isEmpty() && pending.isEmpty()) {
            EmptyState(
                Icons.Rounded.Download,
                "No downloads yet",
                "Download songs, albums or playlists to listen without internet. Look for the download button.",
                modifier = Modifier.padding(top = if (inline) 20.dp else 70.dp),
            ) {
                val library by g.vm.library.collectAsStateWithLifecycle()
                val liked = (library as? UiState.Success)?.data?.likedSongs.orEmpty()
                if (liked.isNotEmpty()) {
                    PillButton("Download liked songs", height = 48.dp, onClick = {
                        scope.launch {
                            val all = YtMusicRepository.allSongs(YtMusicRepository.LIKED_MUSIC).getOrNull() ?: liked
                            g.actions.download(all)
                        }
                    })
                    Spacer(Modifier.height(16.dp))
                }
                Text(
                    "Find something to download",
                    style = GlowType.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.text,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { g.popToRoot(); g.selectTab(1) }.padding(8.dp),
                )
            }
            return@item
        }

        Column {
            // Storage summary.
            var confirmDeleteAll by remember { mutableStateOf(false) }
            Column(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.glass).padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${songs.size} songs · ${totalBytes.formatBytes()}", style = GlowType.body.copy(fontWeight = FontWeight.SemiBold), color = c.text, modifier = Modifier.weight(1f))
                    Text("${free.formatBytes()} free", style = GlowType.secondary, color = c.textSecondary)
                }
                Spacer(Modifier.height(12.dp))
                val share = if (free + totalBytes > 0) totalBytes.toFloat() / (free + totalBytes) else 0f
                ThinProgress(share.coerceAtLeast(0.01f), height = 6.dp)
                Spacer(Modifier.height(12.dp))
                PillButton(
                    "Delete all downloads",
                    Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Delete,
                    height = 44.dp,
                    danger = true,
                ) { confirmDeleteAll = true }
            }
            if (confirmDeleteAll) {
                GlowDialog({ confirmDeleteAll = false }) {
                    Text("Delete all downloads?", style = GlowType.title, color = c.text)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${songs.size} songs (${totalBytes.formatBytes()}) will be removed from this phone" +
                            (if (pending.isNotEmpty()) ", and ${pending.size} downloads in progress will stop" else "") +
                            ". You won't be able to play them offline until you download them again. This can't be undone.",
                        style = GlowType.bodyRegular,
                        color = c.textSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    PillButton("Delete all", Modifier.fillMaxWidth(), height = 48.dp, danger = true) {
                        confirmDeleteAll = false
                        scope.launch {
                            val removed = Downloads.deleteAll(g.context)
                            g.notify(if (removed == 1) "Deleted 1 download" else "Deleted $removed downloads")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    PillButton("Cancel", Modifier.fillMaxWidth(), primary = false, height = 48.dp) { confirmDeleteAll = false }
                }
            }
            if (pending.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionHeader("Downloading · ${pending.size}", action = "Cancel all") {
                    pending.forEach { Downloads.cancel(it.videoId) }
                }
                pending.forEach { item ->
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(item.song.thumbnailUrl, size = 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                GlowText(item.song.title, GlowType.body, Modifier.weight(1f), maxLines = 1)
                                val label = when (val p = item.progress) {
                                    is DownloadProgress.Running -> "${(p.fraction * 100).toInt()}%"
                                    DownloadProgress.Queued -> "Waiting"
                                    else -> ""
                                }
                                Text(label, style = GlowType.secondary, color = c.textSecondary)
                            }
                            Spacer(Modifier.height(8.dp))
                            ThinProgress((item.progress as? DownloadProgress.Running)?.fraction ?: 0f)
                        }
                        IconTap(Icons.Rounded.Close, "Cancel", tint = c.textSecondary) { Downloads.cancel(item.videoId) }
                    }
                }
            }
            if (songs.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionHeader("Downloaded", action = "Shuffle all") {
                    g.actions.shuffle(songs, g.source("Downloads", PlaybackSourceType.QUEUE))
                }
            }
        }
    }
    itemsIndexed(songs, key = { i, song -> "dl-$i-${song.videoId}" }) { i, song ->
        val c = Glow.colors
        SongRow(
            song.title,
            song.artist + (sizes[song.videoId]?.takeIf { it > 0 }?.let { " · ${it.formatBytes()}" } ?: ""),
            song.thumbnailUrl,
            active = g.player.song?.videoId == song.videoId,
            onClick = { g.playList(songs, i, "Downloads", PlaybackSourceType.QUEUE) },
            trailing = {
                IconTap(Icons.Rounded.Delete, "Delete download", tint = c.textSecondary) {
                    g.actions.removeDownload(song.videoId)
                }
            },
        )
    }
}

private fun fileSize(context: android.content.Context, uri: String): Long = runCatching {
    val parsed = android.net.Uri.parse(uri)
    if (parsed.scheme == "file" || parsed.scheme == null) {
        java.io.File(parsed.path ?: uri).length()
    } else {
        context.contentResolver.openFileDescriptor(parsed, "r")?.use { it.statSize } ?: 0L
    }
}.getOrDefault(0L)

// ---- History ----

@Composable
fun HistoryScreen() {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val local by PlayHistory.entries.collectAsStateWithLifecycle()
    val account by vm.history.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(tab) { if (tab == 1) vm.loadHistory() }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE d MMM") }

    GlowScreen(glowUrl = local.firstOrNull()?.song?.thumbnailUrl) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding()) {
            item {
                TopBar(onBack = { g.pop() }) {
                    if (tab == 0 && local.isNotEmpty()) TextAction("Clear all", Modifier.padding(end = 12.dp)) { PlayHistory.clear() }
                }
                Text("History", style = GlowType.display.copy(fontSize = 34.sp), color = c.text, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(14.dp))
                Segmented(listOf("On this phone", "Account"), tab, Modifier.padding(horizontal = 20.dp)) { tab = it }
                Spacer(Modifier.height(10.dp))
            }
            if (tab == 0) {
                if (local.isEmpty()) {
                    item { EmptyState(Icons.Rounded.History, "Nothing played yet", "Songs you play on this phone show up here.") }
                }
                val today = LocalDate.now()
                local.groupBy { Instant.ofEpochMilli(it.playedAtMs).atZone(ZoneId.systemDefault()).toLocalDate() }
                    .forEach { (day, entries) ->
                        item(key = "d$day") {
                            Text(
                                when (day) {
                                    today -> "Today"
                                    today.minusDays(1) -> "Yesterday"
                                    else -> day.format(dateFmt)
                                },
                                style = GlowType.section.copy(fontSize = 14.sp),
                                color = c.textSecondary,
                                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 4.dp),
                            )
                        }
                        items(entries, key = { "e" + it.playedAtMs + it.song.videoId }) { e ->
                            TrackRow(
                                e.song,
                                onClick = { g.playSingle(e.song, "History") },
                                onMore = { g.openSongMenu(e.song) },
                                trailing = {
                                    Text(
                                        Instant.ofEpochMilli(e.playedAtMs).atZone(ZoneId.systemDefault()).format(timeFmt),
                                        style = GlowType.secondary,
                                        color = c.textSecondary,
                                    )
                                    MoreButton { g.openSongMenu(e.song) }
                                },
                            )
                        }
                    }
            } else if (!signedIn) {
                item {
                    EmptyState(Icons.Rounded.History, "Sign in for account history", "Your YouTube Music history from every device shows here once you sign in.") {
                        PillButton("Sign in", onClick = { g.signInOpen = true })
                    }
                }
            } else {
                when (val state = account) {
                    is UiState.Loading -> item { SkeletonRows(6) }
                    is UiState.Error -> item { EmptyState(Icons.Rounded.History, "Couldn't load history", state.message) }
                    is UiState.Success -> itemsIndexed(state.data, key = { i, s -> "a$i" + s.videoId }) { i, song ->
                        TrackRow(
                            song,
                            onClick = { g.playList(state.data, i, "History", PlaybackSourceType.HISTORY) },
                            onMore = { g.openSongMenu(song) },
                        )
                    }
                }
            }
        }
    }
}
