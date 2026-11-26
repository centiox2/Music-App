package com.metrolist.shared.ui.sonder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.model.*
import com.metrolist.shared.viewmodels.HomeViewModel
import com.metrolist.shared.generated.resources.*
import com.metrolist.shared.ui.icons.MetrolistIcons
import org.jetbrains.compose.resources.stringResource
import com.metrolist.shared.ui.screens.SearchContentText
import com.metrolist.shared.viewmodels.SearchViewModel.SearchUiState

@Composable
fun SonderSearchResults(state: SearchUiState, text: SearchContentText,
    onItem: (YTItem) -> Unit, onMore: (YTItem) -> Unit,
    onHistory: (String) -> Unit, onClearHistory: () -> Unit,
    onFilter: (SearchFilter?) -> Unit, onLoadMore: () -> Unit,
    currentSongId: String?, modifier: Modifier, listState: LazyListState, padding: PaddingValues,
    onRemoveHistory: (String) -> Unit = {}, onRemoveRecent: (YTItem) -> Unit = {},
    onClearRecent: () -> Unit = {}, onRetryLoadMore: () -> Unit = {},
    moodsAndGenres: List<HomeViewModel.Genre> = emptyList(),
    itemContainer: @Composable (YTItem, @Composable () -> Unit) -> Unit = { _, content -> content() },
) {
    val results = state.filteredResults?.items ?: state.searchSummary?.summaries?.flatMap { it.items } ?: state.unfilteredResults?.items.orEmpty()
    val filters = listOf(null to text.filterAll, SearchFilter.SONG to text.filterSongs, SearchFilter.ALBUM to text.filterAlbums, SearchFilter.ARTIST to text.filterArtists, SearchFilter.FEATURED_PLAYLIST to text.filterPlaylists, SearchFilter.VIDEO to text.filterVideos, SearchFilter.COMMUNITY_PLAYLIST to text.filterCommunityPlaylists, SearchFilter.PODCAST to text.filterPodcasts, SearchFilter.EPISODE to text.filterEpisodes, SearchFilter.PROFILE to text.filterProfiles)
        .filter { (filter, _) ->
            !(com.metrolist.shared.settings.Settings.shared.hideVideoSongs && filter == SearchFilter.VIDEO) &&
                !(com.metrolist.shared.settings.Settings.shared.hidePodcasts && (filter == SearchFilter.PODCAST || filter == SearchFilter.EPISODE))
        }
    val nearEnd by remember(listState) { derivedStateOf { val info = listState.layoutInfo; info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3 } }
    LaunchedEffect(nearEnd, results.size) { if (nearEnd && state.hasSearched && !state.isSearching && !state.isLoadingMore) onLoadMore() }
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = padding) {
        if (state.hasSearched || state.isQuerySubmitted) {
            item("filters") {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filters) { (filter, title) -> SonderPill(title, state.selectedFilter == filter) { onFilter(filter) } }
                }
            }
            if (state.isSearching && results.isEmpty()) item("loading") { Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }
            if (!state.isSearching && results.isEmpty()) item("empty") { Text(if (state.searchFailed) stringResource(Res.string.search_online_failed) else text.noResultsFound, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            itemsIndexed(results, key = { index, item -> "search_${index}_${item.id}" }) { _, item -> itemContainer(item) { SonderMediaRow(item, { onItem(item) }, { onMore(item) }, current = item.id == currentSongId) } }
        } else if (state.query.isNotBlank()) {
            val suggestions = state.searchSuggestions?.queries.orEmpty()
            item("suggestions") { SonderSection(text.suggestions) }
            items(suggestions) { query ->
                TextButton(onClick = { onHistory(query) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { Text(query, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface) }
            }
            itemsIndexed(state.searchSuggestions?.recommendedItems.orEmpty()) { _, item -> SonderMediaRow(item, { onItem(item) }, { onMore(item) }) }
        } else {
            if (state.searchHistory.isNotEmpty()) {
                item("recent_title") { SonderSection(text.recentSearches, text.clear, onClearHistory) }
                items(state.searchHistory.distinct()) { query ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        TextButton(onClick = { onHistory(query) }, modifier = Modifier.weight(1f)) { Text(query, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface) }
                        SonderIcon(MetrolistIcons.Outlined.Close, text.removeRecentSearchAction, { onRemoveHistory(query) })
                    }
                }
            }
            if (state.recentSearchItems.isNotEmpty()) item("recent_music") { SonderSection(text.recentFromSearches, text.clear, onClearRecent) }
            itemsIndexed(state.recentSearchItems) { _, item -> itemContainer(item) {
                SonderMediaRow(item, { onItem(item) }, { onMore(item) }, trailing = {
                    SonderIcon(MetrolistIcons.Outlined.Close, text.removeRecentSearchItemAction, { onRemoveRecent(item) })
                })
            } }
            if (moodsAndGenres.isNotEmpty()) {
                item("mood_title") { SonderSection("Moods & genres") }
                itemsIndexed(moodsAndGenres.chunked(2)) { rowIndex, row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { columnIndex, genre ->
                            SonderMoodTile(genre.name, rowIndex * 2 + columnIndex, Modifier.weight(1f)) {
                                onItem(BrowseItem(genre.id, genre.name, genre.thumbnail, genre.params))
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else if (state.searchHistory.isEmpty() && state.recentSearchItems.isEmpty()) {
                item("start") { Text(text.startTypingToSearch, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (state.loadMoreFailed) item("retry") { TextButton(onClick = onRetryLoadMore, modifier = Modifier.padding(20.dp)) { Text(stringResource(Res.string.retry)) } }
        if (state.isLoadingMore) item("more") { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }
    }
}

@Composable
private fun SonderMoodTile(title: String, index: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = listOf(0xFF2F5A55, 0xFFB5441B, 0xFF1F3A63, 0xFF8E1C17, 0xFF5B2A86, 0xFF344A63, 0xFF3D5A2A, 0xFF7A4A12)
    val color = Color(colors[index % colors.size])
    val tileColor = color.copy(red = color.red + (1f - color.red) * 0.25f, green = color.green + (1f - color.green) * 0.25f, blue = color.blue + (1f - color.blue) * 0.25f)
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxWidth().height(74.dp).clip(RoundedCornerShape(12.dp))
            .background(color.copy(red = (color.red * 0.55f + 0.02f).coerceAtMost(1f), green = (color.green * 0.55f + 0.02f).coerceAtMost(1f), blue = (color.blue * 0.55f + 0.02f).coerceAtMost(1f)))
            .clickable(onClick = onClick),
    ) {
        androidx.compose.foundation.layout.Box(Modifier.align(androidx.compose.ui.Alignment.BottomEnd).offset(x = 14.dp, y = 18.dp).size(58.dp).rotate(20f)
            .clip(RoundedCornerShape(10.dp)).background(tileColor))
        Text(title, Modifier.padding(12.dp).padding(end = 30.dp), color = Color.White,
            fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 2)
    }
}
