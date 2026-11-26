package com.metrolist.shared.ui.player

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
    com.metrolist.shared.ui.navigation.NavigationBackHandler(enabled = (state.isFullscreen && state.isVideoModeActive) || pane != SonderPlayerPane.COVER) {
        if (state.isFullscreen && state.isVideoModeActive) viewModel.toggleFullscreen()
        else pane = SonderPlayerPane.COVER
    }
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
            SonderPlayerAction.CLEAR_UPCOMING -> viewModel.removeQueueEntries(state.queueEntryKeys.drop(state.currentQueueIndex + 1).filter { it >= 0 }.toSet())
        } },
        artwork = { mod ->
            if (state.isVideoModeActive) platform.MusicVideoSurface(mod, 12.dp, false, true, false, { viewModel.toggleFullscreen() }, null)
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
            modifier = mod, viewModel = viewModel, showBottomToolbar = false,
        ) },
        timeline = { SonderTimeline(viewModel, state.duration, !guest) },
        onAlbum = onAlbum,
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
enum class SonderPlayerAction { CLOSE, MENU, LIKE, SHUFFLE, PREVIOUS, PLAY_PAUSE, NEXT, REPEAT, RETRY, DEVICES, LYRICS_MENU, VIDEO, SHARE, CLEAR_UPCOMING }

@OptIn(ExperimentalFoundationApi::class)
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
    onAlbum: (String) -> Unit = {},
) {

    val song = state.currentSong ?: return
    val colors = MaterialTheme.colorScheme
    val dark = Settings.shared.getCurrentThemeIsDark()
    val shadow by androidx.compose.animation.core.animateFloatAsState(if (state.isPlaying) 1f else 0.4f, androidx.compose.animation.core.tween(500))
    val navInset = with(androidx.compose.ui.platform.LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    Box(modifier.fillMaxSize()) {
        LiquidBackground(song.thumbnail, animate = Settings.shared.animatedGradient, isPlaying = state.isPlaying)
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
            val wide = maxWidth > maxHeight && maxWidth >= 600.dp
            val compact = maxHeight < 700.dp
            @Composable fun Header() {
                Box(Modifier.fillMaxWidth().height(64.dp)) {
                    SonderIcon(MetrolistIcons.Rounded.KeyboardArrowDown, stringResource(Res.string.close), { onAction(SonderPlayerAction.CLOSE) },
                        Modifier.align(Alignment.CenterStart), iconSize = 32.dp)
                    Column(Modifier.align(Alignment.Center).padding(horizontal = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val heading = when (pane) {
                            SonderPlayerPane.COVER -> stringResource(Res.string.sonder_listening_to)
                            SonderPlayerPane.LYRICS -> stringResource(Res.string.lyrics)
                            SonderPlayerPane.QUEUE -> ""
                        }
                        if (heading.isNotEmpty()) Text(heading, fontSize = 13.sp, color = colors.onSurfaceVariant, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (pane == SonderPlayerPane.COVER) { Icon(MetrolistIcons.Rounded.MusicNote, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
                            Text(when (pane) {
                                SonderPlayerPane.COVER -> state.queueTitle ?: song.album ?: stringResource(Res.string.queue)
                                SonderPlayerPane.LYRICS -> state.lyricsProvider ?: stringResource(Res.string.loading)
                                SonderPlayerPane.QUEUE -> stringResource(Res.string.up_next)
                            }, fontSize = if (pane == SonderPlayerPane.QUEUE) 18.sp else 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (pane == SonderPlayerPane.QUEUE) TextButton(onClick = { onAction(SonderPlayerAction.CLEAR_UPCOMING) }, enabled = !guest, modifier = Modifier.align(Alignment.CenterEnd)) { Text(stringResource(Res.string.clear)) }
                    else if (wide && pane == SonderPlayerPane.LYRICS) SonderIcon(MetrolistIcons.Filled.MoreHoriz, stringResource(Res.string.more_options), { onAction(SonderPlayerAction.LYRICS_MENU) }, Modifier.align(Alignment.CenterEnd))
                    else if (state.isVideoAvailable && Settings.shared.showPlayerVideoButton) SonderIcon(MetrolistIcons.Filled.Movie, stringResource(Res.string.video), { onAction(SonderPlayerAction.VIDEO) }, Modifier.align(Alignment.CenterEnd))
                }
            }
            @Composable fun Artwork(mod: Modifier) {
                var drag by remember(song.id) { mutableFloatStateOf(0f) }
                Box(mod.sonderCoverShadow(12.dp, shadow, dark).clip(RoundedCornerShape(12.dp)).background(colors.onSurface.copy(alpha = 0.12f))
                    .pointerInput(song.id, guest) { if (!guest) detectHorizontalDragGestures(
                        onDragEnd = { if (drag < -size.width * 0.25f) onAction(SonderPlayerAction.NEXT) else if (drag > size.width * 0.25f) onAction(SonderPlayerAction.PREVIOUS); drag = 0f },
                        onDragCancel = { drag = 0f },
                    ) { change, amount -> change.consume(); drag += amount } }) { artwork(Modifier.fillMaxSize()) }
            }
            @Composable fun Title(large: Boolean) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!large) { AsyncImage(song.displayThumbnail, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))); Spacer(Modifier.width(14.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(song.title, fontSize = if (large) 24.sp else 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            modifier = Modifier.clickable(enabled = song.albumId != null) { song.albumId?.let(onAlbum) }.basicMarquee(iterations = 3))
                        Text(com.metrolist.shared.ui.menu.artistLinksText(song.artists, onArtist) ?: androidx.compose.ui.text.AnnotatedString(""),
                            fontSize = if (large) 17.sp else 15.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SonderIcon(if (state.isLiked) MetrolistIcons.Rounded.Favorite else MetrolistIcons.Rounded.FavoriteBorder,
                        stringResource(if (state.isLiked) Res.string.unlike else Res.string.action_like), { onAction(SonderPlayerAction.LIKE) }, iconSize = 24.dp)
                    SonderIcon(MetrolistIcons.Filled.MoreHoriz, stringResource(Res.string.more_options), {
                        onAction(if (pane == SonderPlayerPane.LYRICS) SonderPlayerAction.LYRICS_MENU else SonderPlayerAction.MENU)
                    }, iconSize = 24.dp)
                }
            }
            @Composable fun Controls(small: Boolean = false) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SonderIcon(MetrolistIcons.Rounded.Shuffle, stringResource(Res.string.shuffle), { onAction(SonderPlayerAction.SHUFFLE) }, enabled = !guest, selected = state.isShuffleOn, iconSize = 24.dp, selectedBackground = false, tint = if (state.isShuffleOn) colors.onSurface else colors.onSurfaceVariant)
                    SonderIcon(MetrolistIcons.Rounded.SkipPrevious, stringResource(Res.string.previous), { onAction(SonderPlayerAction.PREVIOUS) }, enabled = !guest, size = if (small) 48.dp else 56.dp, iconSize = if (small) 32.dp else 40.dp)
                    Surface(onClick = { onAction(SonderPlayerAction.PLAY_PAUSE) }, shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary,
                        modifier = Modifier.size(if (small) 58.dp else 68.dp).focusRequester(playFocus)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(if (state.isPlaying) MetrolistIcons.Rounded.Pause else MetrolistIcons.Rounded.PlayArrow,
                            stringResource(if (state.isPlaying) Res.string.pause else Res.string.play), Modifier.size(if (small) 30.dp else 34.dp)) }
                    }
                    SonderIcon(MetrolistIcons.Rounded.SkipNext, stringResource(Res.string.next), { onAction(SonderPlayerAction.NEXT) }, enabled = !guest, size = if (small) 48.dp else 56.dp, iconSize = if (small) 32.dp else 40.dp)
                    SonderIcon(if (state.repeatMode == 1) MetrolistIcons.Rounded.RepeatOne else MetrolistIcons.Rounded.Repeat, stringResource(Res.string.repeat), { onAction(SonderPlayerAction.REPEAT) }, enabled = !guest, selected = state.repeatMode != 0, iconSize = 24.dp, selectedBackground = false, tint = if (state.repeatMode != 0) colors.onSurface else colors.onSurfaceVariant)
                }
            }
            @Composable fun PaneBar() {
                Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(colors.onSurface.copy(alpha = 0.09f)).padding(4.dp)) {
                    @Composable fun Button(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, action: () -> Unit) {
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(if (selected) colors.primary else Color.Transparent)
                            .semantics { this.selected = selected }.clickable(role = Role.Tab, onClick = action), contentAlignment = Alignment.Center) {
                            Icon(icon, label, Modifier.size(22.dp), tint = if (selected) colors.onPrimary else colors.onSurface)
                        }
                    }
                    Button(MetrolistIcons.AutoMirrored.Filled.QueueMusic, stringResource(Res.string.up_next), pane == SonderPlayerPane.QUEUE) { onPaneChange(if (pane == SonderPlayerPane.QUEUE) SonderPlayerPane.COVER else SonderPlayerPane.QUEUE) }
                    Button(MetrolistIcons.Outlined.Devices, stringResource(Res.string.connected_devices), false) { onAction(SonderPlayerAction.DEVICES) }
                    Button(MetrolistIcons.Outlined.Lyrics, stringResource(Res.string.lyrics), pane == SonderPlayerPane.LYRICS) { onPaneChange(if (pane == SonderPlayerPane.LYRICS) SonderPlayerPane.COVER else SonderPlayerPane.LYRICS) }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    BoxWithConstraints(Modifier.weight(0.85f).fillMaxHeight(), contentAlignment = Alignment.Center) { Artwork(Modifier.size(minOf(maxWidth, maxHeight))) }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Header()
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            when (pane) { SonderPlayerPane.COVER -> Title(true); SonderPlayerPane.LYRICS -> lyrics(Modifier.fillMaxSize()); SonderPlayerPane.QUEUE -> queue(Modifier.fillMaxSize()) }
                        }
                        timeline(); Spacer(Modifier.height(8.dp)); Controls(small = compact); Spacer(Modifier.height(12.dp)); PaneBar()
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Header()
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        when (pane) {
                            SonderPlayerPane.COVER -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                                val gap = if (compact) 16.dp else 30.dp
                                Artwork(Modifier.size(minOf(maxWidth, (maxHeight - 56.dp - gap).coerceAtLeast(1.dp))))
                                Spacer(Modifier.height(gap)); Title(true)
                            }
                            SonderPlayerPane.LYRICS -> Column(Modifier.fillMaxSize()) { lyrics(Modifier.weight(1f).fillMaxWidth()); Spacer(Modifier.height(12.dp)); Title(false) }
                            SonderPlayerPane.QUEUE -> queue(Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.height(if (compact) 12.dp else 26.dp)); timeline()
                    Spacer(Modifier.height(if (compact) 12.dp else 25.dp)); Controls()
                    state.error?.let { Text(it, Modifier.clickable { onAction(SonderPlayerAction.RETRY) }, color = colors.error, fontSize = 13.sp) }
                    Spacer(Modifier.height(if (compact) 16.dp else 32.dp)); PaneBar()
                    Spacer(Modifier.height(if (compact) 12.dp else (50.dp - navInset).coerceAtLeast(12.dp)))
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
        Canvas(Modifier.fillMaxWidth().height(24.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f); setProgress { if (enabled) { seek(it); true } else false } }
            .pointerInput(duration, enabled) { if (enabled) detectTapGestures { seek(it.x / size.width) } }
            .pointerInput(duration, enabled) { if (enabled) detectDragGestures(onDragEnd = { preview?.let(seek); preview = null }, onDragCancel = { preview = null }) { change, _ -> change.consume(); preview = (change.position.x / size.width).coerceIn(0f, 1f) } }) {
            val y = size.height / 2
            drawLine(color.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(color, Offset(0f, y), Offset(size.width * fraction.coerceIn(0f, 1f), y), 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val radius = if (preview != null) 9.dp.toPx() else 7.dp.toPx()
            drawCircle(color, radius, Offset(radius + (size.width - radius * 2) * fraction.coerceIn(0f, 1f), y))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(sonderClock((fraction * duration).toLong()), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (duration > 0L) "-" + sonderClock((duration - fraction * duration).toLong()) else "--:--", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
internal fun sonderClock(milliseconds: Long): String { val seconds = milliseconds.coerceAtLeast(0L) / 1000; return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" }
