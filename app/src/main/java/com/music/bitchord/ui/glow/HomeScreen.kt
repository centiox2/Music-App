package com.music.bitchord.ui.glow

import androidx.compose.material.icons.rounded.Groups
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val home by vm.home.collectAsStateWithLifecycle()
    val explore by vm.explore.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val online = rememberOnline()

    LaunchedEffect(Unit) {
        // Loaded once; after that Home only reloads when pulled down. (BitChord refreshed it
        // on every return once a song had played, and re-read the moods each time too.)
        if (vm.explore.value !is UiState.Success) vm.loadExplore()
    }

    val shelves = (home as? UiState.Success)?.data.orEmpty()
    val quick = shelves.firstOrNull { s -> s.items.count { it.videoId != null } >= 3 }
    val featured = shelves.asSequence().flatMap { it.items.asSequence().map { item -> it to item } }
        .firstOrNull { (_, item) -> item.browseId != null && item.videoId == null }
    val moods = (explore as? UiState.Success)?.data.orEmpty()
        .flatMap { it.items }
        .take(8)
    // 0 is "For you"; any other chip shows that mood's shelves right here instead of opening a page.
    var moodIndex by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val moodShelves by vm.moodGenreShelves.collectAsStateWithLifecycle()

    GlowScreen(glowUrl = g.player.song?.thumbnailUrl ?: featured?.second?.thumbnailUrl) {
        PullToRefreshBox(
            isRefreshing = MainViewModel.Feed.HOME in refreshing,
            onRefresh = { vm.refresh(MainViewModel.Feed.HOME) },
            modifier = Modifier.fillMaxSize(),
        ) {
            val listState = rememberLazyListState()
            val nearEnd by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
                }
            }
            LaunchedEffect(nearEnd, shelves.size) { if (nearEnd && shelves.isNotEmpty()) vm.loadMoreHome() }

            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = pageContentPadding()) {
                item {
                    PageHeader("Home") {
                        ListenTogetherButton()
                        GlassCircle(onClick = { g.selectTab(1) }) {
                            Icon(Icons.Rounded.Search, "Search", tint = c.text, modifier = Modifier.size(20.dp))
                        }
                        AccountAvatar(account?.name, account?.thumbnailUrl) { g.push(Route.Settings) }
                    }
                }
                if (!online) {
                    item { OfflineBanner(onRetry = { vm.refresh(MainViewModel.Feed.HOME) }) }
                    item { OfflineDownloads() }
                    if (quick != null) {
                        item { Spacer(Modifier.height(18.dp)); SectionHeader(quick.title) }
                        items(quick.items.take(4)) { item ->
                            SongRow(item.title, "Needs internet", item.thumbnailUrl, dimmed = true, onClick = {})
                        }
                    }
                    return@LazyColumn
                }
                when (val state = home) {
                    is UiState.Loading -> item { HomeSkeleton() }
                    is UiState.Error -> item {
                        EmptyState(
                            Icons.Rounded.WifiOff,
                            "Couldn't load Home",
                            state.message,
                            modifier = Modifier.padding(top = 60.dp),
                        ) { PillButton("Try again", onClick = { vm.loadHome() }) }
                    }
                    is UiState.Success -> {
                        item {
                            Spacer(Modifier.height(10.dp))
                            val chips = listOf("For you") + moods.map { it.title }
                            ChipRow(chips, selected = moodIndex.coerceAtMost(chips.lastIndex)) { i ->
                                moodIndex = i
                                if (i > 0) vm.openMoodGenre(moods[i - 1])
                            }
                            Spacer(Modifier.height(22.dp))
                        }
                        if (moodIndex > 0 && moodIndex <= moods.size) {
                            when (val m = moodShelves) {
                                is UiState.Loading -> item { HomeSkeleton() }
                                is UiState.Error -> item {
                                    EmptyState(Icons.Rounded.WifiOff, "Couldn't load ${moods[moodIndex - 1].title}", m.message, modifier = Modifier.padding(top = 40.dp)) {
                                        PillButton("Try again", onClick = { vm.openMoodGenre(moods[moodIndex - 1]) })
                                    }
                                }
                                is UiState.Success -> itemsIndexed(m.data, key = { _, it -> "m" + moodIndex + it.title }) { i, shelf ->
                                    Column(Modifier.staggeredEntrance(i, moodIndex.toString() + shelf.title)) {
                                        ShelfSection(shelf)
                                        Spacer(Modifier.height(22.dp))
                                    }
                                }
                            }
                            return@LazyColumn
                        }
                        featured?.let { (shelf, item) ->
                            item { Box(Modifier.staggeredEntrance(0, item.title)) { FeaturedCard(shelf.title, item) } }
                        }
                        quick?.let { shelf ->
                            item {
                                Spacer(Modifier.height(22.dp))
                                SectionHeader(shelf.title, action = "Play all") {
                                    val songs = shelf.items.mapNotNull { it.asSong() }
                                    g.playList(songs, 0, shelf.title, PlaybackSourceType.QUEUE)
                                }
                            }
                            val songs = shelf.items.mapNotNull { it.asSong() }
                            itemsIndexed(songs.take(8), key = { _, it -> "q" + it.videoId }) { i, song ->
                                TrackRow(
                                    song,
                                    modifier = Modifier.staggeredEntrance(i + 2, song.videoId),
                                    onClick = { g.playSingle(song, shelf.title) },
                                    onMore = { g.openSongMenu(song) },
                                    active = g.player.song?.videoId == song.videoId,
                                )
                            }
                        }
                        val rest = state.data.filter { it !== quick }
                        itemsIndexed(rest, key = { _, it -> "s" + it.title }) { i, shelf ->
                            Column(Modifier.staggeredEntrance(i + 6, shelf.title)) {
                                Spacer(Modifier.height(22.dp))
                                ShelfSection(shelf)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountAvatar(name: String?, imageUrl: String?, onClick: () -> Unit) {
    val c = Glow.colors
    GlassCircle(
        modifier = Modifier.semantics { contentDescription = "Account and settings" },
        onClick = onClick,
    ) {
        if (imageUrl != null) {
            AsyncImage(imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                name?.trim()?.firstOrNull()?.uppercase() ?: "•",
                style = GlowType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                color = c.text,
            )
        }
    }
}

/** The big supermix card: art behind, dark gradient, title, white play circle. */
@Composable
private fun FeaturedCard(shelfTitle: String, item: ShelfItem) {
    val g = LocalGlow.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(190.dp)
            .pressScale(interaction, 0.97f)
            .clip(RoundedCornerShape(14.dp))
            .background(Glow.colors.glassStrong)
            .clickable(interaction, androidx.compose.material3.ripple()) { g.openShelfItem(item, shelfTitle) },
    ) {
        ArtImage(item.thumbnailUrl, 720, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))),
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(shelfTitle, style = GlowType.secondary.copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                Text(item.title, style = GlowType.title.copy(fontSize = 22.sp), color = Color.White, maxLines = 2)
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color.White).clickable { g.openShelfItem(item, shelfTitle) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Open ${item.title}", tint = Color(0xFF111111), modifier = Modifier.size(26.dp)) }
        }
    }
}

/** A titled horizontal row of square cards, used by every feed. */
@Composable
fun ShelfSection(shelf: HomeShelf, cardSize: androidx.compose.ui.unit.Dp = 140.dp) {
    val g = LocalGlow.current
    Column {
        SectionHeader(shelf.title)
        if (shelf.subtitle.isNotBlank()) {
            Text(
                shelf.subtitle,
                style = GlowType.secondary,
                color = Glow.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(shelf.items.distinctBy { (it.videoId ?: it.browseId ?: it.title) + it.subtitle }, key = { (it.videoId ?: it.browseId ?: it.title) + it.subtitle }) { item ->
                val asSong = item.asSong()
                ShelfCard(
                    item,
                    cardSize,
                    circle = item.browseId?.startsWith("UC") == true,
                    onLongClick = asSong?.let { song -> { g.openSongMenu(song) } },
                ) {
                    g.openShelfItem(item, shelf.title)
                }
            }
        }
    }
}

@Composable
fun ShelfCard(
    item: ShelfItem,
    size: androidx.compose.ui.unit.Dp,
    circle: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = Glow.colors
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        Modifier
            .width(size)
            .pressScale(interaction, 0.95f)
            .combinedClickable(interaction, null, onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Cover(item.thumbnailUrl, size = size, shape = if (circle) CircleShape else RoundedCornerShape(12.dp), px = 544)
            val player = LocalGlow.current.player
            androidx.compose.animation.AnimatedVisibility(item.videoId != null && player.song?.videoId == item.videoId, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier.size(size).clip(if (circle) CircleShape else RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) { PlayingBars(Modifier.size(28.dp), playing = player.isPlaying, color = Color.White) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = GlowType.body, color = c.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (item.subtitle.isNotBlank()) {
            Text(item.subtitle, style = GlowType.secondary, color = c.textSecondary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OfflineBanner(onRetry: () -> Unit) {
    val c = Glow.colors
    Row(
        Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.warningSurface)
            .border(1.dp, c.warning.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.WifiOff, null, tint = c.warning, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("You're offline", style = GlowType.body.copy(fontWeight = FontWeight.SemiBold), color = c.warning)
            Text("Only downloaded music can play", style = GlowType.secondary, color = c.warning.copy(alpha = 0.85f))
        }
        Text(
            "Retry",
            style = GlowType.label,
            color = c.warning,
            modifier = Modifier
                .clip(CircleShape)
                .background(c.warning.copy(alpha = 0.15f))
                .clickable(onClick = onRetry)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun OfflineDownloads() {
    val g = LocalGlow.current
    val context = LocalContext.current
    val saved by Downloads.saved.collectAsStateWithLifecycle()
    val songs by produceState(emptyList<Song>(), saved) { value = Downloads.getDownloadedSongs(context) }
    Column {
        Spacer(Modifier.height(10.dp))
        SectionHeader("Downloaded", action = if (songs.size > 3) "See all" else null) { g.push(Route.Downloads) }
        if (songs.isEmpty()) {
            Text(
                "Nothing downloaded yet.",
                style = GlowType.secondary,
                color = Glow.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        songs.take(5).forEachIndexed { i, song ->
            TrackRow(
                song,
                onClick = { g.playList(songs, i, "Downloads", PlaybackSourceType.QUEUE) },
                onMore = { g.openSongMenu(song) },
                trailing = { Box(Modifier.padding(12.dp)) { DownloadedMark(Glow.colors.textSecondary) } },
            )
        }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.padding(top = 10.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(76, 64, 82, 78).forEach { SkeletonBox(Modifier.width(it.dp).height(34.dp), radius = 10.dp) }
        }
        Spacer(Modifier.height(22.dp))
        SkeletonBox(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(190.dp), radius = 14.dp)
        Spacer(Modifier.height(22.dp))
        SkeletonBox(Modifier.padding(horizontal = 20.dp).width(130.dp).height(18.dp), radius = 6.dp)
        Spacer(Modifier.height(16.dp))
        SkeletonRows(5)
        Spacer(Modifier.height(24.dp))
        Text(
            "Loading your music…",
            style = GlowType.secondary,
            color = Glow.colors.textSecondary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Whether the device has a validated internet connection right now. */
@Composable
fun rememberOnline(): Boolean {
    val context = LocalContext.current
    var online by remember { mutableStateOf(isOnline(context)) }
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = true }
            override fun onLost(network: Network) { online = isOnline(context) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
    return online
}

private fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/** Opens Listen together; a green dot shows while a session is live. */
@Composable
private fun ListenTogetherButton() {
    val g = LocalGlow.current
    val c = Glow.colors
    val party by com.music.bitchord.data.listentogether.ListenTogether.state.collectAsStateWithLifecycle()
    Box {
        GlassCircle(onClick = { g.push(Route.ListenTogether) }) {
            Icon(Icons.Rounded.Groups, "Listen together", tint = c.text, modifier = Modifier.size(21.dp))
        }
        if (party.code != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(2.dp).size(10.dp).clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color(0xFF5FD9A6)),
            )
        }
    }
}
