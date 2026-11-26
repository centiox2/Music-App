/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.remove_recent_search_action
import com.metrolist.shared.generated.resources.search
import com.metrolist.shared.model.AlbumItem
import com.metrolist.shared.model.SearchFilter
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.YTItem
import com.metrolist.shared.ui.LocalIsTv
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.tv.tvFocusHighlight
import com.metrolist.shared.viewmodels.SearchViewModel
import org.jetbrains.compose.resources.stringResource

data class SearchScreenText(
    val contentText: SearchContentText,
    val searchPlaceholder: String,
    val back: String,
    val clear: String,
    val microphoneContentDescription: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: SearchViewModel.SearchUiState,
    text: SearchScreenText,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
    onItemClick: (YTItem) -> Unit,
    onVideoItemClick: (YTItem) -> Unit = onItemClick,
    onItemLongClick: (YTItem) -> Unit,
    onSongSave: (SongItem) -> Unit,
    onHistoryItemClick: (String) -> Unit,
    onHistoryItemInsert: (String) -> Unit,
    onClearHistory: () -> Unit,
    onFilterSelected: (SearchFilter?) -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onRemoveHistoryItem: (String) -> Unit = {},
    onRemoveRecentItem: (YTItem) -> Unit = {},
    onClearRecentItems: () -> Unit = {},
    currentSongId: String? = null,
    isPlaying: Boolean = false,
    onArtistClick: ((String) -> Unit)? = null,
    onAlbumClick: ((String) -> Unit)? = null,
    onShuffleClick: (YTItem) -> Unit = {},
    onMixClick: (YTItem) -> Unit = {},
    itemContainer: @Composable (YTItem, @Composable () -> Unit) -> Unit = { _, content -> content() },
    onMicrophoneClick: (() -> Unit)? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    showSearchBar: Boolean = true,
    searchBarEndPadding: Dp = 0.dp,
    onAlbumPlay: ((AlbumItem) -> Unit)? = null,
) {
    val query = uiState.query
    val isQuerySubmitted = uiState.isQuerySubmitted
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun dismissKeyboard() {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.TopCenter,
        ) {
            val isWide = maxWidth > 600.dp

            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (showSearchBar) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(end = searchBarEndPadding),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(modifier = if (isWide) Modifier.widthIn(max = 1400.dp).fillMaxWidth() else Modifier.fillMaxWidth()) {
                            SearchBar(
                                query = query,
                                onQueryChange = onQueryChange,
                                onSearch = onSearch,
                                onClear = { onQueryChange("") },
                                onBack = onBack,
                                onMicrophoneClick = onMicrophoneClick,
                                microphoneContentDescription = text.microphoneContentDescription,
                                isQuerySubmitted = isQuerySubmitted,
                                focusRequester = focusRequester,
                                placeholder = text.searchPlaceholder,
                                backLabel = text.back,
                                clearLabel = text.clear,
                            )
                        }
                    }
                }

                SearchContent(
                    uiState = uiState,
                    text = text.contentText,
                    onItemClick = {
                        dismissKeyboard()
                        onItemClick(it)
                    },
                    onVideoItemClick = {
                        dismissKeyboard()
                        onVideoItemClick(it)
                    },
                    onItemLongClick = onItemLongClick,
                    onSongSave = onSongSave,
                    onHistoryItemClick = {
                        dismissKeyboard()
                        onHistoryItemClick(it)
                    },
                    onHistoryItemInsert = onHistoryItemInsert,
                    onClearHistory = onClearHistory,
                    onFilterSelected = onFilterSelected,
                    onLoadMore = onLoadMore,
                    onRetryLoadMore = onRetryLoadMore,
                    onRemoveHistoryItem = onRemoveHistoryItem,
                    onRemoveRecentItem = onRemoveRecentItem,
                    onClearRecentItems = onClearRecentItems,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    onArtistClick = onArtistClick,
                    onAlbumClick = onAlbumClick,
                    onShuffleClick = onShuffleClick,
                    onMixClick = onMixClick,
                    itemContainer = itemContainer,
                    contentPadding = contentPadding,
                    modifier = Modifier.weight(1f),
                    onAlbumPlay = onAlbumPlay,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onMicrophoneClick: (() -> Unit)?,
    microphoneContentDescription: String,
    isQuerySubmitted: Boolean,
    focusRequester: FocusRequester,
    placeholder: String,
    backLabel: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
    history: List<String> = emptyList(),
    onHistoryItemClick: (String) -> Unit = {},
    onRemoveHistoryItem: (String) -> Unit = {},
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(stringResource(Res.string.search), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 20.dp))
        com.metrolist.shared.ui.sonder.SonderSearchField(
            value = query, placeholder = placeholder, onChange = onQueryChange,
            onSearch = { onSearch(query); keyboard?.hide() },
        )
    }
}
