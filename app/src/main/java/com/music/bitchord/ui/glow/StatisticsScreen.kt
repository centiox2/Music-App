package com.music.bitchord.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.RankedEntry
import com.music.bitchord.data.stats.RankedSong
import com.music.bitchord.data.stats.StatsPeriod
import com.music.bitchord.data.stats.StatsReport
import com.music.bitchord.data.stats.report
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Values from the "Statistics · Overview / Detailed" designs.
private val HeroColor = Color(0xFF1D3B3A)
private val HeroGlow = Color(0xFF5FB3AD)
private val PageGlow = Color(0xFF2F5A55)
private val HeatScale = listOf(Color(0x0FFFFFFF), Color(0xFF1D3B3A), Color(0xFF2F5A55), Color(0xFF5FB3AD), Color(0xFFBFE3D8))
private const val HEAT_WEEKS = 11

/** What the page last showed, so coming back opens on the same view and period. */
private object StatsPageMemory {
    var detailed = false
    var period = StatsPeriod.LIFETIME
}

/** Listening statistics: an Overview (time, activity, habits) and a Detailed view (top charts). */
@Composable
fun StatisticsScreen() {
    val g = LocalGlow.current
    val c = Glow.colors
    var detailed by rememberSaveable { mutableStateOf(StatsPageMemory.detailed) }
    var periodIndex by rememberSaveable { mutableStateOf(StatsPageMemory.period.ordinal) }
    val period = StatsPeriod.entries[periodIndex]
    val report by produceState<StatsReport?>(null, period) { value = ListeningStats.report(period) }

    GlowScreen(glowColor = PageGlow) {
        Column(Modifier.fillMaxSize()) {
            // Fixed header: back, title, Overview / Detailed.
            Column(Modifier.statusBarsPadding().padding(start = 8.dp, end = 20.dp, top = 14.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTap(Icons.AutoMirrored.Rounded.ArrowBackIos, "Back", size = 44.dp, iconSize = 20.dp) { g.pop() }
                    Spacer(Modifier.width(4.dp))
                    Text("Statistics", style = GlowType.display.copy(fontSize = 26.sp), color = c.text, maxLines = 1)
                }
                Spacer(Modifier.height(12.dp))
                Segmented(listOf("Overview", "Detailed"), if (detailed) 1 else 0, Modifier.padding(start = 12.dp)) {
                    detailed = it == 1
                    StatsPageMemory.detailed = detailed
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding()) {
                item(key = "periods") {
                    ChipRow(StatsPeriod.entries.map { it.label }, periodIndex) {
                        periodIndex = it
                        StatsPageMemory.period = StatsPeriod.entries[it]
                    }
                }
                val r = report
                when {
                    r == null -> item(key = "loading") { SkeletonRows(5, Modifier.padding(top = 16.dp)) }
                    r.isEmpty && r.dailyMs.isEmpty() -> item(key = "empty") {
                        EmptyState(
                            Icons.Rounded.BarChart,
                            "Nothing to count yet",
                            "Listen to some music and your statistics will start to fill in. They're kept on this phone only.",
                            modifier = Modifier.padding(top = 30.dp),
                        )
                    }
                    detailed -> detailedContent(g, r)
                    else -> overviewContent(g, r) { detailed = true; StatsPageMemory.detailed = true }
                }
            }
        }
    }
}

// ---- Overview ----

private fun androidx.compose.foundation.lazy.LazyListScope.overviewContent(g: GlowState, r: StatsReport, openDetailed: () -> Unit) {
    item(key = "hero") {
        GlowCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), padding = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Time listened · ${r.period.label}", style = GlowType.secondary, color = Color.White.copy(alpha = 0.75f))
                Text(
                    formatHoursMinutes(r.totalMs),
                    style = GlowType.display.copy(fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em, lineHeight = 46.sp),
                    color = Color.White,
                    maxLines = 1,
                )
                Text(heroLine(r), style = GlowType.secondary, color = Color.White.copy(alpha = 0.75f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TotalTile("Tracks", r.plays, Modifier.weight(1f), openDetailed)
            TotalTile("Albums", r.albums, Modifier.weight(1f), openDetailed)
            TotalTile("Artists", r.artists, Modifier.weight(1f), openDetailed)
        }
    }
    item(key = "activity") { ActivityCard(r, Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) }
    item(key = "hours") { HoursCard(r, Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) }
    item(key = "facts") {
        val c = Glow.colors
        val top = r.topArtists.firstOrNull()
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)).background(c.glass).padding(horizontal = 16.dp),
        ) {
            FactRow("Busiest day", r.busiestWeekday?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: "—")
            CardDivider(start = 0.dp)
            FactRow("Longest streak", if (r.longestStreak == 1) "1 day" else "${r.longestStreak} days")
            CardDivider(start = 0.dp)
            FactRow("Top artist", top?.title ?: "—", onClick = top?.let { artist -> { g.openArtist(artist.browseId, artist.title) } })
        }
    }
}

@Composable
private fun TotalTile(label: String, value: Int, modifier: Modifier, onClick: () -> Unit) {
    val c = Glow.colors
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(c.glass).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = GlowType.secondary.copy(fontSize = 12.sp), color = c.textSecondary, maxLines = 1)
        Text(
            "%,d".format(value),
            style = GlowType.title.copy(fontSize = 20.sp, fontFeatureSettings = "tnum"),
            color = c.text,
            maxLines = 1,
        )
    }
}

@Composable
private fun FactRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    val c = Glow.colors
    Row(
        Modifier.fillMaxWidth().height(52.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = GlowType.bodyRegular.copy(fontSize = 14.sp), color = c.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = GlowType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = c.text, maxLines = 1)
    }
}

/** The last 11 weeks, a column per week (Monday first); tap a day to see its count. */
@Composable
private fun ActivityCard(r: StatsReport, modifier: Modifier) {
    val c = Glow.colors
    val today = remember { LocalDate.now() }
    val firstMonday = remember(today) { today.with(DayOfWeek.MONDAY).minusWeeks((HEAT_WEEKS - 1).toLong()) }
    var selected by remember { mutableStateOf(today) }
    // Plays where they were counted; time for days recorded before that.
    val usePlays = r.dailyPlays.values.any { it > 0 }
    val value: (LocalDate) -> Long = { d -> if (usePlays) (r.dailyPlays[d] ?: 0).toLong() else (r.dailyMs[d] ?: 0L) }
    val max = remember(r) {
        (0 until HEAT_WEEKS * 7).maxOfOrNull { value(firstMonday.plusDays(it.toLong())) } ?: 0L
    }
    fun level(d: LocalDate): Int {
        val v = value(d)
        if (v <= 0 || max <= 0) return 0
        return (1 + (3.0 * v / max).toInt()).coerceIn(1, 4)
    }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.glass).padding(16.dp)) {
        CardTitle("Activity", "Last $HEAT_WEEKS weeks")
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.25f)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selected.format(DateTimeFormatter.ofPattern("EEE, d MMMM")),
                style = GlowType.bodyRegular.copy(fontSize = 14.sp),
                color = c.text.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f),
            )
            if (usePlays) {
                val n = r.dailyPlays[selected] ?: 0
                Text("%,d".format(n), style = GlowType.title.copy(fontSize = 18.sp), color = c.text)
                Text(if (n == 1) " listen" else " listens", style = GlowType.bodyRegular.copy(fontSize = 14.sp), color = c.textSecondary)
            } else {
                Text(formatHoursMinutes(r.dailyMs[selected] ?: 0L), style = GlowType.title.copy(fontSize = 18.sp), color = c.text)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DayOfWeek.values().forEach { d ->
                    Box(Modifier.height(22.dp), contentAlignment = Alignment.CenterStart) {
                        Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = GlowType.secondary.copy(fontSize = 10.sp), color = c.textSecondary)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Month names over the week where each month begins.
                Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(HEAT_WEEKS) { w ->
                        val monday = firstMonday.plusWeeks(w.toLong())
                        val previous = monday.minusWeeks(1)
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            if (w == 0 || monday.month != previous.month) {
                                Text(
                                    monday.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                    style = GlowType.secondary.copy(fontSize = 10.sp),
                                    color = c.textSecondary,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
                repeat(7) { d ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(HEAT_WEEKS) { w ->
                            val day = firstMonday.plusWeeks(w.toLong()).plusDays(d.toLong())
                            val future = day.isAfter(today)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(22.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(HeatScale[if (future) 0 else level(day)])
                                    .then(if (day == selected) Modifier.border(2.dp, c.text, RoundedCornerShape(5.dp)) else Modifier)
                                    .then(if (!future) Modifier.clickable { selected = day } else Modifier),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Less", style = GlowType.secondary.copy(fontSize = 11.sp), color = c.textSecondary)
            Spacer(Modifier.weight(1f))
            HeatScale.forEach {
                Box(Modifier.padding(horizontal = 2.dp).size(14.dp).clip(RoundedCornerShape(4.dp)).background(it))
            }
            Spacer(Modifier.weight(1f))
            Text("More", style = GlowType.secondary.copy(fontSize = 11.sp), color = c.textSecondary)
        }
    }
}

/** Listening by hour of the day, the peak hour in white. */
@Composable
private fun HoursCard(r: StatsReport, modifier: Modifier) {
    val c = Glow.colors
    val usePlays = r.hourPlays.any { it > 0 }
    val values = if (usePlays) r.hourPlays.map { it.toLong() } else r.hourMs
    val max = values.maxOrNull() ?: 0L
    val peak = values.indexOf(max).takeIf { max > 0 }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.glass).padding(16.dp)) {
        CardTitle("When you listen", "By hour")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                peak?.let(::hourLabel) ?: "—",
                style = GlowType.display.copy(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
                color = c.text,
            )
            Spacer(Modifier.width(8.dp))
            if (peak != null) {
                Text(
                    "is your peak · " + if (usePlays) "%,d listens".format(max) else formatHoursMinutes(max),
                    style = GlowType.secondary,
                    color = c.textSecondary,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, v ->
                val fraction = if (max > 0) (v.toFloat() / max).coerceAtLeast(0.03f) else 0.03f
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(if (i == peak) c.text else c.accent.copy(alpha = 0.45f)),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.text.copy(alpha = 0.15f)))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 6, 12, 18, 23).forEach {
                Text(hourLabel(it), style = GlowType.secondary.copy(fontSize = 10.sp), color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun CardTitle(title: String, note: String) {
    val c = Glow.colors
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, style = GlowType.title.copy(fontSize = 18.sp), color = c.text, modifier = Modifier.weight(1f), maxLines = 1)
        Text(note, style = GlowType.secondary.copy(fontSize = 12.sp), color = c.textSecondary, modifier = Modifier.padding(bottom = 2.dp))
    }
}

// ---- Detailed ----

private fun androidx.compose.foundation.lazy.LazyListScope.detailedContent(g: GlowState, r: StatsReport) {
    item(key = "summary") {
        GlowCard(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), padding = 16.dp) {
            Column {
                Text("${r.period.label} summary", style = GlowType.secondary, color = Color.White.copy(alpha = 0.75f))
                Spacer(Modifier.height(4.dp))
                Text(
                    "You've played ${"%,d".format(r.plays)} tracks for ${formatHoursMinutes(r.totalMs)}",
                    style = GlowType.titleSmall.copy(lineHeight = 23.sp),
                    color = Color.White,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryTile("Tracks", r.plays, Modifier.weight(1f))
                    SummaryTile("Albums", r.albums, Modifier.weight(1f))
                    SummaryTile("Artists", r.artists, Modifier.weight(1f))
                }
            }
        }
    }
    if (r.songs.isNotEmpty()) item(key = "top-tracks") {
        ChartHeader("Top tracks") { g.push(Route.StatsList(StatsListKind.TRACKS, r.period.ordinal)) }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(r.songs.take(10), key = { i, s -> "t$i${s.song.videoId}" }) { i, s ->
                TrackCard(g, r, i, s)
            }
        }
    }
    if (r.topAlbums.isNotEmpty()) item(key = "top-albums") {
        ChartHeader("Top albums") { g.push(Route.StatsList(StatsListKind.ALBUMS, r.period.ordinal)) }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(r.topAlbums.take(10), key = { i, a -> "a$i${a.title}" }) { i, a ->
                AlbumCard(g, i, a)
            }
        }
    }
    if (r.topArtists.isNotEmpty()) item(key = "top-artists") {
        ChartHeader("Top artists") { g.push(Route.StatsList(StatsListKind.ARTISTS, r.period.ordinal)) }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(r.topArtists.take(10), key = { i, a -> "r$i${a.title}" }) { i, a ->
                ArtistCircle(g, i, a)
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: Int, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.22f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = GlowType.secondary.copy(fontSize = 11.sp), color = Color.White.copy(alpha = 0.7f), maxLines = 1)
        Text("%,d".format(value), style = GlowType.title.copy(fontSize = 17.sp, fontFeatureSettings = "tnum"), color = Color.White, maxLines = 1)
    }
}

@Composable
private fun ChartHeader(title: String, onSeeAll: () -> Unit) {
    val c = Glow.colors
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = GlowType.title.copy(fontSize = 18.sp), color = c.text, modifier = Modifier.weight(1f))
        TextAction("See all", color = c.textSecondary, onClick = onSeeAll)
    }
}

@Composable
private fun RankBadge(rank: Int, modifier: Modifier = Modifier, background: Color = Color.Black.copy(alpha = 0.55f), ring: Color? = null) {
    Box(
        modifier
            .defaultMinSize(minWidth = 26.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(background)
            .then(if (ring != null) Modifier.border(2.dp, ring, RoundedCornerShape(13.dp)) else Modifier)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$rank", style = GlowType.label.copy(fontWeight = FontWeight.Bold), color = Color.White)
    }
}

@Composable
private fun TrackCard(g: GlowState, r: StatsReport, index: Int, s: RankedSong) {
    val c = Glow.colors
    Column(Modifier.width(148.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(148.dp).clip(RoundedCornerShape(10.dp)).clickable { playTop(g, r, index) }) {
            Cover(s.song.thumbnailUrl, size = 148.dp, shape = RoundedCornerShape(10.dp))
            RankBadge(index + 1, Modifier.align(Alignment.TopStart).padding(8.dp))
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)).clickable { g.openSongMenu(s.song) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.MoreHoriz, "More options", tint = Color.White, modifier = Modifier.size(18.dp)) }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(38.dp).clip(CircleShape)
                    .background(Color.White).clickable { playTop(g, r, index) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Play ${s.song.title}", tint = Color(0xFF111111), modifier = Modifier.size(20.dp)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            GlowText(s.song.title, GlowType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            GlowText(playsAndTime(s.plays, s.ms), GlowType.secondary.copy(fontSize = 12.sp), color = c.textSecondary, maxLines = 1)
        }
    }
}

private fun playTop(g: GlowState, r: StatsReport, index: Int) {
    g.playList(r.songs.map { it.song }, index, "Top tracks", PlaybackSourceType.QUEUE)
}

@Composable
private fun AlbumCard(g: GlowState, index: Int, a: RankedEntry) {
    val c = Glow.colors
    Column(
        Modifier.width(148.dp).clip(RoundedCornerShape(10.dp)).clickable { openAlbum(g, a) },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(148.dp)) {
            Cover(a.artworkUrl, size = 148.dp, shape = RoundedCornerShape(10.dp))
            RankBadge(index + 1, Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            GlowText(a.title, GlowType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            GlowText(listOfNotNull(a.subtitle?.takeIf { it.isNotBlank() }, formatHoursMinutes(a.ms)).joinToString(" · "), GlowType.secondary.copy(fontSize = 12.sp), color = c.textSecondary, maxLines = 2)
        }
    }
}

private fun openAlbum(g: GlowState, a: RankedEntry) {
    val id = a.browseId
    if (id != null) g.openDetail(id, a.title, a.subtitle.orEmpty(), a.artworkUrl, BrowseType.ALBUM)
    else g.notify("Couldn't open ${a.title}")
}

@Composable
private fun ArtistCircle(g: GlowState, index: Int, a: RankedEntry) {
    val c = Glow.colors
    Column(
        Modifier.width(120.dp).clip(RoundedCornerShape(12.dp)).clickable { g.openArtist(a.browseId, a.title) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(120.dp)) {
            Cover(a.artworkUrl, size = 120.dp, shape = CircleShape)
            RankBadge(index + 1, Modifier.padding(4.dp), background = c.base, ring = c.base)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            GlowText(a.title, GlowType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            GlowText(playsAndTime(a.plays, a.ms, hoursOnly = true), GlowType.secondary.copy(fontSize = 12.sp), color = c.textSecondary, maxLines = 1)
        }
    }
}

// ---- See all ----

enum class StatsListKind(val title: String) { TRACKS("Top tracks"), ALBUMS("Top albums"), ARTISTS("Top artists") }

/** Every track, album or artist of a period, ranked. */
@Composable
fun StatsListScreen(route: Route.StatsList) {
    val g = LocalGlow.current
    val c = Glow.colors
    val period = StatsPeriod.entries.getOrElse(route.period) { StatsPeriod.LIFETIME }
    val report by produceState<StatsReport?>(null, period) { value = ListeningStats.report(period) }
    GlowScreen(glowColor = PageGlow) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pageContentPadding()) {
            item {
                TopBar(onBack = { g.pop() })
                Text(route.kind.title, style = GlowType.display.copy(fontSize = 30.sp), color = c.text, modifier = Modifier.padding(horizontal = 20.dp))
                Text(period.label, style = GlowType.secondary, color = c.textSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                Spacer(Modifier.height(10.dp))
            }
            val r = report ?: run {
                item { SkeletonRows(8) }
                return@LazyColumn
            }
            when (route.kind) {
                StatsListKind.TRACKS -> itemsIndexed(r.songs, key = { i, s -> "t$i${s.song.videoId}" }) { i, s ->
                    SongRow(
                        s.song.title,
                        "${s.song.artist} · ${playsAndTime(s.plays, s.ms)}",
                        s.song.thumbnailUrl,
                        leading = { RankedCover(i, s.song.thumbnailUrl, circle = false) },
                        onClick = { playTop(g, r, i) },
                        videoId = s.song.videoId,
                        trailing = { MoreButton { g.openSongMenu(s.song) } },
                    )
                }
                StatsListKind.ALBUMS -> itemsIndexed(r.topAlbums, key = { i, a -> "a$i${a.title}" }) { i, a ->
                    SongRow(
                        a.title,
                        listOfNotNull(a.subtitle?.takeIf { it.isNotBlank() }, playsAndTime(a.plays, a.ms)).joinToString(" · "),
                        a.artworkUrl,
                        leading = { RankedCover(i, a.artworkUrl, circle = false) },
                        onClick = { openAlbum(g, a) },
                    )
                }
                StatsListKind.ARTISTS -> itemsIndexed(r.topArtists, key = { i, a -> "r$i${a.title}" }) { i, a ->
                    SongRow(
                        a.title,
                        playsAndTime(a.plays, a.ms),
                        a.artworkUrl,
                        leading = { RankedCover(i, a.artworkUrl, circle = true) },
                        onClick = { g.openArtist(a.browseId, a.title) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RankedCover(index: Int, url: String?, circle: Boolean) {
    val c = Glow.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${index + 1}",
            style = GlowType.label.copy(fontSize = 14.sp, fontFeatureSettings = "tnum"),
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp),
        )
        Spacer(Modifier.width(6.dp))
        Cover(url, size = 48.dp, shape = if (circle) CircleShape else RoundedCornerShape(8.dp))
    }
}

// ---- Shared ----

/** The teal summary card with a soft light in its top-right corner. */
@Composable
private fun GlowCard(modifier: Modifier, padding: Dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HeroColor)
            // A blurred 200dp circle hanging off the top-right corner, drawn as a soft gradient.
            .drawBehind {
                val radius = 140.dp.toPx()
                val center = Offset(size.width - 40.dp.toPx(), 20.dp.toPx())
                drawCircle(
                    Brush.radialGradient(listOf(HeroGlow.copy(alpha = 0.5f), HeroGlow.copy(alpha = 0f)), center = center, radius = radius),
                    radius = radius,
                    center = center,
                )
            },
    ) {
        Box(Modifier.padding(padding), content = content)
    }
}

private fun heroLine(r: StatsReport): String {
    val days = r.totalMs / 86_400_000.0
    val amount = when {
        days >= 1.0 -> "About ${days.toInt()} ${if (days.toInt() == 1) "day" else "days"} of music"
        r.totalMs >= 3_600_000 -> "About ${r.totalMs / 3_600_000} hours of music"
        else -> "${r.totalMs / 60_000} minutes of music"
    }
    return if (r.perDayMs > 0) "$amount · ${formatHoursMinutes(r.perDayMs)} a day" else amount
}

/** "762h 56m", "14m". */
fun formatHoursMinutes(ms: Long): String {
    val minutes = ms / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${"%,d".format(h)}h ${m}m" else "${m}m"
}

private fun playsAndTime(plays: Int, ms: Long, hoursOnly: Boolean = false): String {
    val count = if (plays == 1) "1 play" else "${"%,d".format(plays)} plays"
    val time = if (hoursOnly && ms >= 3_600_000) "${"%,d".format(ms / 3_600_000)}h" else formatHoursMinutes(ms)
    return "$count · $time"
}

private fun hourLabel(hour: Int): String = when {
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}
