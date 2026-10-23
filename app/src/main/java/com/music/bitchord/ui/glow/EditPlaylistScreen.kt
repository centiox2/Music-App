package com.music.bitchord.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import kotlin.math.roundToInt

/**
 * Rename, remove and reorder. Changes are applied on Save: a rename, one
 * removal per dropped track, and the new running order sent as moves.
 */
@Composable
fun EditPlaylistScreen(route: Route.EditPlaylist) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val stack by vm.detailStack.collectAsStateWithLifecycle()
    val page = stack.lastOrNull { it.browseId == route.browseId }
    val playlist = remember(route.browseId) { vm.editablePlaylist(route.browseId) }
    val original = (page?.songs as? UiState.Success)?.data.orEmpty()
    var name by remember { mutableStateOf(page?.title.orEmpty()) }
    val songs = remember { mutableStateListOf<Song>() }
    var loaded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(original) {
        if (!loaded && original.isNotEmpty()) {
            songs.addAll(original)
            loaded = true
        }
    }
    val rowHeight = with(LocalDensity.current) { 64.dp.toPx() }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    GlowScreen(glowUrl = page?.thumbnailUrl) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextAction("Cancel", color = c.text) { g.pop() }
                Text("Edit playlist", style = GlowType.titleSmall, color = c.text, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                PillButton("Save", height = 40.dp, enabled = playlist != null && name.isNotBlank()) {
                    val p = playlist ?: return@PillButton
                    if (name.trim() != p.title) vm.renamePlaylist(p, name.trim())
                    val removed = original.filter { o -> songs.none { it.setVideoId == o.setVideoId && it.videoId == o.videoId } }
                    removed.forEach { vm.removeFromPlaylist(p.browseId, it) }
                    val kept = original - removed.toSet()
                    if (kept.map { it.setVideoId } != songs.map { it.setVideoId }) {
                        vm.reorderPlaylist(p, kept, songs.toList()) { ok -> if (!ok) g.notify("Couldn't save the new order") }
                    }
                    g.notify("Playlist saved")
                    g.pop()
                }
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                Cover(page?.thumbnailUrl, size = 100.dp, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.width(16.dp))
                GlowTextField(name, { name = it }, Modifier.weight(1f), label = "Name")
            }
            if (playlist == null) {
                Text(
                    "Only playlists you made can be edited.",
                    style = GlowType.secondary,
                    color = c.danger,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Songs", style = GlowType.title, color = c.text, modifier = Modifier.weight(1f))
                Text("Drag to reorder", style = GlowType.secondary, color = c.textSecondary)
            }
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(songs, key = { i, s -> (s.setVideoId ?: s.videoId) + i }) { index, song ->
                    val isDragging = dragging == index
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                            .background(if (isDragging) c.sheet else Color.Transparent)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTap(Icons.Rounded.RemoveCircleOutline, "Remove", tint = c.danger) { songs.removeAt(index) }
                        Cover(song.thumbnailUrl, size = 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            GlowText(song.title, GlowType.body, maxLines = 1)
                            GlowText(song.artist, GlowType.secondary, color = c.textSecondary, maxLines = 1)
                        }
                        Box(
                            Modifier.size(44.dp).pointerInput(index) {
                                detectDragGestures(
                                    onDragStart = { dragging = index; dragOffset = 0f },
                                    onDragEnd = {
                                        val to = (index + (dragOffset / rowHeight).roundToInt()).coerceIn(0, songs.lastIndex)
                                        if (to != index) songs.add(to, songs.removeAt(index))
                                        dragging = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = { dragging = null; dragOffset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.DragHandle, "Drag to reorder", tint = c.textSecondary) }
                    }
                }
            }
            if (playlist != null) {
                PillButton("Delete playlist", Modifier.padding(20.dp).fillMaxWidth(), danger = true) { confirmDelete = true }
            }
        }
    }

    if (confirmDelete && playlist != null) {
        GlowDialog({ confirmDelete = false }) {
            Text("Delete \"${playlist.title}\"?", style = GlowType.title, color = c.text)
            Spacer(Modifier.height(8.dp))
            Text("This removes it from your YouTube Music account.", style = GlowType.bodyRegular, color = c.textSecondary)
            Spacer(Modifier.height(18.dp))
            PillButton("Delete", Modifier.fillMaxWidth(), danger = true, height = 48.dp) {
                vm.deletePlaylist(playlist)
                confirmDelete = false
                g.pop()
                g.pop()
            }
            Spacer(Modifier.height(8.dp))
            PillButton("Cancel", Modifier.fillMaxWidth(), primary = false, height = 48.dp) { confirmDelete = false }
        }
    }
}
