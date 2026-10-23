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
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
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
fun LibraryScreen() {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(signedIn) { if (signedIn) vm.onLibraryShown() }
    val page = (library as? UiState.Success)?.data

    GlowScreen(glowUrl = g.player.song?.thumbnailUrl) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding()) {
            item {
                PageHeader("Library") {
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
                downloadsContent(g, inline = true)
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
                    0 -> playlistsTab(g, state.data.shelves, state.data.likedSongs.size)
                    1 -> songsTab(g, state.data.librarySongs)
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
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.glass)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (accent) c.miniPlayer else c.primary),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (accent) c.text else c.onPrimary, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column {
            GlowText(title, GlowType.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            GlowText(subtitle, GlowType.secondary, color = c.textSecondary, maxLines = 1)
        }
    }
}

private enum class PlaylistSort(val label: String) { RECENT("Recently added"), AZ("A–Z") }

private fun LazyListScope.playlistsTab(g: GlowState, shelves: List<HomeShelf>, likedCount: Int) {
    item {
        val saved by Downloads.saved.collectAsStateWithLifecycle()
        var sort by rememberSaveable { mutableStateOf(PlaylistSort.RECENT) }
        var grid by rememberSaveable { mutableStateOf(false) }
        val c = Glow.colors
        val playlists = shelves.firstOrNull { it.title == YtMusicRepository.PLAYLISTS_SHELF }?.items.orEmpty()
            .filterNot { it.browseId == YtMusicRepository.LIKED_MUSIC }
        val sorted = if (sort == PlaylistSort.AZ) playlists.sortedBy { it.title.lowercase() } else playlists
        Column {
            Row(Modifier.padding(start = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        sort = if (sort == PlaylistSort.RECENT) PlaylistSort.AZ else PlaylistSort.RECENT
                    }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(sort.label, style = GlowType.bodyRegular, color = c.textSecondary)
                }
                Spacer(Modifier.weight(1f))
                IconTap(Icons.Rounded.Add, "New playlist") { g.newPlaylistOpen = true }
                IconTap(if (grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView, "Change layout") { grid = !grid }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CollectionCard("Liked songs", if (likedCount > 0) "$likedCount+ songs" else "Songs you like", Icons.Rounded.Favorite, Modifier.weight(1f)) {
                    g.push(Route.Liked)
                }
                CollectionCard("Downloads", "${saved.size} songs", Icons.Rounded.Download, Modifier.weight(1f), accent = true) {
                    g.push(Route.Downloads)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (sorted.isEmpty()) {
                Text(
                    "Playlists you make or save show up here.",
                    style = GlowType.secondary,
                    color = c.textSecondary,
                    modifier = Modifier.padding(20.dp),
                )
            }
            if (grid) {
                sorted.chunked(2).forEach { pair ->
                    Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { item ->
                            Column(Modifier.weight(1f).clickable { g.openShelfItem(item, "Library") }) {
                                SquareCover(item.thumbnailUrl)
                                Spacer(Modifier.height(8.dp))
                                GlowText(item.title, GlowType.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                                GlowText(item.subtitle, GlowType.secondary, color = c.textSecondary, maxLines = 1)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                sorted.forEach { item ->
                    SongRow(
                        item.title,
                        listOf("Playlist", item.subtitle).filter { it.isNotBlank() }.joinToString(" · "),
                        item.thumbnailUrl,
                        coverSize = 52.dp,
                        onClick = { g.openShelfItem(item, "Library") },
                    )
                }
            }
        }
    }
}

private enum class SongSort(val label: String) { RECENT("Recently added"), TITLE("Title A–Z"), ARTIST("Artist A–Z") }

private fun LazyListScope.songsTab(g: GlowState, songs: List<Song>) {
    item {
        var sort by rememberSaveable { mutableStateOf(SongSort.TITLE) }
        val c = Glow.colors
        val sorted = when (sort) {
            SongSort.RECENT -> songs
            SongSort.TITLE -> songs.sortedBy { it.title.lowercase() }
            SongSort.ARTIST -> songs.sortedBy { it.artist.lowercase() }
        }
        Column {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${songs.size} songs", style = GlowType.bodyRegular, color = c.textSecondary, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        sort = SongSort.entries[(sort.ordinal + 1) % SongSort.entries.size]
                    }.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(sort.label, style = GlowType.bodyRegular, color = c.textSecondary)
                }
            }
            if (songs.isEmpty()) {
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
                sorted.forEachIndexed { i, song ->
                    TrackRow(
                        song,
                        onClick = { g.playList(sorted, i, "Library", PlaybackSourceType.BROWSE) },
                        onMore = { g.openSongMenu(song) },
                        active = g.player.song?.videoId == song.videoId,
                    )
                }
            }
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
    val all by produceState(firstPage, firstPage) {
        YtMusicRepository.allSongs(YtMusicRepository.LIKED_MUSIC).onSuccess { value = it }
    }
    val songs = all.filter { likes[it.videoId] != LikeStatus.INDIFFERENT && likes[it.videoId] != LikeStatus.DISLIKE }
    var sortRecent by rememberSaveable { mutableStateOf(true) }
    val sorted = if (sortRecent) songs else songs.sortedBy { it.title.lowercase() }
    val totalMs = songs.sumOf { it.durationMillis() }
    val allDownloaded = songs.isNotEmpty() && songs.all { it.videoId in saved }

    GlowScreen(glowUrl = songs.firstOrNull()?.thumbnailUrl) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding()) {
            item {
                TopBar(onBack = { g.pop() })
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(120.dp).clip(RoundedCornerShape(14.dp)).background(c.primary),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Favorite, null, tint = c.onPrimary, modifier = Modifier.size(48.dp)) }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Liked songs", style = GlowType.display, color = c.text)
                        Text(
                            "${songs.size} songs" + if (totalMs > 0) " · ${formatLongDuration(totalMs)}" else "",
                            style = GlowType.bodyRegular,
                            color = c.textSecondary,
                        )
                        if (allDownloaded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Check, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Downloaded", style = GlowType.secondary, color = c.textSecondary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(c.glass).clickable { sortRecent = !sortRecent }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, null, tint = c.text, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (sortRecent) "Recently liked" else "Title A–Z", style = GlowType.body, color = c.text)
                    }
                    Spacer(Modifier.weight(1f))
                    IconTap(Icons.Rounded.Download, "Download all") {
                        g.actions.download(songs)
                    }
                    IconTap(Icons.Rounded.Shuffle, "Shuffle") {
                        g.actions.shuffle(sorted, g.source("Liked songs", PlaybackSourceType.BROWSE, YtMusicRepository.LIKED_MUSIC))
                    }
                    Spacer(Modifier.width(8.dp))
                    PrimaryCircle(Icons.Rounded.PlayArrow, "Play", size = 52.dp, enabled = sorted.isNotEmpty()) {
                        g.playList(sorted, 0, "Liked songs", PlaybackSourceType.BROWSE, YtMusicRepository.LIKED_MUSIC)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (songs.isEmpty() && library is UiState.Loading) item { SkeletonRows(6) }
            itemsIndexed(sorted, key = { _, s -> s.videoId }) { i, song ->
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
    GlowScreen(glowUrl = g.player.song?.thumbnailUrl) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding()) {
            item {
                TopBar(onBack = { g.pop() })
                Text("Downloads", style = GlowType.display.copy(fontSize = 34.sp), color = Glow.colors.text, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(14.dp))
            }
            downloadsContent(g, inline = false)
        }
    }
}

private fun LazyListScope.downloadsContent(g: GlowState, inline: Boolean) {
    item(key = "downloads") {
        val context = LocalContext.current
        val c = Glow.colors
        val scope = rememberCoroutineScope()
        val saved by Downloads.saved.collectAsStateWithLifecycle()
        val session by DownloadSession.state.collectAsStateWithLifecycle()
        val songs by produceState(emptyList<Song>(), saved) { value = Downloads.getDownloadedSongs(context) }
        val sizes by produceState(emptyMap<String, Long>(), saved) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                saved.mapValues { (_, uri) -> fileSize(context, uri) }
            }
        }
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
            Column(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.glass).padding(14.dp),
            ) {
                Row {
                    Text("${songs.size} songs · ${totalBytes.formatBytes()}", style = GlowType.body.copy(fontWeight = FontWeight.SemiBold), color = c.text, modifier = Modifier.weight(1f))
                    Text("${free.formatBytes()} free", style = GlowType.secondary, color = c.textSecondary)
                }
                Spacer(Modifier.height(12.dp))
                val share = if (free + totalBytes > 0) totalBytes.toFloat() / (free + totalBytes) else 0f
                ThinProgress(share.coerceAtLeast(0.01f), height = 6.dp)
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
                songs.forEachIndexed { i, song ->
                    SongRow(
                        song.title,
                        "✓ ${song.artist}" + (sizes[song.videoId]?.takeIf { it > 0 }?.let { " · ${it.formatBytes()}" } ?: ""),
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
        }
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
