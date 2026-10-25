package com.music.bitchord.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The orders a playlist's songs can be shown in; the first is the playlist's own. */
val PLAYLIST_SORTS = listOf("Playlist order", "Title A–Z", "Artist A–Z")

/** The orders the library's and Liked songs' lists can be shown in. */
val SONG_SORTS = listOf("Recently added", "Title A–Z", "Artist A–Z")

/**
 * One sheet for the list filters, shared by Liked songs, playlists and the
 * library's songs: the order to show, and whether only downloaded songs count.
 *
 * The library's old sort controls were a label that silently cycled to the next
 * order when tapped. Here every option is listed, the current one is ticked,
 * and nothing changes until one is chosen.
 */
@Composable
fun SortSheet(
    title: String,
    options: List<String>,
    selected: Int,
    downloadedOnly: Boolean? = null,
    onSelect: (Int) -> Unit,
    onDownloadedOnly: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val c = Glow.colors
    GlowSheet(onDismiss) {
        Text(title, style = GlowType.title, color = c.text)
        Spacer(Modifier.height(6.dp))
        options.forEachIndexed { i, label ->
            val active = i == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) c.glass else Color.Transparent)
                    .clickable { onSelect(i); onDismiss() }
                    .padding(horizontal = 10.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Sort, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text(label, style = GlowType.body.copy(fontSize = 16.sp), color = c.text, modifier = Modifier.weight(1f))
                if (active) Icon(Icons.Rounded.Check, "Selected", tint = c.text, modifier = Modifier.size(20.dp))
            }
        }
        if (downloadedOnly != null && onDownloadedOnly != null) {
            GroupLabel("Show", Modifier.padding(start = 10.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Downloaded only", style = GlowType.body.copy(fontSize = 16.sp), color = c.text, modifier = Modifier.weight(1f))
                Switch(
                    checked = downloadedOnly,
                    onCheckedChange = onDownloadedOnly,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = c.onPrimary,
                        checkedTrackColor = c.primary,
                    ),
                )
            }
        }
    }
}

/** The small glass pill that opens [SortSheet], showing the order in use. */
@Composable
fun SortPill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Glow.colors
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.glass)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.AutoMirrored.Rounded.Sort, null, tint = c.text, modifier = Modifier.size(18.dp))
        Text(label, style = GlowType.body, color = c.text)
    }
}
