package com.music.bitchord.ui.glow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import com.music.bitchord.ui.glow.lyrics.GlowLyrics
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.toggleAutoplay
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun PlayerScreen() {
    val g = LocalGlow.current
    val player = g.player
    val song = player.song ?: return
    val c = Glow.colors

    Box(
        Modifier
            .fillMaxSize()
            .background(c.base)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        CoverGlow(song.thumbnailUrl, height = 900.dp, intensity = 0.55f)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= 840.dp && maxHeight >= 560.dp) {
                TabletPlayer(song)
            } else if (maxWidth > maxHeight && maxWidth > 600.dp) {
                LandscapePlayer(song)
            } else {
                PortraitPlayer(song)
            }
        }
    }
}

@Composable
private fun PortraitPlayer(song: Song) {
    val g = LocalGlow.current
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        PlayerHeader(song)
        AnimatedContent(
            targetState = g.playerPane,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            modifier = Modifier.weight(1f),
            label = "pane",
        ) { pane ->
            Column(Modifier.fillMaxSize()) {
                when (pane) {
                    PlayerPane.NOW_PLAYING -> {
                        Spacer(Modifier.weight(0.5f))
                        Artwork(song, Modifier.fillMaxWidth().aspectRatio(1f))
                        Spacer(Modifier.weight(0.5f))
                        Spacer(Modifier.height(24.dp))
                        TitleRow(song, large = true)
                    }
                    PlayerPane.LYRICS -> {
                        LyricsPane(Modifier.weight(1f))
                        Spacer(Modifier.height(12.dp))
                        TitleRow(song, large = false)
                    }
                    PlayerPane.QUEUE -> QueuePane(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        SeekBar()
        Spacer(Modifier.height(18.dp))
        Controls()
        Spacer(Modifier.height(22.dp))
        PaneBar()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun LandscapePlayer(song: Song) {
    val g = LocalGlow.current
    Row(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Box(Modifier.fillMaxHeight().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Artwork(song, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            PlayerHeader(song)
            when (g.playerPane) {
                PlayerPane.NOW_PLAYING -> {
                    Spacer(Modifier.weight(1f))
                    TitleRow(song, large = true)
                    Spacer(Modifier.weight(1f))
                }
                PlayerPane.LYRICS -> LyricsPane(Modifier.weight(1f))
                PlayerPane.QUEUE -> QueuePane(Modifier.weight(1f))
            }
            SeekBar()
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Controls() }
                Spacer(Modifier.width(16.dp))
                Box(Modifier.width(240.dp)) { PaneBar() }
            }
        }
    }
}

/**
 * Tablet: the player on the left, and lyrics or the queue given the right
 * half of the screen, chosen by tabs rather than the bottom pane bar.
 */
@Composable
private fun TabletPlayer(song: Song) {
    val g = LocalGlow.current
    val c = Glow.colors
    if (g.playerPane == PlayerPane.NOW_PLAYING) {
        androidx.compose.runtime.SideEffect { g.playerPane = PlayerPane.LYRICS }
    }
    Row(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 40.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        Column(Modifier.weight(0.9f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTap(Icons.Rounded.KeyboardArrowDown, "Close player", iconSize = 30.dp, modifier = Modifier.offset(x = (-10).dp)) {
                    g.playerOpen = false
                }
                Column {
                    Text("Listening to", style = GlowType.secondary, color = c.textSecondary)
                    Text(
                        song.radioName ?: song.playbackSource ?: song.albumName ?: "Your queue",
                        style = GlowType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                        color = c.text,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Artwork(song, Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
            }
            Spacer(Modifier.height(20.dp))
            TitleRow(song, large = true)
            Spacer(Modifier.height(16.dp))
            SeekBar()
            Spacer(Modifier.height(18.dp))
            Controls()
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Segmented(
                listOf("Lyrics", "Up next", "Devices"),
                if (g.playerPane == PlayerPane.QUEUE) 1 else 0,
                Modifier.width(380.dp),
            ) { i ->
                when (i) {
                    0 -> g.playerPane = PlayerPane.LYRICS
                    1 -> g.playerPane = PlayerPane.QUEUE
                    else -> g.devicesOpen = true
                }
            }
            Spacer(Modifier.height(20.dp))
            if (g.playerPane == PlayerPane.QUEUE) QueuePane(Modifier.weight(1f)) else LyricsPane(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlayerHeader(song: Song) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val lyricsSource by vm.lyricsSource.collectAsStateWithLifecycle()
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(Unit) { detectVerticalDragGestures { _, drag -> if (drag > 18f) g.playerOpen = false } },
    ) {
        IconTap(Icons.Rounded.KeyboardArrowDown, "Close player", iconSize = 32.dp, modifier = Modifier.align(Alignment.CenterStart).offset(x = (-10).dp)) {
            g.playerOpen = false
        }
        Column(Modifier.align(Alignment.Center).padding(horizontal = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val (top, bottom) = when (g.playerPane) {
                PlayerPane.LYRICS -> "Lyrics" to (
                    lyricsSource?.label?.let { src ->
                        val synced = lyrics?.any { it.isWordSynced } == true
                        if (synced) "Synced · Karaoke" else src
                    } ?: "Looking for lyrics…"
                    )
                PlayerPane.QUEUE -> "" to "Up next"
                PlayerPane.NOW_PLAYING -> "Listening to" to (song.radioName ?: song.playbackSource ?: song.albumName ?: "Your queue")
            }
            if (top.isNotEmpty()) Text(top, style = GlowType.secondary, color = c.textSecondary, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (g.playerPane == PlayerPane.NOW_PLAYING) {
                    Icon(Icons.Rounded.MusicNote, null, tint = c.text, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(bottom, style = GlowType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = if (g.playerPane == PlayerPane.QUEUE) 18.sp else 16.sp), color = c.text, maxLines = 1)
            }
        }
        if (g.playerPane == PlayerPane.QUEUE) {
            TextAction("Clear", Modifier.align(Alignment.CenterEnd)) { g.actions.clearUpcoming() }
        }
    }
}

/** The cover, or the "can't play" card in its place, with swipe-to-skip. */
@Composable
private fun Artwork(song: Song, modifier: Modifier) {
    val g = LocalGlow.current
    val c = Glow.colors
    var dragX by remember { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(dragX, label = "swipe")
    Box(
        modifier
            .graphicsLayer { translationX = offset * 0.35f }
            .clip(RoundedCornerShape(12.dp))
            .background(c.glassStrong)
            .pointerInput(song.videoId) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val controller = g.controller
                        when {
                            dragX < -160f -> controller?.seekToNext()
                            dragX > 160f -> controller?.seekToPrevious()
                        }
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                ) { _, delta -> dragX += delta }
            },
    ) {
        AsyncImage(song.thumbnailUrl.artworkAt(1080), song.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        val error = g.player.error
        if (error != null) PlaybackErrorCard(error)
    }
}

@Composable
private fun PlaybackErrorCard(error: String) {
    val g = LocalGlow.current
    val c = Glow.colors
    var countdown by remember(error) { mutableIntStateOf(5) }
    LaunchedEffect(error) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        if (g.player.hasNext) g.controller?.seekToNext()
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xE6201717)).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(c.danger.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = c.danger, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("This song can't play right now", style = GlowType.titleSmall.copy(fontSize = 19.sp), color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "It may be unavailable where you are, or the connection dropped.",
            style = GlowType.bodyRegular,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Try again", height = 42.dp) {
                g.controller?.let { it.prepare(); it.play() }
            }
            PillButton("Skip", primary = false, height = 42.dp, enabled = g.player.hasNext) { g.controller?.seekToNext() }
        }
        if (g.player.hasNext && countdown > 0) {
            Spacer(Modifier.height(12.dp))
            Text("Skipping to the next song in $countdown s", style = GlowType.secondary, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun TitleRow(song: Song, large: Boolean) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val likes by vm.likeStatuses.collectAsStateWithLifecycle()
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val liked = likes[song.videoId] == LikeStatus.LIKE
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!large) {
            Cover(song.thumbnailUrl, size = 52.dp, shape = RoundedCornerShape(8.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = if (large) GlowType.playerTitle else GlowType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                color = c.text,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.clickable(enabled = song.albumId != null) { g.playerOpen = false; g.openAlbumOf(song) },
            )
            Text(
                song.artist,
                style = GlowType.bodyRegular.copy(fontSize = if (large) 17.sp else 15.sp),
                color = c.textSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.clickable(enabled = song.artistId != null) {
                    g.playerOpen = false
                    g.openArtist(song.artistId, song.artist)
                },
            )
        }
        if (signedIn) {
            IconTap(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Unlike" else "Like", iconSize = 24.dp) {
                vm.toggleLike(song.videoId)
            }
        }
        IconTap(Icons.Rounded.MoreHoriz, "More options", iconSize = 24.dp) { g.openSongMenu(song, fromPlayer = true) }
    }
}

@Composable
private fun SeekBar() {
    val g = LocalGlow.current
    val c = Glow.colors
    val player = g.player
    val duration = player.durationMs.coerceAtLeast(0L)
    var dragging by remember { mutableStateOf<Float?>(null) }
    val position = player.position.positionMs
    val fraction = dragging ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val density = LocalDensity.current
    Column {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        if (duration > 0) g.controller?.seekTo((offset.x / size.width * duration).toLong())
                    }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = (it.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragging?.let { f -> if (duration > 0) g.controller?.seekTo((f * duration).toLong()) }
                            dragging = null
                        },
                        onDragCancel = { dragging = null },
                    ) { change, _ -> dragging = (change.position.x / size.width).coerceIn(0f, 1f) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val width = maxWidth
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.glassStrong))
            Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(CircleShape).background(c.text))
            val thumb = if (dragging != null) 18.dp else 14.dp
            Box(
                Modifier
                    .offset { IntOffset(with(density) { ((width - thumb) * fraction).roundToPx() }, 0) }
                    .size(thumb)
                    .clip(CircleShape)
                    .background(c.text),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            val shownPos = dragging?.let { (it * duration).toLong() } ?: position
            Text(shownPos.formatClock(), style = GlowType.secondary, color = c.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                if (duration > 0) "-" + (duration - shownPos).coerceAtLeast(0).formatClock() else "--:--",
                style = GlowType.secondary,
                color = c.textSecondary,
            )
        }
    }
}

@Composable
private fun Controls() {
    val g = LocalGlow.current
    val c = Glow.colors
    val player = g.player
    val controller = g.controller
    val shuffle by AppSettings.shuffleEnabled.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconTap(Icons.Rounded.Shuffle, "Shuffle", tint = if (shuffle) c.text else c.textSecondary, iconSize = 24.dp) { g.toggleShuffle() }
        IconTap(Icons.Rounded.SkipPrevious, "Previous", size = 56.dp, iconSize = 40.dp) { controller?.seekToPrevious() }
        PrimaryCircle(
            if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (player.isPlaying) "Pause" else "Play",
            size = 68.dp,
            iconSize = 34.dp,
        ) { g.actions.togglePlayPause() }
        IconTap(Icons.Rounded.SkipNext, "Next", size = 56.dp, iconSize = 40.dp, enabled = player.hasNext) { controller?.seekToNext() }
        IconTap(
            if (player.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            "Repeat",
            tint = if (player.repeatMode == Player.REPEAT_MODE_OFF) c.textSecondary else c.text,
            iconSize = 24.dp,
        ) { g.cycleRepeat() }
    }
}

/** Up next · devices · lyrics. The current pane wears the white pill. */
@Composable
private fun PaneBar() {
    val g = LocalGlow.current
    val c = Glow.colors
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(c.glass).padding(4.dp),
    ) {
        PaneButton(Icons.AutoMirrored.Rounded.FormatListBulleted, "Up next", g.playerPane == PlayerPane.QUEUE) {
            g.playerPane = if (g.playerPane == PlayerPane.QUEUE) PlayerPane.NOW_PLAYING else PlayerPane.QUEUE
        }
        PaneButton(Icons.Rounded.DevicesOther, "Devices and listening together", false) { g.devicesOpen = true }
        PaneButton(Icons.Rounded.Lyrics, "Lyrics", g.playerPane == PlayerPane.LYRICS) {
            g.playerPane = if (g.playerPane == PlayerPane.LYRICS) PlayerPane.NOW_PLAYING else PlayerPane.LYRICS
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PaneButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = Glow.colors
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) c.primary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (selected) c.onPrimary else c.text, modifier = Modifier.size(22.dp))
    }
}

// ---- Lyrics ----

@Composable
private fun LyricsPane(modifier: Modifier) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val checked by vm.lyricsChecked.collectAsStateWithLifecycle()
    val source by vm.lyricsSource.collectAsStateWithLifecycle()
    val offsetMs by AppSettings.lyricsOffsetMs.collectAsStateWithLifecycle()
    val player by rememberUpdatedState(g.player)
    val view = LocalView.current
    DisposableEffect(view) {
        val was = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = was }
    }
    GlowLyrics(
        lyrics = lyrics,
        checked = checked,
        positionProvider = { g.controller?.currentPosition ?: player.position.positionMs },
        isPlayingProvider = { g.controller?.isPlaying ?: player.isPlaying },
        lyricsOffsetMs = offsetMs.toLong(),
        onSeek = { ms -> if (!g.actions.refusedByHost()) g.controller?.seekTo(ms) },
        onShare = { text -> g.shareUrl(player.song?.title.orEmpty(), text) },
        sourceLabel = source?.label,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No lyrics for this song", style = GlowType.title, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            TextAction("Lyrics sources") { g.playerOpen = false; g.push(Route.SettingsLyrics) }
        }
    }
}

// ---- Up next ----

@Composable
private fun QueuePane(modifier: Modifier) {
    val g = LocalGlow.current
    val c = Glow.colors
    val player = g.player
    val autoplay by AppSettings.autoplay.collectAsStateWithLifecycle()
    val queue = player.queue
    val currentIndex = player.queueIndex
    val current = player.song
    val upcoming = queue.withIndex().filter { it.index > currentIndex }
    val rowHeightPx = with(LocalDensity.current) { 64.dp.toPx() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(modifier.fillMaxWidth()) {
        if (current != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.glass).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(current.thumbnailUrl, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    GlowText(current.title, GlowType.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                    GlowText(current.artist, GlowType.secondary, color = c.textSecondary, maxLines = 1)
                }
                PlayingBars(Modifier.padding(end = 8.dp), playing = player.isPlaying)
            }
        }
        Row(Modifier.padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Playing from", style = GlowType.secondary, color = c.textSecondary)
                GlowText(current?.radioName ?: current?.playbackSource ?: "Your queue", GlowType.titleSmall, maxLines = 1)
            }
            Text("Autoplay", style = GlowType.bodyRegular, color = c.textSecondary)
            Spacer(Modifier.width(10.dp))
            GlowSwitch(autoplay, { g.controller?.toggleAutoplay() })
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(upcoming, key = { _, s -> (s.value.queueEntryId ?: s.value.videoId) + s.index }) { pos, entry ->
                val song = entry.value
                val isDragging = draggingIndex == entry.index
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffset else 0f
                            shadowElevation = if (isDragging) 12f else 0f
                        }
                        .background(if (isDragging) c.sheet else Color.Transparent)
                        .clickable { g.actions.skipToQueueIndex(entry.index) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(song.thumbnailUrl, size = 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        GlowText(song.title, GlowType.body, maxLines = 1)
                        GlowText(
                            song.artist + if (song.fromAutoplay) " · Autoplay" else "",
                            GlowType.secondary,
                            color = c.textSecondary,
                            maxLines = 1,
                        )
                    }
                    Box(
                        Modifier
                            .size(44.dp)
                            .pointerInput(entry.index) {
                                detectDragGestures(
                                    onDragStart = { draggingIndex = entry.index; dragOffset = 0f },
                                    onDragEnd = {
                                        val from = entry.index
                                        val steps = (dragOffset / rowHeightPx).roundToInt()
                                        val to = (from + steps).coerceIn(currentIndex + 1, queue.lastIndex)
                                        if (to != from) g.actions.moveQueueItem(from, to)
                                        draggingIndex = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = { draggingIndex = null; dragOffset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.DragHandle, "Drag to reorder", tint = c.textSecondary, modifier = Modifier.size(22.dp))
                    }
                }
            }
            if (upcoming.isEmpty()) {
                item {
                    Text(
                        if (autoplay) "Autoplay will add similar songs." else "Nothing else is queued.",
                        style = GlowType.secondary,
                        color = c.textSecondary,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            }
        }
    }
}
