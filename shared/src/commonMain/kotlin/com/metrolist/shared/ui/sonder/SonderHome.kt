package com.metrolist.shared.ui.sonder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.shared.model.*
import com.metrolist.shared.viewmodels.HomeViewModel
import com.metrolist.shared.ui.screens.HomeShelf
import com.metrolist.shared.ui.screens.HomeContentText
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.see_all
import org.jetbrains.compose.resources.stringResource

/** Original Sonder Home composition: mood pills, featured mix, quick-pick tracks and cover shelves. */
@Composable
fun SonderHome(
    shelves: List<HomeShelf>, text: HomeContentText,
    onItemClick: (YTItem) -> Unit, onItemLongClick: ((YTItem) -> Unit)?,
    onPlaySongs: ((List<SongItem>) -> Unit)?,
    currentSongId: State<String?>, enabled: (YTItem) -> Boolean,
    listState: LazyListState, contentPadding: PaddingValues, modifier: Modifier,
    onLoadMore: (() -> Unit)?, loading: Boolean, header: (LazyListScope.() -> Unit)?,
    shelfAction: (HomeShelf) -> (() -> Unit)? = { null },
    shelfActionLabel: (HomeShelf) -> String? = { null },
    genres: List<HomeViewModel.Genre> = emptyList(),
    onGenreClick: (HomeViewModel.Genre) -> Unit = {},
) {
    val featured = shelves.firstNotNullOfOrNull { shelf ->
        shelf.items.firstOrNull { (it is PlaylistItem || it is AlbumItem) && it.thumbnail != null && enabled(it) }?.let { shelf to it }
    }
    val quick = shelves.firstOrNull { it.items.filterIsInstance<SongItem>().size >= 3 }
    val nearEnd by remember(listState) { derivedStateOf {
        val info = listState.layoutInfo
        info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
    } }
    LaunchedEffect(nearEnd, shelves.size) { if (nearEnd) onLoadMore?.invoke() }
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        header?.invoke(this)
        item("sonder_moods") {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { SonderPill("For you", selected = true, onClick = {}) }
                items(genres, key = { "sonder_genre_${it.id}" }) { genre ->
                    SonderPill(genre.name, onClick = { onGenreClick(genre) })
                }
            }
        }
        if (featured != null) item("sonder_featured") {
            SonderFeaturedCard(featured.first.title, featured.second) { onItemClick(featured.second) }
        }
        if (quick != null) {
            val songs = quick.items.filterIsInstance<SongItem>()
            item("sonder_quick_header") {
                SonderSection(quick.title, text.playAll, onPlaySongs?.let { play -> { play(songs.filter(enabled)) } })
            }
            itemsIndexed(songs.take(8), key = { index, song -> "sonder_quick_${index}_${song.id}" }) { _, song ->
                SonderMediaRow(song, { onItemClick(song) }, onItemLongClick?.let { more -> { more(song) } }, current = currentSongId.value == song.id, enabled = enabled(song))
            }
        }
        shelves.filter { it !== quick && it.items.isNotEmpty() }.forEachIndexed { index, shelf ->
            item("sonder_shelf_${index}_${shelf.key}") {
                Column {
                    Spacer(Modifier.height(16.dp))
                    SonderSection(shelf.headerTitle ?: shelf.title, shelfActionLabel(shelf) ?: stringResource(Res.string.see_all), shelfAction(shelf))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(shelf.items, key = { i, item -> "${i}_${item.id}" }) { _, item ->
                            SonderCard(item, { if (enabled(item)) onItemClick(item) }, onItemLongClick?.let { more -> { more(item) } })
                        }
                    }
                }
            }
        }
        if (loading) item("sonder_loading") {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
        }
        item("sonder_end") { Spacer(Modifier.height(24.dp)) }
    }
}
