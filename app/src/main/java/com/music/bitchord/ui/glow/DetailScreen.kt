package com.music.bitchord.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.download.DownloadTarget
import com.music.bitchord.download.Downloads

@Composable
fun DetailScreen(route: Route.Detail) {
    val g = LocalGlow.current
    val stack by g.vm.detailStack.collectAsStateWithLifecycle()
    val page = stack.lastOrNull { it.browseId == route.browseId }
    if (page == null) {
        GlowScreen {
            Column {
                TopBar(onBack = { g.pop() })
                SkeletonRows(6, Modifier.padding(top = 20.dp))
            }
        }
        return
    }
    if (page.type == BrowseType.ARTIST || route.type == BrowseType.ARTIST) ArtistPageScreen(page) else ReleasePageScreen(page)
}

@Composable
private fun ReleasePageScreen(page: DetailPage) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val collections by Downloads.collections.collectAsStateWithLifecycle()
    val saved by Downloads.saved.collectAsStateWithLifecycle()
    val owned by vm.playlistOwned.collectAsStateWithLifecycle()
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(page.browseId) { vm.resolvePlaylistOwnership(page.browseId) }

    val songs = (page.songs as? UiState.Success)?.data.orEmpty()
    val shown = if (filter.isBlank()) songs else songs.filter {
        it.title.contains(filter, true) || it.artist.contains(filter, true)
    }
    val totalMs = songs.sumOf { it.durationMillis() }
    val downloaded = songs.isNotEmpty() && (page.browseId in collections || songs.all { it.videoId in saved })
    val isPlaylist = page.type == BrowseType.PLAYLIST || page.type == BrowseType.OTHER
    val isOwned = owned[page.browseId] == true
    val target = DownloadTarget(page.browseId, page.title, page.subtitle, page.thumbnailUrl, playlist = isPlaylist)

    GlowScreen(glowUrl = page.thumbnailUrl) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding()) {
            item {
                if (searching) {
                    Row(
                        Modifier.statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTap(Icons.AutoMirrored.Rounded.ArrowBack, "Close search") { searching = false; filter = "" }
                        SearchField(filter, { filter = it }, "Find in ${page.title}", Modifier.weight(1f))
                    }
                } else {
                    TopBar(onBack = { g.pop() }) {
                        IconTap(Icons.Rounded.Search, "Find in page") { searching = true }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Cover(page.thumbnailUrl, size = 200.dp, shape = RoundedCornerShape(12.dp), px = 720)
                    Spacer(Modifier.height(18.dp))
                    Text(page.title, style = GlowType.display.copy(fontSize = 28.sp), color = c.text, textAlign = TextAlign.Center, maxLines = 2)
                    val artist = songs.firstOrNull()?.artists?.firstOrNull()
                    val subtitleParts = page.subtitle.split(" • ", " · ").map { it.trim() }.filter { it.isNotEmpty() }
                    if (subtitleParts.isNotEmpty()) {
                        Text(
                            subtitleParts.first(),
                            style = GlowType.bodyRegular.copy(fontSize = 17.sp),
                            color = c.text.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = artist?.browseId != null) { g.openArtist(artist?.browseId, artist?.name ?: "") },
                        )
                    }
                    val meta = (subtitleParts.drop(1) + listOfNotNull(
                        songs.size.takeIf { it > 0 }?.let { "$it songs" },
                        totalMs.takeIf { it > 0 }?.let { formatLongDuration(it) },
                    )).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(meta, style = GlowType.secondary.copy(fontSize = 14.sp), color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        GlassCircle(onClick = {
                            if (downloaded) {
                                g.notify("Already downloaded")
                            } else {
                                g.actions.download(songs, target)
                            }
                        }) {
                            Icon(if (downloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download, "Download", tint = c.text, modifier = Modifier.size(22.dp))
                        }
                        val savedToLibrary = page.library?.saved == true
                        if (signedIn && page.library != null && !isOwned) {
                            GlassCircle(onClick = { vm.toggleLibrary(page.browseId) }) {
                                Icon(
                                    if (savedToLibrary) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    if (savedToLibrary) "Remove from library" else "Save to library",
                                    tint = c.text,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        PrimaryCircle(Icons.Rounded.PlayArrow, "Play", size = 60.dp, iconSize = 30.dp, enabled = songs.isNotEmpty()) {
                            g.actions.playDetail(page, songs, 0, vm::takePlaybackContext)
                        }
                        GlassCircle(onClick = {
                            if (songs.isNotEmpty()) g.actions.playDetail(page, songs, songs.indices.random(), vm::takePlaybackContext, shuffle = true)
                        }) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = c.text, modifier = Modifier.size(22.dp)) }
                        GlassCircle(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreHoriz, "More", tint = c.text, modifier = Modifier.size(22.dp))
                        }
                    }
                    if (!page.description.isNullOrBlank()) {
                        Text(
                            page.description!!,
                            style = GlowType.secondary.copy(fontSize = 14.sp),
                            color = c.textSecondary,
                            maxLines = 3,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
            when (val state = page.songs) {
                is UiState.Loading -> item { SkeletonRows(6) }
                is UiState.Error -> item {
                    EmptyState(Icons.Rounded.ErrorOutline, "Couldn't load this page", state.message, iconTint = c.danger)
                }
                is UiState.Success -> itemsIndexed(shown, key = { i, s -> "$i" + s.videoId }) { i, song ->
                    val active = g.player.song?.videoId == song.videoId
                    val index = songs.indexOf(song)
                    SongRow(
                        song.title,
                        listOfNotNull(song.artist.ifBlank { null }, song.durationText).joinToString(" · "),
                        song.thumbnailUrl,
                        active = active,
                        leading = if (page.type == BrowseType.ALBUM) {
                            {
                                Box(Modifier.width(32.dp), contentAlignment = Alignment.CenterStart) {
                                    if (active) {
                                        PlayingBars(playing = g.player.isPlaying)
                                    } else {
                                        Text("${song.trackNumber ?: (index + 1)}", style = GlowType.bodyRegular.copy(fontSize = 16.sp), color = c.textSecondary)
                                    }
                                }
                            }
                        } else {
                            null
                        },
                        onClick = { g.actions.playDetail(page, songs, index, vm::takePlaybackContext) },
                        trailing = {
                            if (song.videoId in saved) {
                                Icon(Icons.Rounded.Check, "Downloaded", tint = c.textTertiary, modifier = Modifier.size(16.dp))
                            }
                            MoreButton { g.openSongMenu(if (page.type == BrowseType.ALBUM) song.copy(albumName = song.albumName ?: page.title, albumId = song.albumId ?: page.browseId) else song) }
                        },
                    )
                }
            }
            if (page.suggestedSongs.isNotEmpty() && isOwned) {
                item { Spacer(Modifier.height(20.dp)); SectionHeader("Suggestions") }
                items(page.suggestedSongs, key = { "sg" + it.videoId }) { song ->
                    SongRow(
                        song.title,
                        song.artist,
                        song.thumbnailUrl,
                        videoId = song.videoId,
                        onClick = { g.playSingle(song, page.title) },
                        trailing = {
                            IconTap(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") { vm.addSuggestedSong(page.browseId, song) }
                        },
                    )
                }
            }
            items(page.sections, key = { "sec" + it.title }) { shelf ->
                Spacer(Modifier.height(22.dp))
                ShelfSection(shelf)
            }
        }
    }

    if (menuOpen) {
        ReleaseMenuSheet(page, songs, isOwned, onDismiss = { menuOpen = false })
    }
}

@Composable
private fun ReleaseMenuSheet(page: DetailPage, songs: List<Song>, owned: Boolean, onDismiss: () -> Unit) {
    val g = LocalGlow.current
    val vm = g.vm
    GlowSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(page.thumbnailUrl, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                GlowText(page.title, GlowType.titleSmall, maxLines = 1)
                GlowText(page.subtitle, GlowType.secondary, color = Glow.colors.textSecondary, maxLines = 1)
            }
        }
        Spacer(Modifier.height(10.dp))
        MenuRow(Icons.Rounded.SkipNext, "Play next") { g.actions.queueSongs(songs, next = true); onDismiss() }
        MenuRow(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { g.actions.queueSongs(songs, next = false); onDismiss() }
        MenuRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Save to playlist") { g.saveToPlaylist = songs; onDismiss() }
        MenuRow(Icons.Rounded.IosShare, "Share") {
            g.shareUrl(page.title, "https://music.youtube.com/" + if (page.browseId.startsWith("VL")) "playlist?list=${page.browseId.removePrefix("VL")}" else "browse/${page.browseId}")
            onDismiss()
        }
        if (owned) {
            MenuRow(Icons.Rounded.Edit, "Edit playlist") { g.push(Route.EditPlaylist(page.browseId)); onDismiss() }
            MenuRow(Icons.Rounded.Delete, "Delete playlist", danger = true) {
                vm.editablePlaylist(page.browseId)?.let { vm.deletePlaylist(it) }
                onDismiss()
                g.pop()
            }
        }
    }
}

// ---- Artist ----

@Composable
private fun ArtistPageScreen(page: DetailPage) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val songs = (page.songs as? UiState.Success)?.data.orEmpty()
    val releases = page.sections.filter { isReleaseShelf(it) }
    val others = page.sections - releases.toSet()

    Box(Modifier.fillMaxSize().background(c.base)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding()) {
            item {
                Box(Modifier.fillMaxWidth().height(380.dp)) {
                    AsyncImage(
                        page.thumbnailUrl.artworkAt(1080),
                        null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, c.base.copy(alpha = 0.6f), c.base), startY = 0f),
                        ),
                    )
                    Row(
                        Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        GlassCircle(background = Color.Black.copy(alpha = 0.35f), onClick = { g.pop() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        GlassCircle(background = Color.Black.copy(alpha = 0.35f), onClick = {
                            g.shareUrl(page.title, "https://music.youtube.com/channel/${page.browseId}")
                        }) { Icon(Icons.Rounded.IosShare, "Share", tint = Color.White, modifier = Modifier.size(20.dp)) }
                    }
                    Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp)) {
                        Text(page.title, style = GlowType.hero.copy(fontSize = 42.sp), color = c.text, maxLines = 2)
                        val listeners = page.monthlyListenerCount ?: page.subscriberCountText
                        if (!listeners.isNullOrBlank()) Text(listeners, style = GlowType.secondary, color = c.textSecondary)
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            page.subscription?.let { sub ->
                                if (signedIn) {
                                    Box(
                                        Modifier
                                            .height(40.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, c.text.copy(alpha = 0.7f), CircleShape)
                                            .background(if (sub.subscribed) c.glass else Color.Transparent)
                                            .clickable { vm.toggleSubscription(page.browseId) }
                                            .padding(horizontal = 18.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(if (sub.subscribed) "Following" else "Follow", style = GlowType.body.copy(fontWeight = FontWeight.SemiBold), color = c.text)
                                    }
                                }
                            }
                            if (songs.isNotEmpty()) {
                                PillButton("Radio", primary = false, icon = Icons.Rounded.Podcasts, height = 40.dp) {
                                    g.actions.startRadio(songs.first())
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            IconTap(Icons.Rounded.Shuffle, "Shuffle", enabled = songs.isNotEmpty()) {
                                g.actions.shuffle(songs, g.source(page.title, PlaybackSourceType.BROWSE, page.browseId))
                            }
                            PrimaryCircle(Icons.Rounded.PlayArrow, "Play", size = 56.dp, iconSize = 28.dp, enabled = songs.isNotEmpty()) {
                                g.playList(songs, 0, page.title, PlaybackSourceType.BROWSE, page.browseId)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            when (val state = page.songs) {
                is UiState.Loading -> item { SkeletonRows(4) }
                is UiState.Error -> if (page.sections.isEmpty()) {
                    item { EmptyState(Icons.Rounded.ErrorOutline, "Couldn't load this artist", state.message, iconTint = c.danger) }
                }
                is UiState.Success -> if (songs.isNotEmpty()) {
                    item { SectionHeader("Top songs") }
                    itemsIndexed(songs.take(5), key = { _, s -> "top" + s.videoId }) { i, song ->
                        TrackRow(
                            song,
                            subtitle = song.albumName ?: song.artist,
                            onClick = { g.playList(songs, i, page.title, PlaybackSourceType.BROWSE, page.browseId) },
                            onMore = { g.openSongMenu(song) },
                            active = g.player.song?.videoId == song.videoId,
                        )
                    }
                }
            }
            if (releases.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader("Albums & singles", action = "See all") { g.push(Route.Discography(page.browseId, page.title)) }
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(releases.flatMap { it.items }.distinctBy { it.browseId ?: it.title }, key = { it.browseId ?: it.title }) { item ->
                            ShelfCard(item, 120.dp) { g.openShelfItem(item, page.title) }
                        }
                    }
                }
            }
            items(others, key = { "o" + it.title }) { shelf ->
                Spacer(Modifier.height(22.dp))
                ShelfSection(shelf, 130.dp)
            }
            if (!page.description.isNullOrBlank()) {
                item {
                    Spacer(Modifier.height(22.dp))
                    AboutCard(page)
                }
            }
        }
    }
}

private fun isReleaseShelf(shelf: HomeShelf) =
    listOf("album", "single", "ep").any { shelf.title.contains(it, ignoreCase = true) }

@Composable
private fun AboutCard(page: DetailPage) {
    val c = Glow.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp)) {
        GlowText("About", GlowType.title, Modifier.padding(bottom = 10.dp))
        GlassCard {
            Column(Modifier.padding(16.dp)) {
                Text(
                    page.description.orEmpty(),
                    style = GlowType.bodyRegular,
                    color = c.text.copy(alpha = 0.85f),
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    if (expanded) "Less" else "More",
                    style = GlowType.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.text,
                    modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(6.dp)).clickable { expanded = !expanded }.padding(vertical = 4.dp),
                )
                val stats = listOfNotNull(
                    page.subscriberCountText?.let { "Followers" to it },
                    page.monthlyListenerCount?.let { "Monthly listeners" to it },
                )
                if (stats.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        stats.forEach { (label, value) ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.glass).padding(12.dp),
                            ) {
                                Text(label, style = GlowType.caption.copy(fontSize = 12.sp), color = c.textSecondary)
                                Text(value.substringBefore(" subscribers").substringBefore(" monthly"), style = GlowType.body.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp), color = c.text, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiscographyScreen(route: Route.Discography) {
    val g = LocalGlow.current
    val stack by g.vm.detailStack.collectAsStateWithLifecycle()
    val page = stack.lastOrNull { it.browseId == route.browseId }
    val sections = page?.sections.orEmpty()
    val tabs = buildList {
        sections.firstOrNull { it.title.contains("album", true) }?.let { add("Albums" to it) }
        sections.firstOrNull { it.title.contains("single", true) || it.title.contains("ep", true) && !it.title.contains("album", true) }?.let { add("Singles & EPs" to it) }
        sections.firstOrNull { it.title.contains("video", true) }?.let { add("Videos" to it) }
    }.ifEmpty { sections.take(3).map { it.title to it } }
    var tab by rememberSaveable { mutableStateOf(0) }

    GlowScreen(glowUrl = page?.thumbnailUrl) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding()) {
            item {
                TopBar(onBack = { g.pop() }, title = route.artist)
                if (tabs.size > 1) {
                    Segmented(tabs.map { it.first }, tab.coerceAtMost(tabs.lastIndex), Modifier.padding(horizontal = 20.dp)) { tab = it }
                }
                Spacer(Modifier.height(12.dp))
            }
            val shelf = tabs.getOrNull(tab.coerceAtMost(tabs.lastIndex))?.second
            items(shelf?.items.orEmpty(), key = { "d" + (it.browseId ?: it.videoId ?: it.title) }) { item ->
                SongRow(
                    item.title,
                    item.subtitle,
                    item.thumbnailUrl,
                    coverSize = 60.dp,
                    onClick = { g.openShelfItem(item, route.artist) },
                    trailing = { ChevronRight(); Spacer(Modifier.width(8.dp)) },
                )
            }
            if (page != null && !page.description.isNullOrBlank()) {
                item { Spacer(Modifier.height(20.dp)); AboutCard(page) }
            }
        }
    }
}

/** A row in a menu sheet: icon, label, optional trailing value. */
@Composable
fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = Glow.colors
    val tint = if (danger) c.danger else c.text
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, style = GlowType.bodyRegular.copy(fontSize = 17.sp), color = tint.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = GlowType.secondary.copy(fontSize = 14.sp), color = c.textSecondary)
    }
}
