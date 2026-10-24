package com.music.bitchord.ui.glow

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.EntityType
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchHistoryEntity
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.ui.MainViewModel
import kotlinx.coroutines.launch

private val FILTERS = listOf(
    SearchFilter.ALL to "Top",
    SearchFilter.SONGS to "Songs",
    SearchFilter.ALBUMS to "Albums",
    SearchFilter.ARTISTS to "Artists",
    SearchFilter.PLAYLISTS to "Playlists",
    SearchFilter.VIDEOS to "Videos",
)

/** Placeholder palette for mood tiles that arrive without a colour. */
private val TILE_COLORS = listOf(0xFF2F5A55, 0xFFB5441B, 0xFF1F3A63, 0xFF8E1C17, 0xFF5B2A86, 0xFF344A63, 0xFF3D5A2A, 0xFF7A4A12)

@Composable
fun SearchScreen() {
    val g = LocalGlow.current
    val vm = g.vm
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    var typing by rememberSaveable { mutableStateOf(false) }

    when {
        results != null -> SearchResults(query, onBack = {
            vm.onQueryChange("")
            typing = false
        })
        typing || query.isNotEmpty() -> SearchTyping(query, onBack = {
            vm.onQueryChange("")
            typing = false
        })
        else -> SearchIdle(onStartTyping = { typing = true })
    }
}

@Composable
private fun SearchIdle(onStartTyping: () -> Unit) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val history by vm.searchHistory.collectAsStateWithLifecycle()
    val explore by vm.explore.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadExplore() }
    val voice = rememberVoiceSearch { vm.searchFor(it) }
    val moods = (explore as? UiState.Success)?.data.orEmpty().flatMap { it.items }

    GlowScreen(glowUrl = g.player.song?.thumbnailUrl) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = pageContentPadding().calculateBottomPadding()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Column {
                    Text(
                        "Search",
                        style = GlowType.display,
                        color = c.text,
                        modifier = Modifier.statusBarsPadding().padding(top = 14.dp, bottom = 14.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.glassStrong)
                                .clickable(onClick = onStartTyping)
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Search, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Songs, albums, artists, podcasts", style = GlowType.bodyRegular, color = c.textSecondary, maxLines = 1)
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(c.glassStrong).clickable { voice() },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Mic, "Voice search", tint = c.text, modifier = Modifier.size(22.dp)) }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
            if (history.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                        GlowText("Recent searches", GlowType.title, Modifier.weight(1f))
                        TextAction("Clear") { vm.clearSearchHistory() }
                    }
                }
                history.take(6).forEach { entity ->
                    item(span = { GridItemSpan(2) }, key = "h" + entity.id) {
                        RecentSearchRow(entity, onRemove = { vm.removeSearch(entity.id) }) {
                            vm.recordEntity(entity.copy(timestamp = System.currentTimeMillis()))
                            when (entity.entityType) {
                                EntityType.TRACK -> g.playSingle(
                                    Song(entity.id, entity.title, entity.subtitle.substringAfterLast(" · "), entity.artworkUrl),
                                    "Search",
                                )
                                EntityType.ARTIST -> g.openDetail(entity.id, entity.title, entity.subtitle, entity.artworkUrl, BrowseType.ARTIST)
                                EntityType.ALBUM -> g.openDetail(entity.id, entity.title, entity.subtitle, entity.artworkUrl, BrowseType.ALBUM)
                                EntityType.PLAYLIST -> g.openDetail(entity.id, entity.title, entity.subtitle, entity.artworkUrl, BrowseType.PLAYLIST)
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(10.dp)) }
            }
            item(span = { GridItemSpan(2) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlowText("Moods & genres", GlowType.title, Modifier.weight(1f))
                    TextAction("New & charts") { g.push(Route.Explore) }
                }
            }
            if (explore is UiState.Loading) {
                items(6) { SkeletonBox(Modifier.fillMaxWidth().height(74.dp), radius = 12.dp) }
            } else if (moods.isEmpty()) {
                // A failed or empty load used to leave this section blank with no way back.
                item(span = { GridItemSpan(2) }) {
                    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlowText("Couldn't load moods & genres", GlowType.secondary, Modifier.weight(1f), color = Glow.colors.textSecondary)
                        TextAction("Try again") { vm.loadExplore() }
                    }
                }
            }
            itemsIndexed(moods, key = { _, m -> m.browseId + (m.params ?: "") }) { i, mood ->
                MoodTile(mood, i, Modifier.staggeredEntrance(i, mood.browseId + mood.title)) { g.openMood(mood) }
            }
        }
    }
}

@Composable
private fun MoodTile(mood: MoodGenre, index: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val base = mood.stripeColor?.let { Color(it) } ?: Color(TILE_COLORS[index % TILE_COLORS.size])
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .height(74.dp)
            .pressScale(interaction, 0.95f)
            .clip(RoundedCornerShape(12.dp))
            .background(base.copy(alpha = 1f).darken(0.55f))
            .clickable(interaction, androidx.compose.material3.ripple(), onClick = onClick),
    ) {
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 14.dp, y = 18.dp)
                .size(58.dp)
                .rotate(20f)
                .clip(RoundedCornerShape(10.dp))
                .background(base.lighten(0.25f)),
        )
        Text(
            mood.title,
            style = GlowType.body.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
            color = Color.White,
            maxLines = 2,
            modifier = Modifier.padding(12.dp).padding(end = 30.dp),
        )
    }
}

private fun Color.darken(f: Float) = Color(red * f + 0.02f, green * f + 0.02f, blue * f + 0.02f, 1f)
private fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, 1f)

@Composable
private fun RecentSearchRow(entity: SearchHistoryEntity, onRemove: () -> Unit, onClick: () -> Unit) {
    val kind = when (entity.entityType) {
        EntityType.TRACK -> "Song"
        EntityType.ALBUM -> "Album"
        EntityType.ARTIST -> "Artist"
        EntityType.PLAYLIST -> "Playlist"
    }
    val subtitle = if (entity.entityType == EntityType.ARTIST || entity.subtitle.isBlank()) kind else "$kind · ${entity.subtitle}"
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(
            entity.artworkUrl,
            size = 44.dp,
            shape = if (entity.entityType == EntityType.ARTIST) CircleShape else RoundedCornerShape(8.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            GlowText(entity.title, GlowType.body, maxLines = 1)
            GlowText(subtitle, GlowType.secondary, color = Glow.colors.textSecondary, maxLines = 1)
        }
        IconTap(Icons.Rounded.Close, "Remove", tint = Glow.colors.textSecondary, iconSize = 20.dp, onClick = onRemove)
    }
}

@Composable
private fun SearchTyping(query: String, onBack: () -> Unit) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val typeahead by vm.typeaheadResults.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(c.base)) {
        LazyColumn(Modifier.fillMaxSize().imePadding()) {
            item {
                Row(
                    Modifier.statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTap(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
                    SearchField(
                        value = query,
                        onValueChange = vm::onQueryChange,
                        placeholder = "Songs, albums, artists",
                        modifier = Modifier.weight(1f),
                        outlined = true,
                        autoFocusModifier = Modifier.focusRequester(focus),
                        onSearch = { vm.submitSearch() },
                    )
                }
            }
            items(suggestions, key = { "s$it" }) { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.searchFor(s) }.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        boldPrefix(s, query),
                        style = GlowType.bodyRegular.copy(fontSize = 16.sp),
                        color = c.text,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    IconTap(Icons.Rounded.NorthWest, "Fill in", tint = c.textSecondary, iconSize = 18.dp) { vm.onQueryChange("$s ") }
                }
            }
            if (suggestions.isNotEmpty() && typeahead.isNotEmpty()) {
                item { CardDivider(Modifier.padding(vertical = 8.dp), start = 20.dp) }
            }
            items(typeahead.take(6), key = { "t" + resultKey(it) }) { r -> ResultRow(r, compact = true) }
        }
    }
}

private fun boldPrefix(text: String, query: String) = buildAnnotatedString {
    val q = query.trim()
    if (q.isNotEmpty() && text.startsWith(q, ignoreCase = true)) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(0, q.length)) }
        append(text.substring(q.length))
    } else {
        append(text)
    }
}

private fun resultKey(r: SearchResult) = when (r) {
    is SearchResult.TopTrack -> "top" + r.song.videoId
    is SearchResult.Track -> r.song.videoId
    is SearchResult.Browse -> r.item.browseId
}

@Composable
private fun SearchResults(query: String, onBack: () -> Unit) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val results by vm.results.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val loadingMore by vm.searchLoadingMore.collectAsStateWithLifecycle()
    val rows = (results as? UiState.Success)?.data.orEmpty()
    var editing by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    GlowScreen(glowUrl = (rows.firstOrNull() as? SearchResult.Browse)?.item?.thumbnailUrl ?: g.player.song?.thumbnailUrl) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding()) {
            item {
                Row(
                    Modifier.statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTap(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
                    SearchField(
                        value = query,
                        onValueChange = {
                            vm.onQueryChange(it)
                            editing = true
                        },
                        placeholder = "Search",
                        modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) editing = true },
                        onSearch = {
                            vm.submitSearch()
                            editing = false
                            focusManager.clearFocus()
                        },
                    )
                }
                Spacer(Modifier.height(14.dp))
                ChipRow(FILTERS.map { it.second }, FILTERS.indexOfFirst { it.first == filter }) { i ->
                    vm.onFilterChange(FILTERS[i].first)
                }
                Spacer(Modifier.height(16.dp))
            }
            when (val state = results) {
                is UiState.Loading, null -> item { SkeletonRows(6, Modifier.padding(top = 8.dp)) }
                is UiState.Error -> item {
                    EmptyState(Icons.Rounded.SearchOff, "Search failed", state.message) {
                        PillButton("Try again", onClick = { vm.submitSearch() })
                    }
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        item { EmptyState(Icons.Rounded.SearchOff, "No results", "Try a different spelling or fewer words.") }
                    } else if (filter == SearchFilter.ALL) {
                        topResults(state.data)
                    } else {
                        items(state.data, key = { resultKey(it) }) { r -> ResultRow(r) }
                        item {
                            LaunchedEffect(state.data.size) { vm.loadMoreSearchResults() }
                            if (loadingMore) SkeletonRows(2, Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.topResults(rows: List<SearchResult>) {
    val top = rows.firstOrNull()
    val songs = rows.drop(1).mapNotNull {
        when (it) {
            is SearchResult.Track -> it.song
            is SearchResult.TopTrack -> it.song
            else -> null
        }
    }
    val browse = rows.drop(1).filterIsInstance<SearchResult.Browse>().map { it.item }
    val albums = browse.filter { it.type == BrowseType.ALBUM }
    val artists = browse.filter { it.type == BrowseType.ARTIST }
    val playlists = browse.filter { it.type == BrowseType.PLAYLIST }
    val others = browse.filter { it.type == BrowseType.OTHER }

    if (top != null) item { TopResultCard(top) }
    if (songs.isNotEmpty()) {
        item { Spacer(Modifier.height(16.dp)); SectionHeader("Songs") }
        items(songs.take(4), key = { "song" + it.videoId }) { s -> ResultRow(SearchResult.Track(s)) }
    }
    browseShelf("Albums", albums)
    browseShelf("Artists", artists, circle = true)
    browseShelf("Playlists", playlists)
    browseShelf("More", others)
}

private fun androidx.compose.foundation.lazy.LazyListScope.browseShelf(title: String, items: List<BrowseItem>, circle: Boolean = false) {
    if (items.isEmpty()) return
    item(key = "shelf$title") {
        val g = LocalGlow.current
        Column {
            Spacer(Modifier.height(16.dp))
            SectionHeader(title)
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.browseId }) { item ->
                    Column(
                        Modifier.width(130.dp).clickable {
                            g.recordBrowse(item)
                            g.openDetail(item.browseId, item.title, item.subtitle, item.thumbnailUrl, item.type)
                        },
                    ) {
                        Cover(item.thumbnailUrl, size = 130.dp, shape = if (circle) CircleShape else RoundedCornerShape(10.dp))
                        Spacer(Modifier.height(8.dp))
                        GlowText(item.title, GlowType.body, maxLines = 1)
                        GlowText(item.subtitle, GlowType.secondary, color = Glow.colors.textSecondary, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun GlowState.recordBrowse(item: BrowseItem) {
    vm.recordEntity(
        SearchHistoryEntity(
            id = item.browseId,
            title = item.title,
            subtitle = item.subtitle,
            artworkUrl = item.thumbnailUrl,
            entityType = when (item.type) {
                BrowseType.ARTIST -> EntityType.ARTIST
                BrowseType.ALBUM -> EntityType.ALBUM
                else -> EntityType.PLAYLIST
            },
        ),
    )
}

private fun GlowState.recordSong(song: Song) {
    vm.recordEntity(
        SearchHistoryEntity(
            id = song.videoId,
            title = song.title,
            subtitle = song.artist,
            artworkUrl = song.thumbnailUrl,
            entityType = EntityType.TRACK,
        ),
    )
}

@Composable
private fun TopResultCard(result: SearchResult) {
    val g = LocalGlow.current
    val c = Glow.colors
    val (title, kind, art, circle) = when (result) {
        is SearchResult.TopTrack -> Quad(result.song.title, "Song · ${result.song.artist}", result.song.thumbnailUrl, false)
        is SearchResult.Track -> Quad(result.song.title, "Song · ${result.song.artist}", result.song.thumbnailUrl, false)
        is SearchResult.Browse -> Quad(
            result.item.title,
            when (result.item.type) {
                BrowseType.ARTIST -> "Artist"
                BrowseType.ALBUM -> result.item.subtitle.ifBlank { "Album" }
                else -> result.item.subtitle.ifBlank { "Playlist" }
            },
            result.item.thumbnailUrl,
            result.item.type == BrowseType.ARTIST,
        )
    }
    val open: () -> Unit = { openResult(g, result) }
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.glass)
            .clickable(onClick = open)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(art, size = 76.dp, shape = if (circle) CircleShape else RoundedCornerShape(10.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            GlowText("Top result · $kind", GlowType.secondary, color = c.textSecondary, maxLines = 1)
            GlowText(title, GlowType.title.copy(fontSize = 22.sp), maxLines = 2)
        }
        Spacer(Modifier.width(10.dp))
        PrimaryCircle(Icons.Rounded.PlayArrow, "Play", size = 48.dp) {
            when (result) {
                is SearchResult.TopTrack -> { g.recordSong(result.song); g.playSingle(result.song, "Search") }
                is SearchResult.Track -> { g.recordSong(result.song); g.playSingle(result.song, "Search") }
                is SearchResult.Browse -> {
                    g.recordBrowse(result.item)
                    g.playBrowse(result.item.browseId, result.item.title)
                }
            }
        }
    }
}

private data class Quad(val a: String, val b: String, val c: String?, val d: Boolean)

private fun openResult(g: GlowState, result: SearchResult) {
    when (result) {
        is SearchResult.TopTrack -> { g.recordSong(result.song); g.playSingle(result.song, "Search") }
        is SearchResult.Track -> { g.recordSong(result.song); g.playSingle(result.song, "Search") }
        is SearchResult.Browse -> {
            g.recordBrowse(result.item)
            g.openDetail(result.item.browseId, result.item.title, result.item.subtitle, result.item.thumbnailUrl, result.item.type)
        }
    }
}

@Composable
private fun ResultRow(result: SearchResult, compact: Boolean = false) {
    val g = LocalGlow.current
    when (result) {
        is SearchResult.TopTrack, is SearchResult.Track -> {
            val song = if (result is SearchResult.Track) result.song else (result as SearchResult.TopTrack).song
            val kind = if (song.isVideo) "Video" else "Song"
            SongRow(
                song.title,
                listOfNotNull(kind, song.artist.ifBlank { null }, song.durationText).joinToString(" · "),
                song.thumbnailUrl,
                active = g.player.song?.videoId == song.videoId,
                onClick = { openResult(g, result) },
                trailing = if (compact) null else ({ MoreButton { g.openSongMenu(song) } }),
            )
        }
        is SearchResult.Browse -> {
            val item = result.item
            val kind = when (item.type) {
                BrowseType.ARTIST -> "Artist"
                BrowseType.ALBUM -> "Album"
                BrowseType.PLAYLIST -> "Playlist"
                BrowseType.OTHER -> ""
            }
            SongRow(
                item.title,
                listOf(kind, item.subtitle).filter { it.isNotBlank() }.distinct().joinToString(" · "),
                item.thumbnailUrl,
                circleCover = item.type == BrowseType.ARTIST,
                onClick = { openResult(g, result) },
                trailing = if (compact) null else ({ ChevronRight(); Spacer(Modifier.width(12.dp)) }),
            )
        }
    }
}

/** Plays a whole album or playlist straight from its browse id. */
fun GlowState.playBrowse(browseId: String, title: String, shuffle: Boolean = false) {
    if (MainViewModel.browseTypeOf(browseId) == BrowseType.ARTIST) {
        openDetail(browseId, title, "Artist", null, BrowseType.ARTIST)
        return
    }
    scope.launch {
        val songs = YtMusicRepository.allSongs(browseId).getOrNull().orEmpty()
        if (songs.isEmpty()) {
            notify("Couldn't load $title")
        } else if (shuffle) {
            actions.shuffle(songs, source(title, PlaybackSourceType.BROWSE, browseId))
        } else {
            playList(songs, 0, title, PlaybackSourceType.BROWSE, browseId)
        }
    }
}

@Composable
private fun rememberVoiceSearch(onResult: (String) -> Unit): () -> Unit {
    val g = LocalGlow.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onResult)
        }
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search music")
        runCatching { launcher.launch(intent) }.onFailure { g.notify("Voice search isn't available") }
    }
}

// ---- Mood / genre ----

@Composable
fun MoodScreen(route: Route.Mood) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val selected by vm.selectedMoodGenre.collectAsStateWithLifecycle()
    val shelves by vm.moodGenreShelves.collectAsStateWithLifecycle()
    val data = (shelves as? UiState.Success)?.data.orEmpty()
    val firstPlaylist = data.flatMap { it.items }.firstOrNull { it.browseId != null }

    GlowScreen(glowUrl = selected?.thumbnailUrl ?: firstPlaylist?.thumbnailUrl) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding()) {
            item {
                TopBar(onBack = { g.pop() }) {
                    IconTap(Icons.Rounded.Search, "Search") { g.popToRoot(); g.selectTab(1) }
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("Mood", style = GlowType.secondary.copy(fontSize = 14.sp), color = c.textSecondary)
                    Text(selected?.title ?: route.title, style = GlowType.hero, color = c.text)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Start radio", icon = Icons.Rounded.PlayArrow, height = 44.dp, enabled = firstPlaylist != null) {
                            firstPlaylist?.let { g.playBrowse(it.browseId!!, it.title, shuffle = true) }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
            when (val state = shelves) {
                is UiState.Loading -> item { SkeletonRows(5) }
                is UiState.Error -> item { EmptyState(Icons.Rounded.SearchOff, "Couldn't load", state.message) }
                is UiState.Success -> state.data.forEach { shelf -> gridShelf(shelf) }
            }
        }
    }
}

/** A shelf laid out as the design's two-column card grid. */
private fun androidx.compose.foundation.lazy.LazyListScope.gridShelf(shelf: HomeShelf) {
    item(key = "t" + shelf.title) { SectionHeader(shelf.title, Modifier.padding(top = 8.dp)) }
    shelf.items.chunked(2).forEachIndexed { i, pair ->
        item(key = shelf.title + i) {
            val g = LocalGlow.current
            Row(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pair.forEach { item ->
                    Column(Modifier.weight(1f).clickable { g.openShelfItem(item, shelf.title) }) {
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

// ---- Explore: new releases, charts, moods ----

@Composable
fun ExploreScreen() {
    val g = LocalGlow.current
    val vm = g.vm
    val explore by vm.explore.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(Unit) { vm.loadExplore() }
    val shelves = (home as? UiState.Success)?.data.orEmpty()
    val releases = shelves.filter { s -> listOf("new", "release", "album", "single").any { s.title.contains(it, true) } }
    val charts = shelves.filter { s -> listOf("chart", "top", "trending", "popular").any { s.title.contains(it, true) } }
    val moods = (explore as? UiState.Success)?.data.orEmpty()

    GlowScreen(glowUrl = (releases.firstOrNull() ?: charts.firstOrNull())?.items?.firstOrNull()?.thumbnailUrl) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding()) {
            item {
                TopBar(onBack = { g.pop() })
                Text("Explore", style = GlowType.display.copy(fontSize = 34.sp), color = Glow.colors.text, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(14.dp))
                ChipRow(listOf("New releases", "Charts", "Moods"), tab) { tab = it }
                Spacer(Modifier.height(18.dp))
            }
            when (tab) {
                0 -> if (releases.isEmpty()) {
                    item { EmptyState(Icons.Rounded.Search, "Nothing new here yet", "New releases appear as YouTube Music adds them to your Home.") }
                } else {
                    releases.forEach { shelf -> item(key = "r" + shelf.title) { ShelfSection(shelf, 136.dp); Spacer(Modifier.height(18.dp)) } }
                }
                1 -> if (charts.isEmpty()) {
                    item { EmptyState(Icons.Rounded.Search, "No charts yet", "Charts appear as YouTube Music adds them to your Home.") }
                } else {
                    charts.forEach { shelf ->
                        item(key = "c" + shelf.title) {
                            val songs = shelf.items.mapNotNull { it.asSong() }
                            if (songs.isNotEmpty()) {
                                SectionHeader(shelf.title, action = "Play all") {
                                    g.playList(songs, 0, shelf.title, PlaybackSourceType.QUEUE)
                                }
                                songs.take(10).forEachIndexed { i, song ->
                                    SongRow(
                                        song.title,
                                        song.artist,
                                        song.thumbnailUrl,
                                        leading = {
                                            Text(
                                                "${i + 1}",
                                                style = GlowType.title.copy(fontSize = 18.sp),
                                                color = Glow.colors.text,
                                                modifier = Modifier.width(30.dp),
                                            )
                                            Cover(song.thumbnailUrl, size = 48.dp)
                                        },
                                        onClick = { g.playList(songs, i, shelf.title, PlaybackSourceType.QUEUE) },
                                        trailing = { MoreButton { g.openSongMenu(song) } },
                                    )
                                }
                            } else {
                                ShelfSection(shelf)
                            }
                            Spacer(Modifier.height(18.dp))
                        }
                    }
                }
                else -> moods.forEach { section ->
                    item(key = "m" + section.title) {
                        SectionHeader(section.title)
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(section.items) { i, mood ->
                                Box(Modifier.width(150.dp)) { MoodTile(mood, i) { g.openMood(mood) } }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                }
            }
        }
    }
}
