package com.metrolist.shared.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.albums
import com.metrolist.shared.generated.resources.artists
import com.metrolist.shared.generated.resources.back
import com.metrolist.shared.generated.resources.statistics
import com.metrolist.shared.generated.resources.statistics_activity
import com.metrolist.shared.generated.resources.statistics_calendar_mode
import com.metrolist.shared.generated.resources.statistics_continuous_mode
import com.metrolist.shared.generated.resources.statistics_custom_dates
import com.metrolist.shared.generated.resources.statistics_lifetime
import com.metrolist.shared.generated.resources.statistics_listening_habits
import com.metrolist.shared.generated.resources.statistics_no_data
import com.metrolist.shared.generated.resources.statistics_overview
import com.metrolist.shared.generated.resources.statistics_time
import com.metrolist.shared.generated.resources.statistics_plays_label
import com.metrolist.shared.generated.resources.share
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.screens.StatisticsDetail
import com.metrolist.shared.ui.screens.StatisticsItem
import com.metrolist.shared.ui.screens.StatisticsPeriod
import com.metrolist.shared.ui.screens.StatisticsUiState
import com.metrolist.shared.ui.screens.periodLabel
import com.metrolist.shared.ui.screens.statisticsDuration
import com.metrolist.shared.ui.statistics.ActivityHeatmap
import com.metrolist.shared.ui.statistics.HourlyActivityChart
import com.metrolist.shared.ui.statistics.StatisticsDateRange
import com.metrolist.shared.ui.statistics.StatisticsDateRangePicker
import com.metrolist.shared.ui.statistics.StatisticsPeriodMode
import com.metrolist.shared.ui.statistics.StatisticsShareDialog
import com.metrolist.shared.ui.statistics.canExportStatistics
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.sonder.SonderGroup
import com.metrolist.shared.ui.sonder.SonderIcon
import com.metrolist.shared.ui.sonder.SonderPill
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SonderStatisticsScreen(
    state: StatisticsUiState,
    onPeriodSelected: (StatisticsPeriod) -> Unit,
    onModeSelected: (StatisticsPeriodMode) -> Unit,
    onDateRangeSelected: (StatisticsDateRange) -> Unit,
    onPlayAll: (shuffle: Boolean) -> Unit,
    onRefresh: (() -> Unit)?,
    exportLabel: String,
    onExport: suspend (androidx.compose.ui.graphics.ImageBitmap) -> Boolean,
    onItemClick: (StatisticsItem) -> Unit,
    onPlayItem: ((StatisticsItem) -> Unit)? = null,
    onItemMenu: ((StatisticsItem) -> Unit)?,
    onNavigateBack: (() -> Unit)?,
    bottomContentPadding: Dp,
) {
    var detail by remember { mutableStateOf<StatisticsDetail?>(null) }
    var detailed by rememberSaveable { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    val shareSnapshot = state.copy(dateRange = state.dateRange)
    if (shareOpen) {
        StatisticsShareDialog(
            state = shareSnapshot,
            period = periodLabel(state.period),
            summary = "${state.totalPlays} plays · ${statisticsDuration(state.totalTimeMs)}",
            exportLabel = exportLabel,
            onExport = onExport,
            onDismiss = { shareOpen = false },
        )
    }
    var modeMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    if (showDatePicker) StatisticsDateRangePicker(state.dateRange, onDateRangeSelected, onDismiss = { showDatePicker = false })
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(58.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onNavigateBack?.let { SonderIcon(MetrolistIcons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back), it, glass = true) }
            Text(
                text = if (detail == null) stringResource(Res.string.statistics) else when (detail) {
                    StatisticsDetail.TRACKS -> stringResource(Res.string.statistics_plays_label)
                    StatisticsDetail.ALBUMS -> stringResource(Res.string.albums)
                    StatisticsDetail.ARTISTS -> stringResource(Res.string.artists)
                    null -> ""
                },
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (state.canExportStatistics()) SonderIcon(MetrolistIcons.Outlined.Share, exportLabel, { shareOpen = true }, glass = true)
        }

        if (detail == null) {
            SonderStatisticsTabs(selected = detailed, onSelected = { detailed = it })
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Box {
                        SonderPill(
                            text = stringResource(if (state.mode == StatisticsPeriodMode.CALENDAR) Res.string.statistics_calendar_mode else Res.string.statistics_continuous_mode),
                            onClick = { modeMenu = true },
                        )
                        DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                            StatisticsPeriodMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (mode == StatisticsPeriodMode.CALENDAR) Res.string.statistics_calendar_mode else Res.string.statistics_continuous_mode)) },
                                    onClick = { modeMenu = false; onModeSelected(mode) },
                                )
                            }
                            DropdownMenuItem(text = { Text(stringResource(Res.string.statistics_custom_dates)) }, onClick = { modeMenu = false; showDatePicker = true })
                        }
                    }
                }
                StatisticsPeriod.entries.forEach { period ->
                    item(key = "sonder_stats_period_$period") {
                        SonderPill(periodLabel(period), state.dateRange == null && state.period == period) { onPeriodSelected(period) }
                    }
                }
            }
        }

        if (detail == null) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomContentPadding + 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                if (state.isLoading && !state.hasLoaded) {
                    item { Text("Loading statistics…", Modifier.padding(20.dp), color = colors.onSurfaceVariant) }
                } else if (state.errorMessage != null) {
                    item { Text(state.errorMessage, Modifier.padding(20.dp), color = colors.error) }
                } else {
                    if (detailed) item { SonderStatisticsSummary(state) }
                    if (state.totalPlays == 0 && state.dailyStats.isEmpty()) {
                        item { Text(stringResource(Res.string.statistics_no_data), color = colors.onSurfaceVariant) }
                    } else if (detailed) {
                        item {
                            SonderStatisticsCollection(
                                title = stringResource(Res.string.statistics_plays_label),
                                items = state.tracks,
                                circular = false,
                                onSeeAll = { detail = StatisticsDetail.TRACKS },
                                onItemClick = { onPlayItem?.invoke(it) ?: onItemClick(it) },
                                onItemMenu = onItemMenu,
                            )
                        }
                        item {
                            SonderStatisticsCollection(
                                title = stringResource(Res.string.albums), items = state.albums, circular = false,
                                onSeeAll = { detail = StatisticsDetail.ALBUMS }, onItemClick = onItemClick, onItemMenu = onItemMenu,
                            )
                        }
                        item {
                            SonderStatisticsCollection(
                                title = stringResource(Res.string.artists), items = state.artists, circular = true,
                                onSeeAll = { detail = StatisticsDetail.ARTISTS }, onItemClick = onItemClick, onItemMenu = onItemMenu,
                            )
                        }
                    } else {
                        item { SonderStatisticsSummary(state) }
                        item { SonderStatisticsChartCard(stringResource(Res.string.statistics_activity)) { ActivityHeatmap(state.dailyStats, state.period, state.dateRange) } }
                        item { SonderStatisticsChartCard(stringResource(Res.string.statistics_listening_habits)) { HourlyActivityChart(state.hourlyStats) } }
                    }
                }
            }
        } else {
            val selectedItems = when (detail) {
                StatisticsDetail.TRACKS -> state.tracks
                StatisticsDetail.ALBUMS -> state.albums
                StatisticsDetail.ARTISTS -> state.artists
                null -> emptyList()
            }
            LazyColumn(contentPadding = PaddingValues(bottom = bottomContentPadding + 24.dp)) {
                itemsIndexed(selectedItems, key = { index, item -> "sonder-stat-detail-${item.id}-$index" }) { index, item ->
                    SonderStatisticsRow(index + 1, item, onClick = { if (detail == StatisticsDetail.TRACKS) onPlayItem?.invoke(item) ?: onItemClick(item) else onItemClick(item) }, onMenu = { onItemMenu?.invoke(item) })
                }
            }
        }
    }
}

@Composable
private fun SonderStatisticsTabs(selected: Boolean, onSelected: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(shape).background(colors.onSurface.copy(alpha = 0.09f)).padding(4.dp)) {
        listOf("Overview" to false, "Detailed" to true).forEach { (label, isDetailed) ->
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (selected == isDetailed) colors.primary else Color.Transparent)
                    .clickable { onSelected(isDetailed) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (selected == isDetailed) colors.onPrimary else colors.onSurfaceVariant, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SonderStatisticsSummary(state: StatisticsUiState) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1D3B3A), Color(0xFF2F5A55), Color(0xFF1D3B3A))))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${periodLabel(state.period)} summary", color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "You've played ${state.totalPlays} tracks for ${statisticsDuration(state.totalTimeMs)}",
            color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            SummaryMetric("Tracks", state.totalPlays.toString(), Modifier.weight(1f))
            SummaryMetric("Albums", state.albumCount.toString(), Modifier.weight(1f))
            SummaryMetric("Artists", state.artistCount.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.18f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SonderStatisticsCollection(
    title: String,
    items: List<StatisticsItem>,
    circular: Boolean,
    onSeeAll: () -> Unit,
    onItemClick: (StatisticsItem) -> Unit,
    onItemMenu: ((StatisticsItem) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("See all", Modifier.clip(CircleShape).clickable(onClick = onSeeAll).padding(horizontal = 10.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
        if (items.isEmpty()) {
            Text(stringResource(Res.string.statistics_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 20.dp)) {
                itemsIndexed(items.take(12), key = { index, item -> "sonder-stat-card-${item.id}-$index" }) { index, item ->
                    Column(Modifier.width(160.dp).clickable { onItemClick(item) }, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(if (circular) CircleShape else RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))) {
                            AsyncImage(item.thumbnailUrl.orEmpty(), Modifier.fillMaxSize(), contentScale = ContentScale.Crop, shape = if (circular) CircleShape else RoundedCornerShape(14.dp))
                            Box(Modifier.align(Alignment.TopStart).padding(8.dp).size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.56f)), contentAlignment = Alignment.Center) {
                                Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            if (!circular) {
                                Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(44.dp).clip(CircleShape).background(Color.White).clickable { onItemClick(item) }, contentAlignment = Alignment.Center) {
                                    Icon(MetrolistIcons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(25.dp))
                                }
                            }
                            if (onItemMenu != null) {
                                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.52f)).clickable { onItemMenu(item) }, contentAlignment = Alignment.Center) {
                                    Icon(MetrolistIcons.Filled.MoreHoriz, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun SonderStatisticsChartCard(title: String, content: @Composable () -> Unit) {
    SonderGroup(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SonderStatisticsRow(number: Int, item: StatisticsItem, onClick: () -> Unit, onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = item.isClickable, onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$number", Modifier.width(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        AsyncImage(item.thumbnailUrl.orEmpty(), Modifier.size(52.dp).clip(if (item.circularArtwork) CircleShape else RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onMenu) { Icon(MetrolistIcons.Filled.MoreHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
