package com.music.bitchord.ui.glow

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.download.DownloadState
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.AudioOutputStatus
import com.music.bitchord.playback.SleepTimer
import kotlinx.coroutines.delay

@Composable
fun SongMenuSheet(song: Song, onDismiss: () -> Unit) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val likes by vm.likeStatuses.collectAsStateWithLifecycle()
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val saved by Downloads.saved.collectAsStateWithLifecycle()
    val active by Downloads.active.collectAsStateWithLifecycle()
    val sleepDeadline by SleepTimer.deadline.collectAsStateWithLifecycle()
    val sleepAfterTrack by SleepTimer.afterTrack.collectAsStateWithLifecycle()
    val speed by AppSettings.playbackSpeed.collectAsStateWithLifecycle()
    val liked = likes[song.videoId] == LikeStatus.LIKE
    val isCurrent = g.player.song?.videoId == song.videoId
    val close: (() -> Unit) -> Unit = { action -> onDismiss(); action() }
    val sheetColor = if (g.songMenuFromPlayer) playerSheetColor(song.thumbnailUrl) else null

    GlowSheet(onDismiss, color = sheetColor) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(song.thumbnailUrl, size = 52.dp, shape = RoundedCornerShape(10.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                GlowText(song.title, GlowType.titleSmall, maxLines = 1)
                GlowText(
                    listOfNotNull(song.artist, song.albumName).joinToString(" · "),
                    GlowType.secondary.copy(fontSize = 14.sp),
                    color = c.textSecondary,
                    maxLines = 1,
                )
            }
            if (signedIn) {
                IconTap(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Unlike" else "Like") {
                    g.toggleLike(song)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isCurrent) {
                // Queueing the song that's already playing only duplicated it, so the player's menu leaves that out.
                QuickTile(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist", Modifier.weight(1f)) { close { g.saveToPlaylist = listOf(song) } }
                QuickTile(Icons.Rounded.Podcasts, "Start radio", Modifier.weight(1f)) { close { g.actions.startRadio(song) } }
                QuickTile(Icons.Rounded.IosShare, "Share", Modifier.weight(1f)) { close { g.share(song) } }
            } else {
                QuickTile(Icons.Rounded.SkipNext, "Play next", Modifier.weight(1f)) { close { g.actions.playNext(song) } }
                QuickTile(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue", Modifier.weight(1f)) { close { g.actions.addToQueue(song) } }
                QuickTile(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist", Modifier.weight(1f)) { close { g.saveToPlaylist = listOf(song) } }
                QuickTile(Icons.Rounded.IosShare, "Share", Modifier.weight(1f)) { close { g.share(song) } }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!isCurrent) MenuRow(Icons.Rounded.Podcasts, "Start radio") { close { g.actions.startRadio(song) } }
        // Both are looked up when the song didn't arrive with them.
        MenuRow(Icons.Rounded.Album, "Go to album") { close { g.playerOpen = false; g.openAlbumOf(song) } }
        val artistId = song.artistId ?: song.artists.firstOrNull { it.browseId != null }?.browseId
        MenuRow(Icons.Rounded.Person, "Go to artist") {
            close {
                g.playerOpen = false
                if (artistId != null) g.openArtist(artistId, song.artist) else g.openArtistOf(song)
            }
        }
        when {
            song.videoId in saved -> MenuRow(Icons.Rounded.Delete, "Remove download") { close { g.actions.removeDownload(song.videoId) } }
            active[song.videoId] is DownloadState.Running || active[song.videoId] == DownloadState.Queued ->
                MenuRow(Icons.Rounded.Close, "Cancel download") { close { Downloads.cancel(song.videoId) } }
            else -> MenuRow(Icons.Rounded.Download, "Download") { close { g.actions.download(listOf(song)) } }
        }
        if (isCurrent) {
            val sleepLabel = when {
                sleepAfterTrack -> "End of song"
                sleepDeadline != null -> "On"
                else -> "Off"
            }
            MenuRow(Icons.Rounded.Bedtime, "Sleep timer", value = sleepLabel) { close { g.sleepTimerOpen = true } }
            MenuRow(Icons.Rounded.Tune, "Equalizer & audio", value = "%.2f×".format(speed)) {
                close { g.playerOpen = false; g.push(Route.Equalizer) }
            }
            MenuRow(Icons.Rounded.SwapHoriz, if (song.isVideo) "Switch to song version" else "Switch to video version") {
                close { g.actions.switchVersion(song, toVideo = !song.isVideo) }
            }
            MenuRow(Icons.Rounded.Groups, "Listen together") { close { g.devicesOpen = true } }
        }
        if (signedIn) {
            MenuRow(Icons.Rounded.ThumbDown, if (likes[song.videoId] == LikeStatus.DISLIKE) "Undo dislike" else "Not interested") {
                close {
                    val previous = vm.toggleDislike(song.videoId)
                    if (previous != null &&
                        com.music.bitchord.shouldSkipAfterDislike(previous, song.videoId, g.player.song?.videoId) &&
                        g.player.hasNext
                    ) {
                        g.controller?.seekToNext()
                    }
                }
            }
        }
        g.songMenuPlaylist?.let { playlist ->
            if (song.setVideoId != null) {
                MenuRow(Icons.Rounded.Delete, "Remove from this playlist", danger = true) {
                    close {
                        vm.removeFromPlaylist(playlist, song) { ok -> if (!ok) g.notify("Couldn't remove ${song.title}") }
                        g.notify("Removed from playlist", detail = song.title, artworkUrl = song.thumbnailUrl)
                    }
                }
            }
        }
        MenuRow(Icons.Rounded.Info, "Song details") { close { g.songDetails = song } }
    }
}

@Composable
private fun QuickTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Glow.colors
    Column(
        modifier
            .height(70.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.glass)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = c.text, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = GlowType.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = c.text, maxLines = 1)
    }
}

// ---- Save to playlist ----

@Composable
fun SaveToPlaylistSheet(songs: List<Song>, onDismiss: () -> Unit) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val loading by vm.playlistsLoading.collectAsStateWithLifecycle()
    val local by LocalPlaylistStore.playlists.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) { vm.loadPlaylists() }
    val first = songs.firstOrNull() ?: return
    val shown = playlists.filter { query.isBlank() || it.title.contains(query, true) }

    GlowSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(first.thumbnailUrl, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Save to playlist", style = GlowType.title, color = c.text)
                GlowText(
                    if (songs.size == 1) "${first.title} · ${first.artist}" else "${songs.size} songs",
                    GlowType.secondary.copy(fontSize = 14.sp),
                    color = c.textSecondary,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SearchField(query, { query = it }, "Find a playlist", Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onDismiss()
                        g.newPlaylistFor = songs
                        g.newPlaylistOpen = true
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(c.primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, null, tint = c.onPrimary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("New playlist", style = GlowType.body.copy(fontWeight = FontWeight.SemiBold), color = c.text)
                }
            }
            if (!signedIn) {
                item {
                    Text(
                        "Sign in to save to your YouTube Music playlists. New playlists can still be kept on this phone.",
                        style = GlowType.secondary,
                        color = c.textSecondary,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }
            if (loading && playlists.isEmpty()) item { SkeletonRows(3) }
            items(shown, key = { it.playlistId }) { playlist ->
                val checked = playlist.playlistId in chosen
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (checked) chosen.remove(playlist.playlistId) else chosen.add(playlist.playlistId)
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(playlist.thumbnailUrl, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        GlowText(playlist.title, GlowType.body, maxLines = 1)
                        GlowText(playlist.subtitle, GlowType.secondary, color = c.textSecondary, maxLines = 1)
                    }
                    GlowCheckbox(checked)
                }
            }
            items(local, key = { "local" + it.id }) { playlist ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        LocalPlaylistStore.savePlaylist(playlist.title, (playlist.songs + songs).distinctBy { it.videoId })
                        g.notify("Saved to ${playlist.title}", detail = first.title, artworkUrl = first.thumbnailUrl)
                        onDismiss()
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(playlist.songs.firstOrNull()?.thumbnailUrl, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        GlowText(playlist.title, GlowType.body, maxLines = 1)
                        GlowText("${playlist.songs.size} songs · On this phone", GlowType.secondary, color = c.textSecondary, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PillButton("Done", Modifier.fillMaxWidth()) {
            val targets = playlists.filter { it.playlistId in chosen }
            if (targets.isNotEmpty()) addSongsTo(g, targets, songs)
            onDismiss()
        }
    }
}

private fun addSongsTo(g: GlowState, targets: List<UserPlaylist>, songs: List<Song>) {
    val first = songs.first()
    songs.forEach { song ->
        g.vm.addToPlaylists(targets, song) { added, already, failed ->
            if (song != first) return@addToPlaylists
            val name = targets.singleOrNull()?.title ?: "${targets.size} playlists"
            when {
                failed > 0 && added == 0 -> g.notify("Couldn't save to $name")
                added == 0 && already > 0 -> g.notify("Already in $name", artworkUrl = first.thumbnailUrl)
                else -> g.notify(
                    "Saved to $name",
                    detail = if (songs.size == 1) first.title else "${songs.size} songs",
                    artworkUrl = first.thumbnailUrl,
                    actionLabel = targets.singleOrNull()?.let { "View" },
                    action = targets.singleOrNull()?.let { p -> { g.playerOpen = false; g.openPlaylist(p) } },
                )
            }
        }
    }
}

// ---- New playlist ----

@Composable
fun NewPlaylistSheet(songs: List<Song>?, onDismiss: () -> Unit) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var account by remember { mutableStateOf(if (signedIn) 0 else 1) }
    GlowSheet(onDismiss) {
        Text("New playlist", style = GlowType.title.copy(fontSize = 22.sp), color = c.text)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Box(
                Modifier
                    .size(86.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.glass)
                    .border(1.dp, c.textTertiary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Cover(songs?.firstOrNull()?.thumbnailUrl, size = 86.dp, shape = RoundedCornerShape(12.dp))
            }
            Spacer(Modifier.width(14.dp))
            GlowTextField(name, { name = it }, Modifier.weight(1f), label = "Name", placeholder = "My playlist")
        }
        Spacer(Modifier.height(14.dp))
        GlowTextField(description, { description = it }, label = "Description (optional)", placeholder = "What's this playlist for?", singleLine = false)
        Spacer(Modifier.height(14.dp))
        Text("Save to", style = GlowType.secondary, color = c.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
        Segmented(listOf("YouTube Music account", "This phone only"), account) { if (signedIn || it == 1) account = it }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Cancel", Modifier.weight(1f), primary = false, onClick = onDismiss)
            PillButton("Create", Modifier.weight(1f), enabled = name.isNotBlank()) {
                val title = name.trim()
                val list = songs.orEmpty()
                if (account == 1) {
                    LocalPlaylistStore.savePlaylist(title, list)
                    g.notify("Created $title", detail = "On this phone", artworkUrl = list.firstOrNull()?.thumbnailUrl)
                } else {
                    vm.createPlaylistWithVideoIds(title, PlaylistPrivacy.PRIVATE, list.map { it.videoId }, list) { browseId, created, local ->
                        g.notify(
                            if (local) "Created $created on this phone" else "Created $created",
                            artworkUrl = list.firstOrNull()?.thumbnailUrl,
                            secondaryLabel = browseId?.let { "View" },
                            secondaryAction = browseId?.let { id -> { g.openDetail(id, created) } },
                        )
                    }
                }
                onDismiss()
            }
        }
    }
}

// ---- Sleep timer ----

@Composable
fun SleepTimerSheet(onDismiss: () -> Unit) {
    val c = Glow.colors
    val deadline by SleepTimer.deadline.collectAsStateWithLifecycle()
    val minutes by SleepTimer.minutes.collectAsStateWithLifecycle()
    val afterTrack by SleepTimer.afterTrack.collectAsStateWithLifecycle()
    var remaining by remember { mutableLongStateOf(SleepTimer.remainingMs() ?: 0L) }
    LaunchedEffect(deadline) {
        while (deadline != null) {
            remaining = SleepTimer.remainingMs() ?: 0L
            delay(1000)
        }
    }
    var custom by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableStateOf("") }

    GlowSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bedtime, null, tint = c.text, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text("Sleep timer", style = GlowType.title.copy(fontSize = 22.sp), color = c.text)
        }
        Spacer(Modifier.height(16.dp))
        if (deadline != null || afterTrack) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.glass).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val total = (minutes ?: 1) * 60_000L
                val frac = if (afterTrack) 1f else (remaining.toFloat() / total).coerceIn(0f, 1f)
                androidx.compose.foundation.Canvas(Modifier.size(52.dp)) {
                    drawArc(c.text, -90f, 360f * frac, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Music stops", style = GlowType.secondary, color = c.textSecondary)
                    Text(
                        if (afterTrack) "After this song" else "in " + remaining.formatClock(),
                        style = GlowType.title.copy(fontSize = 26.sp),
                        color = c.text,
                    )
                }
                if (!afterTrack) {
                    Text(
                        "+5 min",
                        style = GlowType.label,
                        color = c.text,
                        modifier = Modifier.clip(CircleShape).background(c.glassStrong)
                            .clickable { SleepTimer.start(((SleepTimer.remainingMs() ?: 0L) / 60_000L).toInt() + 5) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        val selected = when {
            afterTrack -> "end"
            deadline == null -> "off"
            minutes in SleepTimer.PRESETS -> "m$minutes"
            else -> "custom"
        }
        TimerOption("Off", selected == "off") { SleepTimer.cancel() }
        SleepTimer.PRESETS.forEach { m ->
            TimerOption(if (m == 60) "1 hour" else "$m minutes", selected == "m$m") { SleepTimer.start(m) }
        }
        TimerOption("End of this song", selected == "end") { SleepTimer.startAfterTrack() }
        TimerOption("Custom…", selected == "custom" || custom) { custom = true }
        if (custom) {
            Row(verticalAlignment = Alignment.Bottom) {
                GlowTextField(customMinutes, { v -> customMinutes = v.filter(Char::isDigit).take(3) }, Modifier.weight(1f), label = "Minutes", placeholder = "90")
                Spacer(Modifier.width(10.dp))
                PillButton("Start", height = 50.dp, enabled = (customMinutes.toIntOrNull() ?: 0) > 0) {
                    SleepTimer.start(customMinutes.toInt())
                    custom = false
                }
            }
        }
    }
}

@Composable
private fun TimerOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = GlowType.bodyRegular.copy(fontSize = 17.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = Glow.colors.text,
            modifier = Modifier.weight(1f),
        )
        GlowRadio(selected)
    }
}

// ---- Song details ----

@Composable
fun SongDetailsSheet(song: Song, onDismiss: () -> Unit) {
    val g = LocalGlow.current
    val c = Glow.colors
    val context = LocalContext.current
    val stats by NerdStats.current.collectAsStateWithLifecycle()
    val output by AudioOutputStatus.current.collectAsStateWithLifecycle()
    val isCurrent = g.player.song?.videoId == song.videoId
    val url = "https://music.youtube.com/watch?v=${song.videoId}"

    GlowSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(song.thumbnailUrl, size = 64.dp, shape = RoundedCornerShape(10.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                GlowText(song.title, GlowType.title, maxLines = 2)
                GlowText(song.artist, GlowType.bodyRegular, color = c.textSecondary, maxLines = 1)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Details", style = GlowType.section, color = c.textSecondary)
        val length = (if (isCurrent && g.player.durationMs > 0) g.player.durationMs else song.durationMillis()).takeIf { it > 0 }
        listOfNotNull(
            song.albumName?.let { "Album" to it },
            song.artists.takeIf { it.size > 1 }?.let { a -> "Artists" to a.joinToString(", ") { it.name } },
            length?.let { "Length" to it.formatClock() },
            (if (song.isVideo) "Type" to "Music video" else "Type" to "Song"),
            song.isExplicit?.takeIf { it }?.let { "Explicit" to "Yes" },
        ).forEach { (k, v) -> DetailLine(k, v) }
        if (isCurrent && stats != null) {
            val s = stats!!
            Spacer(Modifier.height(16.dp))
            Text("Audio", style = GlowType.section, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            val tiles = listOfNotNull(
                NerdStats.codecLabel(s.mimeType)?.let { "Format" to it },
                s.bitrateKbps?.let { "Bitrate" to "$it kbps" },
                s.sampleRateHz?.let { "Sample rate" to "%.1f kHz".format(it / 1000f).replace(".0 ", " ") },
                output.loudnessGainDb?.let { "Loudness" to "%+.1f dB".format(it) } ?: s.bitDepth?.let { "Bit depth" to "$it-bit" },
            )
            tiles.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    pair.forEach { (label, value) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.glass).padding(12.dp)) {
                            Text(label, style = GlowType.caption.copy(fontSize = 12.sp), color = c.textSecondary)
                            Text(value, style = GlowType.body.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp), color = c.text)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Copy link", Modifier.weight(1f), primary = false, height = 48.dp) {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText(song.title, url))
                g.notify("Link copied")
            }
            PillButton("Open in YouTube Music", Modifier.weight(1.4f), primary = false, height = 48.dp) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    val c = Glow.colors
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(label, style = GlowType.bodyRegular.copy(fontSize = 16.sp), color = c.textSecondary, modifier = Modifier.weight(1f))
            Text(value, style = GlowType.bodyRegular.copy(fontSize = 16.sp), color = c.text, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
    }
}
