package com.music.bitchord.ui.glow

import androidx.compose.foundation.background
import androidx.compose.ui.layout.layout
import com.music.bitchord.data.model.ShelfItem
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
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
    var order by androidx.compose.runtime.saveable.rememberSaveable(page.browseId) { mutableStateOf(SongOrder.DEFAULT) }
    androidx.compose.runtime.LaunchedEffect(page.browseId) { vm.resolvePlaylistOwnership(page.browseId) }

    val songs = (page.songs as? UiState.Success)?.data.orEmpty()
    val isAlbum = page.type == BrowseType.ALBUM
    // An album keeps its track order; a playlist can be sorted.
    val ordered = remember(songs, order, isAlbum) { if (isAlbum) songs else order.apply(songs) }
    val shown = ordered.matching(filter)
    val totalMs = songs.sumOf { it.durationMillis() }
    // Fully downloaded only when every track on the page is on disk, not merely asked for.
    val downloaded = songs.isNotEmpty() && isFullyDownloaded(page.browseId, collections, saved, songs)
    val isPlaylist = page.type == BrowseType.PLAYLIST || page.type == BrowseType.OTHER
    val isOwned = owned[page.browseId] == true
    val target = DownloadTarget(page.browseId, page.title, page.subtitle, page.thumbnailUrl, playlist = isPlaylist)

    androidx.compose.runtime.CompositionLocalProvider(
        LocalSelectionPool provides songs,
        LocalSelectionPlaylist provides page.browseId.takeIf { isOwned },
    ) {
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
                if (!isAlbum) {
                    PlaylistHeader(page, songs, ordered, order, { order = it }, downloaded, isOwned, signedIn, target, totalMs) { menuOpen = true }
                } else Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        Cover(page.thumbnailUrl, size = 200.dp, shape = RoundedCornerShape(12.dp), px = 720)
                        if (downloaded) DownloadedBadge(Modifier.align(Alignment.TopEnd).padding(10.dp), size = 28.dp)
                    }
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
                    val meta = (subtitleWithoutCounts(subtitleParts.drop(1).joinToString(" • ")) + listOfNotNull(
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
                            Icon(if (downloaded) Icons.Rounded.DownloadForOffline else Icons.Rounded.Download, if (downloaded) "Downloaded" else "Download", tint = c.text, modifier = Modifier.size(22.dp))
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
                    val index = ordered.indexOf(song)
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
                        onClick = { playOrdered(g, page, songs, ordered, order, index) },
                        song = song,
                        trailing = {
                            if (song.videoId in saved) {
                                DownloadedMark()
                            }
                            MoreButton {
                                g.openSongMenu(
                                    if (page.type == BrowseType.ALBUM) song.copy(albumName = song.albumName ?: page.title, albumId = song.albumId ?: page.browseId) else song,
                                    playlist = page.browseId.takeIf { isOwned },
                                )
                            }
                        },
                    )
                }
            }
            if (page.suggestedSongs.isNotEmpty() && isOwned) {
                item { Spacer(Modifier.height(20.dp)); SectionHeader("Suggestions") }
                items(page.suggestedSongs.distinctBy { "sg" + it.videoId }, key = { "sg" + it.videoId }) { song ->
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
            items(page.sections.distinctBy { "sec" + it.title }, key = { "sec" + it.title }) { shelf ->
                Spacer(Modifier.height(22.dp))
                ShelfSection(shelf)
            }
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
        if (page.type != BrowseType.ALBUM) {
            val pinned by GlowPrefs.pinnedPlaylists.collectAsStateWithLifecycle()
            val isPinned = page.browseId in pinned
            MenuRow(Icons.Rounded.PushPin, if (isPinned) "Unpin from library" else "Pin to library") {
                GlowPrefs.togglePinned(page.browseId)
                g.notify(if (isPinned) "Unpinned" else "Pinned to the top of your library")
                onDismiss()
            }
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

/**
 * The artist page, after the Cover Glow artist design: a full-bleed photo
 * fading into a colour taken from it, the name in condensed caps, five round
 * actions, the latest release, top songs in swipeable pages of four, then
 * albums, singles, fans-also-like and the biography.
 */
@Composable
private fun ArtistPageScreen(page: DetailPage) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    // Top songs come as the page's song list, or for some artists only as a shelf of tracks.
    val songShelf = page.sections.firstOrNull { s -> s.items.isNotEmpty() && s.items.count { it.videoId != null } >= s.items.size / 2 && s.title.contains("song", true) }
    val songs = (page.songs as? UiState.Success)?.data.orEmpty().ifEmpty { songShelf?.items.orEmpty().mapNotNull { it.asSong() } }
    val shelves = page.sections - setOfNotNull(songShelf)
    val albums = shelves.firstOrNull { it.title.contains("album", true) }
    val singles = shelves.firstOrNull { it.title.contains("single", true) || it.title.contains(" ep", true) }
    val fans = shelves.firstOrNull { s -> s.title.contains("fans", true) || (s.items.isNotEmpty() && s.items.all { it.browseId?.startsWith("UC") == true }) }
    val others = shelves - setOfNotNull(albums, singles, fans)
    val latest = latestRelease(albums?.items.orEmpty(), singles?.items.orEmpty())

    // The page wears the artist photo's colour, darkened, as the design does.
    val palette = rememberGlowPalette(page.thumbnailUrl)
    val tintTarget = palette?.dominant?.let { artistTint(it, c.isDark) } ?: c.base
    val tint by androidx.compose.animation.animateColorAsState(tintTarget, tween(600), label = "artistTint")
    val onTint = if (c.isDark || tint.luminance() < 0.4f) Color.White else Color(0xFF111111)
    val dim = onTint.copy(alpha = 0.65f)
    val source = g.source(page.title, PlaybackSourceType.BROWSE, page.browseId)

    Box(Modifier.fillMaxSize().background(tint)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding()) {
            item(key = "hero") {
                Box(Modifier.fillMaxWidth().height(470.dp)) {
                    ArtImage(page.thumbnailUrl, 1080, Modifier.fillMaxSize())
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(250.dp).background(
                            Brush.verticalGradient(0f to Color.Transparent, 0.55f to tint.copy(alpha = 0.75f), 1f to tint),
                        ),
                    )
                }
                Column(
                    Modifier.fillMaxWidth().overlapAbove(140.dp).padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        page.title.uppercase(),
                        style = TextStyle(fontFamily = ArchivoCondensed, fontWeight = FontWeight.Black, fontSize = 50.sp, lineHeight = 47.sp, letterSpacing = (-0.5).sp, textAlign = TextAlign.Center),
                        color = onTint,
                        maxLines = 3,
                    )
                    val listeners = page.monthlyListenerCount ?: page.subscriberCountText
                    if (!listeners.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(listeners, style = GlowType.secondary, color = dim)
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        val following = page.subscription?.subscribed == true
                        ArtistAction(if (following) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (following) "Unfollow" else "Follow artist", onTint, enabled = signedIn && page.subscription != null) {
                            vm.toggleSubscription(page.browseId)
                        }
                        ArtistAction(Icons.Rounded.Shuffle, "Shuffle play", onTint, enabled = songs.isNotEmpty()) { g.actions.shuffle(songs, source) }
                        PrimaryCircle(Icons.Rounded.PlayArrow, "Play ${page.title}", size = 60.dp, iconSize = 28.dp, enabled = songs.isNotEmpty()) {
                            g.playList(songs, 0, page.title, PlaybackSourceType.BROWSE, page.browseId)
                        }
                        ArtistAction(Icons.Rounded.Podcasts, "Start artist radio", onTint, enabled = songs.isNotEmpty()) { g.actions.startRadio(songs.first()) }
                        ArtistAction(Icons.Rounded.IosShare, "Share artist", onTint) {
                            g.shareUrl(page.title, "https://music.youtube.com/channel/${page.browseId}")
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            if (latest != null) {
                item(key = "latest") {
                    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    Row(
                        Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .pressScale(interaction, 0.98f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(onTint.copy(alpha = 0.08f))
                            .clickable(interaction, androidx.compose.material3.ripple()) { g.openShelfItem(latest, page.title) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(latest.thumbnailUrl, size = 76.dp, shape = RoundedCornerShape(8.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Latest release" + latest.subtitle.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(), style = GlowType.caption.copy(fontSize = 12.sp), color = dim, maxLines = 1)
                            Text(latest.title, style = GlowType.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = onTint, maxLines = 2)
                        }
                        latest.browseId?.let { id ->
                            ArtistAction(Icons.Rounded.PlayArrow, "Play ${latest.title}", onTint) { g.playBrowse(id, latest.title, shuffle = false) }
                        }
                    }
                }
            }
            if (page.songs is UiState.Loading) item(key = "loading") { SkeletonRows(4, Modifier.padding(top = 20.dp)) }
            if (songs.isNotEmpty()) {
                item(key = "top") {
                    Text("Top songs", style = GlowType.title.copy(fontSize = 18.sp), color = onTint, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp))
                    TopSongPages(songs, page, onTint, dim)
                }
            }
            listOfNotNull(albums?.let { "Albums" to it }, singles?.let { "Singles & EPs" to it }).forEach { (label, shelf) ->
                item(key = "rel$label") {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                        Text(label, style = GlowType.title.copy(fontSize = 18.sp), color = onTint, modifier = Modifier.weight(1f))
                        TextAction("See all", color = onTint.copy(alpha = 0.75f)) { g.push(Route.Discography(page.browseId, page.title)) }
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(shelf.items.distinctBy { it.browseId ?: it.title }, key = { it.browseId ?: it.title }) { item ->
                            ArtistCard(item, 136.dp, onTint, dim) { g.openShelfItem(item, page.title) }
                        }
                    }
                }
            }
            if (fans != null) {
                item(key = "fans") {
                    Text("Fans also like", style = GlowType.title.copy(fontSize = 18.sp), color = onTint, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 10.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(fans.items.distinctBy { it.browseId ?: it.title }, key = { it.browseId ?: it.title }) { item ->
                            Column(
                                Modifier.width(96.dp).clip(RoundedCornerShape(12.dp)).clickable { g.openArtist(item.browseId, item.title) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Cover(item.thumbnailUrl, size = 96.dp, shape = CircleShape)
                                Spacer(Modifier.height(8.dp))
                                Text(item.title, style = GlowType.body.copy(fontSize = 13.sp), color = onTint, maxLines = 2, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
            items(others.distinctBy { "o" + it.title }, key = { "o" + it.title }) { shelf ->
                Spacer(Modifier.height(22.dp))
                CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides onTint) { ShelfSection(shelf, 136.dp) }
            }
            if (!page.description.isNullOrBlank()) {
                item(key = "about") {
                    var expanded by rememberSaveable { mutableStateOf(false) }
                    Column(
                        Modifier
                            .padding(start = 20.dp, end = 20.dp, top = 22.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(onTint.copy(alpha = 0.08f))
                            .clickable { expanded = !expanded }
                            .padding(16.dp)
                            .animateContentSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("About", style = GlowType.title.copy(fontSize = 18.sp), color = onTint)
                        Text(
                            page.description.orEmpty(),
                            style = GlowType.bodyRegular.copy(fontSize = 14.sp, lineHeight = 20.sp),
                            color = onTint.copy(alpha = 0.78f),
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        GlassCircle(
            Modifier.statusBarsPadding().padding(start = 8.dp, top = 6.dp),
            background = Color.Black.copy(alpha = 0.35f),
            onClick = { g.pop() },
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(22.dp)) }
    }
}

/** Draws the content [by] higher, over whatever is above it, and takes that much less room. */
private fun Modifier.overlapAbove(by: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}

/** The page colour from the artist photo: its main colour, deep enough for white text. */
private fun artistTint(seed: Color, dark: Boolean): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(seed.toArgb(), hsl)
    hsl[1] = (hsl[1] * 0.7f).coerceAtMost(0.45f)
    hsl[2] = if (dark) 0.16f else 0.88f
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

@Composable
private fun ArtistAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onTint: Color, enabled: Boolean = true, onClick: () -> Unit) {
    GlassCircle(background = onTint.copy(alpha = 0.1f), onClick = { if (enabled) onClick() }) {
        Icon(icon, label, tint = onTint.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ArtistCard(item: ShelfItem, size: androidx.compose.ui.unit.Dp, onTint: Color, dim: Color, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(Modifier.width(size).pressScale(interaction, 0.95f).clickable(interaction, null, onClick = onClick)) {
        Cover(item.thumbnailUrl, size = size, shape = RoundedCornerShape(10.dp))
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = GlowType.body.copy(fontSize = 14.sp), color = onTint, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (item.subtitle.isNotBlank()) Text(item.subtitle, style = GlowType.caption.copy(fontSize = 12.sp), color = dim, maxLines = 1)
    }
}

/** Top songs four to a page; pages snap as they're swiped and the next one peeks in at the right. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TopSongPages(songs: List<Song>, page: DetailPage, onTint: Color, dim: Color) {
    val g = LocalGlow.current
    val pages = songs.take(20).chunked(4)
    val state = rememberLazyListState()
    BoxWithConstraints {
        val pageWidth = maxWidth - 64.dp
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(state),
        ) {
            itemsIndexed(pages) { p, chunk ->
                Column(Modifier.width(pageWidth)) {
                    chunk.forEachIndexed { k, song ->
                        val index = p * 4 + k
                        val playing = g.player.song?.videoId == song.videoId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    onClick = { g.playList(songs, index, page.title, PlaybackSourceType.BROWSE, page.browseId) },
                                    onLongClick = { g.openSongMenu(song) },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Cover(song.thumbnailUrl, size = 46.dp)
                                if (playing) {
                                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                                        PlayingBars(Modifier.size(16.dp), playing = g.player.isPlaying, color = Color.White)
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(song.title, style = GlowType.body.copy(fontSize = 15.sp, fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Medium), color = onTint, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(song.artist, style = GlowType.secondary.copy(fontSize = 13.sp), color = dim, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            IconTap(Icons.Rounded.MoreHoriz, "More options", tint = onTint.copy(alpha = 0.7f), size = 40.dp, iconSize = 18.dp) { g.openSongMenu(song) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The newest of an artist's albums and singles, by the year each one's subtitle gives (each
 * shelf lists newest first, so its first is its newest). Singles used to win every time, so an
 * album out this year never showed; on the same year, or with no years, the album goes first.
 */
internal fun latestRelease(albums: List<ShelfItem>, singles: List<ShelfItem>): ShelfItem? {
    val album = albums.firstOrNull()
    val single = singles.firstOrNull()
    if (album == null || single == null) return album ?: single
    val albumYear = releaseYear(album.subtitle)
    val singleYear = releaseYear(single.subtitle)
    return if (singleYear != null && (albumYear == null || singleYear > albumYear)) single else album
}

private val YEAR = Regex("\\b(19|20)\\d{2}\\b")

private fun releaseYear(subtitle: String): Int? = YEAR.findAll(subtitle).lastOrNull()?.value?.toIntOrNull()

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
            items(shelf?.items.orEmpty().distinctBy { "d" + (it.browseId ?: it.videoId ?: it.title) }, key = { "d" + (it.browseId ?: it.videoId ?: it.title) }) { item ->
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

/** Plays [ordered] from [index]: in the page's own order it's the page's queue, sorted it's that list. */
private fun playOrdered(g: GlowState, page: DetailPage, songs: List<Song>, ordered: List<Song>, order: SongOrder, index: Int, shuffle: Boolean = false) {
    if (order == SongOrder.DEFAULT || page.type == BrowseType.ALBUM) {
        g.actions.playDetail(page, songs, index, g.vm::takePlaybackContext, shuffle = shuffle)
    } else if (shuffle) {
        g.actions.shuffle(ordered, g.source(page.title, PlaybackSourceType.BROWSE, page.browseId))
    } else {
        g.playList(ordered, index, page.title, PlaybackSourceType.BROWSE, page.browseId)
    }
}

/** A playlist's top, drawn as Liked songs draws its own: cover beside the title, actions in a row. */
@Composable
private fun PlaylistHeader(
    page: DetailPage,
    songs: List<Song>,
    ordered: List<Song>,
    order: SongOrder,
    onOrder: (SongOrder) -> Unit,
    downloaded: Boolean,
    isOwned: Boolean,
    signedIn: Boolean,
    target: DownloadTarget,
    totalMs: Long,
    onMore: () -> Unit,
) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val parts = subtitleWithoutCounts(page.subtitle)
    val count = listOfNotNull(
        songs.size.takeIf { it > 0 }?.let { if (it == 1) "1 song" else "$it songs" },
        totalMs.takeIf { it > 0 }?.let { formatLongDuration(it) },
    ).joinToString(" · ")
    CollectionHeader(
        cover = { Cover(page.thumbnailUrl, size = 120.dp, shape = RoundedCornerShape(14.dp), px = 480) },
        title = page.title,
        lines = listOfNotNull(parts.joinToString(" · ").ifBlank { null }, count.ifBlank { null }),
        downloaded = downloaded,
        description = page.description,
        sort = {
            SortMenu(SongOrder.entries, order, label = { if (it == SongOrder.DEFAULT) "Playlist order" else it.label }, onSelect = onOrder)
        },
    ) {
        if (!downloaded) {
            IconTap(Icons.Rounded.Download, "Download") { g.actions.download(songs, target) }
        }
        if (signedIn && page.library != null && !isOwned) {
            val savedToLibrary = page.library?.saved == true
            IconTap(
                if (savedToLibrary) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (savedToLibrary) "Remove from library" else "Save to library",
            ) { vm.toggleLibrary(page.browseId) }
        }
        IconTap(Icons.Rounded.MoreHoriz, "More", onClick = onMore)
        IconTap(Icons.Rounded.Shuffle, "Shuffle", enabled = songs.isNotEmpty()) {
            if (songs.isNotEmpty()) playOrdered(g, page, songs, ordered, order, songs.indices.random(), shuffle = true)
        }
        Spacer(Modifier.width(6.dp))
        PrimaryCircle(Icons.Rounded.PlayArrow, "Play", size = 52.dp, enabled = songs.isNotEmpty()) {
            playOrdered(g, page, songs, ordered, order, 0)
        }
    }
}
