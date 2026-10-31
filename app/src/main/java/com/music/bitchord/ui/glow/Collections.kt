package com.music.bitchord.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis

/** How a list of songs can be ordered. [DEFAULT] is the list's own order (as added, as liked). */
enum class SongOrder(val label: String) {
    DEFAULT("Default"),
    TITLE("Title A–Z"),
    ARTIST("Artist A–Z"),
    ALBUM("Album"),
    LONGEST("Longest first"),
    SHORTEST("Shortest first"),
    ;

    fun apply(songs: List<Song>): List<Song> = when (this) {
        DEFAULT -> songs
        TITLE -> songs.sortedBy { it.title.lowercase() }
        ARTIST -> songs.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
        ALBUM -> songs.sortedWith(compareBy({ it.albumName.orEmpty().lowercase() }, { it.trackNumber ?: Int.MAX_VALUE }))
        LONGEST -> songs.sortedByDescending { it.durationMillis() }
        SHORTEST -> songs.sortedBy { it.durationMillis() }
    }
}

/** Songs whose title, artist or album has [query] in it. */
fun List<Song>.matching(query: String): List<Song> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter {
        it.title.contains(q, true) || it.artist.contains(q, true) || it.albumName.orEmpty().contains(q, true)
    }
}

/**
 * A glass chip showing the current order; tapping it lists every option, the chosen one
 * ticked. [label] names an option, so a page can call its default "Recently liked".
 */
@Composable
fun <T> SortMenu(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    /** Just the ↓↑ icon, as in the Library toolbar; the choice shows in the menu. */
    iconOnly: Boolean = false,
    onSelect: (T) -> Unit,
) {
    val c = Glow.colors
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        if (iconOnly) {
            IconTap(Icons.Rounded.SwapVert, "Sort: ${label(selected)}", size = 40.dp, iconSize = 22.dp) { open = true }
        } else Row(
            Modifier.clip(RoundedCornerShape(10.dp)).background(c.glass).clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Sort, null, tint = c.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label(selected), style = GlowType.body, color = c.text, maxLines = 1)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = c.sheet,
            shape = RoundedCornerShape(14.dp),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), style = GlowType.body, color = c.text) },
                    trailingIcon = {
                        if (option == selected) Icon(Icons.Rounded.Check, null, tint = c.text, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/**
 * The top of a song collection, as Liked songs draws it: the cover beside the title, a
 * line or two about it, then the sort and the actions on one row, Play at the end.
 */
@Composable
fun CollectionHeader(
    cover: @Composable () -> Unit,
    title: String,
    lines: List<String>,
    downloaded: Boolean,
    modifier: Modifier = Modifier,
    onFirstLineClick: (() -> Unit)? = null,
    description: String? = null,
    sort: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val c = Glow.colors
    Column(modifier) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                cover()
                if (downloaded) DownloadedBadge(Modifier.align(Alignment.TopEnd).padding(8.dp), size = 24.dp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = GlowType.display.copy(fontSize = 26.sp), color = c.text, maxLines = 2)
                lines.forEachIndexed { i, line ->
                    Text(
                        line,
                        style = GlowType.bodyRegular,
                        color = c.textSecondary,
                        maxLines = 1,
                        modifier = if (i == 0 && onFirstLineClick != null) {
                            Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onFirstLineClick)
                        } else {
                            Modifier
                        },
                    )
                }
                if (downloaded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DownloadedMark(c.textSecondary)
                        Spacer(Modifier.width(6.dp))
                        Text("Downloaded", style = GlowType.secondary, color = c.textSecondary)
                    }
                }
            }
        }
        if (!description.isNullOrBlank()) {
            Text(
                description,
                style = GlowType.secondary.copy(fontSize = 14.sp),
                color = c.textSecondary,
                maxLines = 3,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            sort()
            Spacer(Modifier.weight(1f))
            actions()
        }
        Spacer(Modifier.height(10.dp))
    }
}

/**
 * The parts of a release's subtitle worth showing beside counts the page works out itself:
 * "Playlist", "12 tracks", "47 minutes" and the like go, since the page shows its own
 * "12 songs · 47 min" (YouTube's and ours used to sit side by side, saying it twice).
 */
fun subtitleWithoutCounts(subtitle: String): List<String> =
    subtitle.split(" • ", " · ").map { it.trim() }.filter { part ->
        part.isNotEmpty() && !COUNT.matches(part) && !DURATION.matches(part) && !part.equals("Playlist", true)
    }

private val COUNT = Regex("(?i)^[\\d.,]+\\s*[KkMm]?\\s+(tracks?|songs?|episodes?|videos?)$")
private val DURATION = Regex("(?i)^(\\d+\\+?\\s*(hours?|hrs?|minutes?|mins?|seconds?|secs?)[, ]*)+$")

/** A small filled heart for a song the listener likes, beside its ⋯. */
@Composable
fun LikedMark(videoId: String?) {
    if (videoId == null) return
    val g = LocalGlow.current
    val likes by g.vm.likeStatuses.collectAsStateWithLifecycle()
    if (likes[videoId] == LikeStatus.LIKE) {
        Icon(Icons.Rounded.Favorite, "Liked", tint = Glow.colors.textSecondary, modifier = Modifier.padding(end = 2.dp).size(16.dp))
    }
}
