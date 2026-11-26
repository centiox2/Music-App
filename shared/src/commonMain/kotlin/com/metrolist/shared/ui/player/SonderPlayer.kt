package com.metrolist.shared.ui.player

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.*
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.playback.LocalPlayerSession
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.components.BottomSheetState
import com.metrolist.shared.ui.components.LiquidBackground
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.sonder.*
import com.metrolist.shared.viewmodels.PlayerViewModel
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToLong

enum class SonderPlayerPane { COVER, QUEUE, LYRICS }

@Composable
internal fun SonderPlayerScreen(
    viewModel: PlayerViewModel, bottomSheetState: BottomSheetState, onClose: () -> Unit,
    onAlbum: (String) -> Unit, onArtist: (String) -> Unit, onListenTogether: () -> Unit,
    onIntegrations: () -> Unit, modifier: Modifier, requestFocus: Boolean,
    onFocusRequested: () -> Unit, onLyricsVisible: (Boolean) -> Unit, screenVisible: Boolean,
) {
    val active = screenVisible && bottomSheetState.value > bottomSheetState.collapsedBound && !bottomSheetState.isCollapsed
    val state by viewModel.uiState.collectWhilePlayerActive(active)
    val song = state.currentSong ?: return
    val platform = LocalPlayerPlatform.current
    var pane by remember { mutableStateOf(SonderPlayerPane.COVER) }
    var menu by remember { mutableStateOf(false) }
    val playerSession = LocalPlayerSession.current
    var sleepTimer by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(false) }
    var lyricsMenu by remember { mutableStateOf(false) }
    var lyricsOffset by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }
    val guest = platform.isListenTogetherGuest()
    val canvasLayers = remember { CanvasLayers() }
    val canvas = canvasLayers.select(song, platform)
    LaunchedEffect(active, pane, state.isVideoModeActive) {
        viewModel.setCanvasVisible(active && pane == SonderPlayerPane.COVER && !state.isVideoModeActive)
        viewModel.setQueueVisible(active && pane == SonderPlayerPane.QUEUE)
    }
    DisposableEffect(viewModel) { onDispose { viewModel.setCanvasVisible(false); viewModel.setQueueVisible(false) } }
    LaunchedEffect(active, pane, song.id) {
        onLyricsVisible(active && pane == SonderPlayerPane.LYRICS)
        viewModel.setLyricsVisible(active && pane == SonderPlayerPane.LYRICS)
        if (active && pane == SonderPlayerPane.LYRICS) viewModel.loadLyrics(song)
    }
    DisposableEffect(Unit) { onDispose { onLyricsVisible(false); viewModel.setLyricsVisible(false) } }
    LaunchedEffect(active, requestFocus) { if (active && requestFocus) { playFocus.requestFocus(); onFocusRequested() } }
    if (!active) return
    platform.PlayerWindowEffects(hideSystemBars = Settings.shared.immersivePlayer || (state.isFullscreen && state.isVideoModeActive), lockLandscape = state.isFullscreen && state.isVideoModeActive)
    if (state.isFullscreen && state.isVideoModeActive) {
        platform.MusicVideoSurface(modifier.fillMaxSize(), 0.dp, true, true, true,
            { viewModel.toggleFullscreen() },
            if (platform.supportsPictureInPicture) ({ platform.enterPictureInPicture(state.isPlaying, true) }) else null)
        return
    }
    SonderPlayerPresentation(state, pane, { pane = it }, guest, playFocus, modifier, onArtist,
        onAction = { action -> when (action) {
            SonderPlayerAction.CLOSE -> onClose()
            SonderPlayerAction.MENU -> menu = true
            SonderPlayerAction.LIKE -> viewModel.toggleLike()
            SonderPlayerAction.SHUFFLE -> viewModel.toggleShuffle()
            SonderPlayerAction.PREVIOUS -> viewModel.previous()
            SonderPlayerAction.PLAY_PAUSE -> viewModel.togglePlayPause()
            SonderPlayerAction.NEXT -> viewModel.next()
            SonderPlayerAction.REPEAT -> viewModel.toggleRepeat()
            SonderPlayerAction.RETRY -> viewModel.retry()
            SonderPlayerAction.DEVICES -> devices = true
            SonderPlayerAction.LYRICS_MENU -> lyricsMenu = true
            SonderPlayerAction.VIDEO -> viewModel.toggleVideoMode()
            SonderPlayerAction.SHARE -> viewModel.shareSong()
        } },
        artwork = { mod ->
            if (state.isVideoModeActive) platform.MusicVideoSurface(mod, 18.dp, false, true, false, { viewModel.toggleFullscreen() }, null)
            else Box(mod) {
                AsyncImage(song.displayThumbnail, Modifier.fillMaxSize())
                canvasLayers.Render(canvas, state.isPlaying && active, Modifier.fillMaxSize())
            }
        },
        lyrics = { mod -> LyricsViewWithPosition(viewModel, state.lyrics, state.lyricsSongwriters, state.lyricsAgents, state.lyricsProvider,
            state.lyricsOffsetMs, viewModel::seekToPosition, state.isLyricsLoading, MaterialTheme.colorScheme.onSurface, false, mod) },
        queue = { mod -> QueueScreen(
            onClose = { pane = SonderPlayerPane.COVER }, onShowSleepTimer = { sleepTimer = true },
            onNavigateToAlbum = onAlbum, onNavigateToArtist = onArtist, onNavigateToListenTogether = onListenTogether,
            modifier = mod, viewModel = viewModel,
        ) },
        timeline = { SonderTimeline(viewModel, state.duration, !guest) },
    )
    if (sleepTimer && playerSession != null) {
        val timer by playerSession.sleepTimerState.collectAsState()
        com.metrolist.shared.ui.components.SleepTimerDialog(timer, { sleepTimer = false },
            playerSession::startSleepTimer, playerSession::stopAfterCurrentSong, playerSession::cancelSleepTimer)
    }
    if (menu) platform.PlayerMenu(viewModel, { menu = false }, onAlbum, onArtist, onListenTogether, { details = true })
    if (details) platform.SongDetailsSheet(viewModel, { details = false })
    if (devices) platform.DeviceConnectSheet({ devices = false }, onIntegrations)
    if (lyricsMenu) platform.LyricsMenu(viewModel, { lyricsMenu = false }, { lyricsOffset = true })
    if (lyricsOffset) {
        AlertDialog(onDismissRequest = { lyricsOffset = false }, title = { Text(stringResource(Res.string.lyrics_offset)) },
            text = { Slider(state.lyricsOffsetMs.toFloat(), { viewModel.setLyricsOffset(song.id, it.toInt()) }, valueRange = -10000f..10000f) },
            confirmButton = { TextButton(onClick = { lyricsOffset = false }) { Text(stringResource(Res.string.close)) } })
    }
}

/** Backend-independent presentation used by the Android player and rendered screen checks. */
enum class SonderPlayerAction { CLOSE, MENU, LIKE, SHUFFLE, PREVIOUS, PLAY_PAUSE, NEXT, REPEAT, RETRY, DEVICES, LYRICS_MENU, VIDEO, SHARE }

@Composable
fun SonderPlayerPresentation(
    state: PlayerViewModel.PlayerUiState,
    pane: SonderPlayerPane,
    onPaneChange: (SonderPlayerPane) -> Unit,
    guest: Boolean,
    playFocus: FocusRequester,
    modifier: Modifier,
    onArtist: (String) -> Unit,
    onAction: (SonderPlayerAction) -> Unit,
    artwork: @Composable (Modifier) -> Unit,
    lyrics: @Composable (Modifier) -> Unit,
    queue: @Composable (Modifier) -> Unit,
    timeline: @Composable () -> Unit,
) {
    val song = state.currentSong ?: return
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        LiquidBackground(song.thumbnail, animate = Settings.shared.animatedGradient, isPlaying = state.isPlaying, visible = true)
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide = maxWidth > maxHeight && maxWidth >= 600.dp
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SonderIcon(MetrolistIcons.Rounded.KeyboardArrowDown, stringResource(Res.string.close), { onAction(SonderPlayerAction.CLOSE) }, glass = true)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(Res.string.now_playing), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.queueTitle?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    SonderIcon(MetrolistIcons.Filled.MoreHoriz, stringResource(Res.string.more_options), { onAction(SonderPlayerAction.MENU) }, glass = true)
                }
                @Composable fun Artwork(mod: Modifier) {
                    Box(mod.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))) {
                        artwork(Modifier.fillMaxSize())
                    }
                }
                @Composable fun Pane(mod: Modifier) {
                    when (pane) {
                        SonderPlayerPane.COVER -> Box(mod, contentAlignment = Alignment.Center) { Artwork(Modifier.fillMaxWidth().aspectRatio(1f)) }
                        SonderPlayerPane.LYRICS -> lyrics(mod)
                        SonderPlayerPane.QUEUE -> queue(mod)
                    }
                }
                @Composable fun Controls(mod: Modifier) {
                    Column(mod, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(song.title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(song.artists.joinToString { it.name }, Modifier.clickable { song.artists.firstOrNull()?.id?.let(onArtist) }.padding(top = 4.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            SonderIcon(if (state.isLiked) MetrolistIcons.Rounded.Favorite else MetrolistIcons.Rounded.FavoriteBorder,
                                stringResource(if (state.isLiked) Res.string.unlike else Res.string.action_like), { onAction(SonderPlayerAction.LIKE) })
                        }
                        timeline()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            SonderIcon(MetrolistIcons.Rounded.Shuffle, stringResource(Res.string.shuffle), { onAction(SonderPlayerAction.SHUFFLE) }, enabled = !guest, selected = state.isShuffleOn)
                            SonderIcon(MetrolistIcons.Rounded.SkipPrevious, stringResource(Res.string.previous), { onAction(SonderPlayerAction.PREVIOUS) }, enabled = !guest)
                            Surface(onClick = { onAction(SonderPlayerAction.PLAY_PAUSE) }, shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(if (wide) 68.dp else 76.dp).focusRequester(playFocus)) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(if (state.isPlaying) MetrolistIcons.Rounded.Pause else MetrolistIcons.Rounded.PlayArrow,
                                        stringResource(if (state.isPlaying) Res.string.pause else Res.string.play), Modifier.size(36.dp))
                                }
                            }
                            SonderIcon(MetrolistIcons.Rounded.SkipNext, stringResource(Res.string.next), { onAction(SonderPlayerAction.NEXT) }, enabled = !guest)
                            SonderIcon(if (state.repeatMode == 1) MetrolistIcons.Rounded.RepeatOne else MetrolistIcons.Rounded.Repeat, stringResource(Res.string.repeat), { onAction(SonderPlayerAction.REPEAT) }, enabled = !guest, selected = state.repeatMode != 0)
                        }
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.clickable { onAction(SonderPlayerAction.RETRY) }) }
                    }
                }
                if (wide) {
                    Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(Modifier.weight(1f).aspectRatio(1f))
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                            if (pane != SonderPlayerPane.COVER) Pane(Modifier.weight(1f).fillMaxWidth())
                            Controls(Modifier.fillMaxWidth())
                        }
                    }
                } else {
                    Pane(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp))
                    Controls(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SonderIcon(MetrolistIcons.Outlined.Devices, stringResource(Res.string.connected_devices), { onAction(SonderPlayerAction.DEVICES) }, glass = true)
                    SonderPill(stringResource(Res.string.queue), pane == SonderPlayerPane.QUEUE) { onPaneChange(if (pane == SonderPlayerPane.QUEUE) SonderPlayerPane.COVER else SonderPlayerPane.QUEUE) }
                    SonderPill(stringResource(Res.string.lyrics), pane == SonderPlayerPane.LYRICS) { onPaneChange(if (pane == SonderPlayerPane.LYRICS) SonderPlayerPane.COVER else SonderPlayerPane.LYRICS) }
                    if (pane == SonderPlayerPane.LYRICS) SonderIcon(MetrolistIcons.Filled.MoreHoriz, stringResource(Res.string.more_options), { onAction(SonderPlayerAction.LYRICS_MENU) })
                    else if (state.isVideoAvailable && Settings.shared.showPlayerVideoButton) SonderIcon(MetrolistIcons.Filled.Movie, stringResource(Res.string.video), { onAction(SonderPlayerAction.VIDEO) })
                    else SonderIcon(MetrolistIcons.Outlined.Share, stringResource(Res.string.share), { onAction(SonderPlayerAction.SHARE) })
                }
            }
        }
    }
}

@Composable
private fun SonderTimeline(viewModel: PlayerViewModel, duration: Long, enabled: Boolean) {
    val position by viewModel.currentPosition.collectAsState()
    SonderSeekBar(position, duration, enabled, viewModel::seekToPosition)
}

@Composable
fun SonderSeekBar(position: Long, duration: Long, enabled: Boolean, onSeek: (Long) -> Unit) {
    var preview by remember { mutableStateOf<Float?>(null) }
    val fraction = preview ?: if (duration > 0) position.toFloat() / duration else 0f
    val color = MaterialTheme.colorScheme.onSurface
    val seek: (Float) -> Unit = { f -> onSeek((f.coerceIn(0f, 1f) * duration).roundToLong()) }
    Column {
        Canvas(Modifier.fillMaxWidth().height(28.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f); setProgress { if (enabled) { seek(it); true } else false } }
            .pointerInput(duration, enabled) { if (enabled) detectTapGestures { seek(it.x / size.width) } }
            .pointerInput(duration, enabled) { if (enabled) detectDragGestures(onDragEnd = { preview?.let(seek); preview = null }, onDragCancel = { preview = null }) { change, _ -> change.consume(); preview = (change.position.x / size.width).coerceIn(0f, 1f) } }) {
            val y = size.height / 2
            drawLine(color.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(color, Offset(0f, y), Offset(size.width * fraction.coerceIn(0f, 1f), y), 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(color, 4.dp.toPx(), Offset(size.width * fraction.coerceIn(0f, 1f), y))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(sonderClock((fraction * duration).toLong()), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(sonderClock(duration), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
internal fun sonderClock(milliseconds: Long): String { val seconds = milliseconds.coerceAtLeast(0L) / 1000; return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" }
