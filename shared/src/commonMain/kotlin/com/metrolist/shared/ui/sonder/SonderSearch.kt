package com.metrolist.shared.ui.sonder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.shared.model.*
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
            if (state.searchHistory.isEmpty() && state.recentSearchItems.isEmpty()) item("start") { Text(text.startTypingToSearch, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (state.loadMoreFailed) item("retry") { TextButton(onClick = onRetryLoadMore, modifier = Modifier.padding(20.dp)) { Text(stringResource(Res.string.retry)) } }
        if (state.isLoadingMore) item("more") { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }
    }
}
