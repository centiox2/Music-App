package com.music.bitchord.ui.glow

import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.music.bitchord.data.PlayHistory
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.listentogether.JamInviteLink
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.playback.LinkRequest
import com.music.bitchord.playback.MusicLink
import com.music.bitchord.playback.PlaybackActions
import com.music.bitchord.playback.PlayerDeepLink
import com.music.bitchord.playback.QueueSource
import com.music.bitchord.playback.rememberMediaController
import com.music.bitchord.playback.rememberPlayerState
import com.music.bitchord.ui.MainViewModel
import kotlinx.coroutines.delay

/** Height reserved under page content for the mini player and the tab bar. */
val BottomChrome = 168.dp

@Composable
fun GlowApp(
    vm: MainViewModel = viewModel(),
    /** Stands in for the live player in screenshot tests, where no service runs. */
    playerOverride: com.music.bitchord.playback.PlayerState? = null,
) {
    val context = LocalContext.current
    remember(context) {
        GlowPrefs.init(context)
        PlayHistory.init(context)
    }
    val scope = rememberCoroutineScope()
    val controller = rememberMediaController()
    val player = rememberPlayerState(controller)

    val state = remember(vm) {
        lateinit var holder: GlowState
        val actions = PlaybackActions(
            context = context.applicationContext,
            scope = scope,
            controller = { holder.controller },
            currentQueue = { holder.player.queue },
            notify = { holder.notify(it) },
        )
        holder = GlowState(context.applicationContext, vm, actions, scope)
        holder.tab = GlowPrefs.startTab.value
        holder
    }
    state.controller = controller
    state.player = playerOverride ?: player

    CompositionLocalProvider(LocalGlow provides state) {
        GlowRoot(state)
    }
}

@Composable
private fun GlowRoot(state: GlowState) {
    val vm = state.vm
    val player = state.player
    val c = Glow.colors
    val context = LocalContext.current

    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val onboardingComplete by AppSettings.onboardingComplete.collectAsStateWithLifecycle()
    val notificationsAsked by GlowPrefs.notificationsAsked.collectAsStateWithLifecycle()

    // ---- side effects that keep BitChord's logic fed ----

    LaunchedEffect(player.song?.videoId) {
        if (!GlowPrefs.pauseListenHistory.value) player.song?.let { PlayHistory.record(it) }
    }
    val syncedLyrics by AppSettings.syncedLyrics.collectAsStateWithLifecycle()
    val lyricsSources by AppSettings.lyricsSources.collectAsStateWithLifecycle()
    LaunchedEffect(player.song?.videoId, player.durationMs, syncedLyrics, lyricsSources) {
        player.song?.let {
            vm.loadLyrics(
                it.videoId,
                it.title,
                it.artist,
                player.durationMs.takeIf { ms -> ms > 0L } ?: it.durationMillis(),
                it.albumName,
                it.localUri,
            )
        }
    }
    LaunchedEffect(state.toast?.id) {
        val shown = state.toast ?: return@LaunchedEffect
        delay(if (shown.actionLabel != null) 5_000 else 3_000)
        if (state.toast?.id == shown.id) state.toast = null
    }

    // Links from outside: the widget's artwork, a party invite, a shared link.
    val openPlayer by PlayerDeepLink.pending.collectAsStateWithLifecycle()
    LaunchedEffect(openPlayer) {
        if (openPlayer) {
            state.playerOpen = true
            PlayerDeepLink.handled()
        }
    }
    val invite by JamInviteLink.pending.collectAsStateWithLifecycle()
    LaunchedEffect(invite) {
        if (invite != null) {
            state.playerOpen = false
            state.push(Route.JoinSession)
        }
    }
    val link by MusicLink.pending.collectAsStateWithLifecycle()
    LaunchedEffect(link, state.controller) {
        val request = link ?: return@LaunchedEffect
        val session = state.controller ?: return@LaunchedEffect
        when (request) {
            is LinkRequest.Track -> {
                val song = YtMusicRepository.trackLinks(request.videoId).getOrNull()
                if (song == null) {
                    state.notify("Couldn't open that link")
                } else {
                    state.actions.playRadio(song, QueueSource("Shared link", PlaybackSourceType.SHARED_LINK, song.videoId))
                }
            }
            is LinkRequest.Page -> {
                state.playerOpen = false
                state.openDetail(request.browseId, title = "")
            }
            is LinkRequest.Search -> {
                val top = if (!request.play) null else {
                    YtMusicRepository.search(request.query, SearchFilter.SONGS).getOrNull()
                        ?.filterIsInstance<SearchResult.Track>()
                        ?.firstOrNull()?.song
                }
                if (top != null) {
                    state.actions.playRadio(top, QueueSource("Search", PlaybackSourceType.SEARCH))
                } else {
                    state.playerOpen = false
                    state.selectTab(1)
                    vm.searchFor(request.query)
                }
            }
            LinkRequest.Resume -> if (session.mediaItemCount > 0) session.play()
        }
        MusicLink.handled()
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        GlowPrefs.setNotificationsAsked()
    }

    // ---- back ----
    BackHandler(enabled = state.playerOpen || state.devicesOpen || state.stack.isNotEmpty()) {
        when {
            state.devicesOpen -> state.devicesOpen = false
            state.playerOpen -> state.playerOpen = false
            else -> state.pop()
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(c.base)) {
        state.wide = maxWidth >= 840.dp
        val railWidth = if (state.wide) RailWidth else 0.dp
        // Pages.
        AnimatedContent(
            modifier = Modifier.padding(start = railWidth),
            targetState = state.top to state.tab,
            transitionSpec = {
                val forward = state.stack.size > 0 && targetState.first != null && initialState.first != targetState.first
                if (targetState.second != initialState.second && targetState.first == null) {
                    fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                } else if (forward) {
                    (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(200))) togetherWith fadeOut(tween(160))
                } else {
                    fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(240)) { it / 4 } + fadeOut(tween(180)))
                }
            },
            label = "pages",
        ) { (route, tab) ->
            Box(Modifier.fillMaxSize()) {
                when (route) {
                    null -> when (tab) {
                        0 -> HomeScreen()
                        1 -> SearchScreen()
                        else -> LibraryScreen()
                    }
                    is Route.Detail -> DetailScreen(route)
                    is Route.Discography -> DiscographyScreen(route)
                    is Route.Mood -> MoodScreen(route)
                    is Route.EditPlaylist -> EditPlaylistScreen(route)
                    Route.Explore -> ExploreScreen()
                    Route.Liked -> LikedSongsScreen()
                    Route.Downloads -> DownloadsScreen()
                    Route.History -> HistoryScreen()
                    Route.Settings -> SettingsScreen()
                    Route.SettingsTheme -> ThemeScreen()
                    Route.SettingsLyrics -> LyricsSourcesScreen()
                    Route.SettingsTranslation -> TranslationSettingsScreen()
                    Route.SettingsPlaybackOptions -> PlaybackSettingsScreen()
                    Route.SettingsPrivacy -> PrivacyScreen()
                    Route.SettingsBackup -> BackupScreen()
                    Route.SettingsStorage -> StorageScreen()
                    Route.SettingsLastFm -> LastFmScreen()
                    Route.SettingsPlayback -> StreamingQualityScreen()
                    Route.Equalizer -> EqualizerScreen()
                    Route.JoinSession -> JoinSessionScreen()
                }
            }
        }

        // Mini player + tabs, hidden on the full-screen pages that don't show them.
        val chromeHidden = state.top in setOf(
            Route.Settings, Route.SettingsTheme, Route.SettingsLyrics, Route.SettingsStorage,
            Route.SettingsLastFm, Route.SettingsPlayback, Route.Equalizer, Route.JoinSession,
            Route.SettingsTranslation, Route.SettingsPlaybackOptions, Route.SettingsPrivacy, Route.SettingsBackup,
        ) || state.top is Route.EditPlaylist || state.top is Route.Discography
        if (!chromeHidden) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = railWidth)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToastHost(state)
                AnimatedVisibility(
                    visible = player.song != null,
                    enter = slideInVertically(androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f)) { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) { MiniPlayer(state) }
                if (!state.wide) BottomTabs(state)
            }
        } else {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(12.dp),
            ) { ToastHost(state) }
        }

        if (state.wide) SideRail(state)

        // The player is a full-screen take-over.
        LaunchedEffect(state.playerOpen) { if (state.playerOpen) state.playerDrag = 0f }
        AnimatedVisibility(
            visible = state.playerOpen && player.song != null,
            enter = slideInVertically(tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it } +
                fadeIn(tween(200)),
            exit = slideOutVertically(tween(280, easing = androidx.compose.animation.core.FastOutLinearInEasing)) { it },
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val radius = with(density) { 28.dp.toPx() }
            Box(
                Modifier.graphicsLayer {
                    val drag = state.playerDrag
                    val f = (drag / size.height).coerceIn(0f, 1f)
                    translationY = drag
                    // Pulled down, the player shrinks a touch and rounds like a card.
                    scaleX = 1f - f * 0.08f
                    scaleY = 1f - f * 0.08f
                    shape = RoundedCornerShape((radius * (f * 6f).coerceAtMost(1f)).let { with(density) { it.toDp() } })
                    clip = drag > 0f
                },
            ) {
                PlayerScreen()
            }
        }
        AnimatedVisibility(
            visible = state.devicesOpen,
            enter = slideInVertically(tween(300)) { it },
            exit = slideOutVertically(tween(240)) { it },
        ) {
            DevicesScreen()
        }

        // First launch: sign in or carry on, then the notifications ask.
        val showWelcome = !onboardingComplete && !signedIn
        if (showWelcome || state.signInOpen) {
            SignInFlow(
                onFinished = {
                    AppSettings.setOnboardingComplete()
                    state.signInOpen = false
                },
                closable = state.signInOpen,
            )
        } else if (state.switchChannelOpen) {
            SignInFlow(
                onFinished = { state.switchChannelOpen = false },
                closable = true,
                mode = com.music.bitchord.auth.WebSessionMode.SWITCH_CHANNEL,
            )
        } else if (!notificationsAsked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            NotificationsPrompt(
                onAllow = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onSkip = { GlowPrefs.setNotificationsAsked() },
            )
        }
    }

    // ---- sheets ----
    state.songMenu?.let { song -> SongMenuSheet(song, onDismiss = { state.songMenu = null }) }
    state.saveToPlaylist?.let { songs -> SaveToPlaylistSheet(songs, onDismiss = { state.saveToPlaylist = null }) }
    if (state.newPlaylistOpen) {
        NewPlaylistSheet(songs = state.newPlaylistFor, onDismiss = {
            state.newPlaylistOpen = false
            state.newPlaylistFor = null
        })
    }
    if (state.sleepTimerOpen) SleepTimerSheet(onDismiss = { state.sleepTimerOpen = false })
    state.songDetails?.let { song -> SongDetailsSheet(song, onDismiss = { state.songDetails = null }) }
}

@Composable
private fun ToastHost(state: GlowState) {
    AnimatedVisibility(
        visible = state.toast != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        val toast = state.toast ?: return@AnimatedVisibility
        ToastCard(toast) { state.toast = null }
    }
}

/** The light confirmation card: "Saved to Late night drives · View · Undo". */
@Composable
fun ToastCard(toast: GlowToast, onDone: () -> Unit) {
    val c = Glow.colors
    val bg = if (c.isDark) androidx.compose.ui.graphics.Color(0xFFF1F3F2) else c.text
    val fg = if (c.isDark) androidx.compose.ui.graphics.Color(0xFF111111) else androidx.compose.ui.graphics.Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (toast.artworkUrl != null) {
            Cover(toast.artworkUrl, size = 40.dp)
            Spacer(Modifier.width(12.dp))
        } else {
            Spacer(Modifier.width(6.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(toast.message, style = GlowType.body.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 2)
            if (toast.detail != null) Text(toast.detail, style = GlowType.secondary, color = fg.copy(alpha = 0.65f), maxLines = 1)
        }
        if (toast.secondaryLabel != null) {
            Text(
                toast.secondaryLabel,
                style = GlowType.body.copy(fontWeight = FontWeight.SemiBold),
                color = fg,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { toast.secondaryAction?.invoke(); onDone() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (toast.actionLabel != null) {
            Text(
                toast.actionLabel,
                style = GlowType.body.copy(fontWeight = FontWeight.SemiBold),
                color = bg,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(fg)
                    .clickable { toast.action?.invoke(); onDone() }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun MiniPlayer(state: GlowState) {
    val c = Glow.colors
    val player = state.player
    val song = player.song ?: return
    val progress = if (player.durationMs > 0) player.position.positionMs.toFloat() / player.durationMs else 0f
    val animatedProgress by animateFloatAsState(progress.coerceIn(0f, 1f), tween(300), label = "miniProgress")
    val swipe by GlowPrefs.swipeMiniPlayer.collectAsStateWithLifecycle()
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val dragX = remember { androidx.compose.animation.core.Animatable(0f) }
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .graphicsLayer {
                translationX = dragX.value
                alpha = 1f - (kotlin.math.abs(dragX.value) / size.width).coerceIn(0f, 0.6f)
            }
            .clip(RoundedCornerShape(16.dp))
            .background(c.miniPlayer)
            .clickable(interaction, androidx.compose.material3.ripple()) { state.playerPane = PlayerPane.NOW_PLAYING; state.playerOpen = true }
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, drag -> if (drag < -12f) state.playerOpen = true }
            }
            .pointerInput(swipe, player.hasNext) {
                if (!swipe) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val threshold = size.width * 0.28f
                        scope.launch {
                            val x = dragX.value
                            val controller = state.controller
                            when {
                                x < -threshold && player.hasNext -> {
                                    haptic()
                                    dragX.animateTo(-size.width.toFloat(), tween(160))
                                    controller?.seekToNext()
                                    dragX.snapTo(size.width * 0.5f)
                                }
                                x > threshold -> {
                                    haptic()
                                    dragX.animateTo(size.width.toFloat(), tween(160))
                                    controller?.seekToPrevious()
                                    dragX.snapTo(-size.width * 0.5f)
                                }
                            }
                            dragX.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 500f))
                        }
                    },
                    onDragCancel = { scope.launch { dragX.animateTo(0f) } },
                ) { change, dx ->
                    change.consume()
                    scope.launch { dragX.snapTo(dragX.value + dx) }
                }
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = song,
                contentKey = { it.videoId },
                transitionSpec = { (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 3 }) togetherWith fadeOut(tween(160)) },
                modifier = Modifier.weight(1f),
                label = "miniSong",
            ) { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Cover(s.thumbnailUrl, size = 46.dp, shape = RoundedCornerShape(8.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        GlowText(s.title, GlowType.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                        GlowText(s.artist, GlowType.secondary, color = c.textSecondary, maxLines = 1)
                    }
                }
            }
            PrimaryCircle(
                if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (player.isPlaying) "Pause" else "Play",
                size = 44.dp,
                iconSize = 22.dp,
            ) { haptic(); state.actions.togglePlayPause() }
            Spacer(Modifier.width(6.dp))
            IconTap(Icons.Rounded.SkipNext, "Next", size = 44.dp, iconSize = 28.dp, enabled = player.hasNext) {
                haptic()
                state.controller?.seekToNext()
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(c.glass),
        ) {
            Box(Modifier.fillMaxWidth(animatedProgress).height(2.dp).background(c.text))
        }
    }
}

private val RailWidth = 88.dp

/** The tablet's navigation: the same three tabs, stacked down the left edge. */
@Composable
private fun SideRail(state: GlowState) {
    val c = Glow.colors
    Column(
        Modifier
            .fillMaxHeight()
            .width(RailWidth)
            .background(c.base)
            .statusBarsPadding()
            .padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        RailItem("Home", Icons.Rounded.Home, Icons.Outlined.Home, state.tab == 0) { state.selectTab(0) }
        RailItem("Search", Icons.Rounded.Search, Icons.Rounded.Search, state.tab == 1) { state.selectTab(1) }
        RailItem("Library", Icons.AutoMirrored.Rounded.FormatListBulleted, Icons.AutoMirrored.Rounded.FormatListBulleted, state.tab == 2) { state.selectTab(2) }
    }
    Box(
        Modifier
            .padding(start = RailWidth)
            .fillMaxHeight()
            .width(1.dp)
            .background(c.divider),
    )
}

@Composable
private fun RailItem(label: String, selectedIcon: ImageVector, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val c = Glow.colors
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(if (selected) selectedIcon else icon, null, tint = if (selected) c.text else c.textSecondary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = GlowType.caption.copy(fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) c.text else c.textSecondary,
        )
    }
}

@Composable
private fun BottomTabs(state: GlowState) {
    val c = Glow.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.navBar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabItem("Home", Icons.Rounded.Home, Icons.Outlined.Home, state.tab == 0 && state.top == null || state.tab == 0) { state.selectTab(0) }
        TabItem("Search", Icons.Rounded.Search, Icons.Rounded.Search, state.tab == 1) { state.selectTab(1) }
        TabItem("Library", Icons.AutoMirrored.Rounded.FormatListBulleted, Icons.AutoMirrored.Rounded.FormatListBulleted, state.tab == 2) { state.selectTab(2) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    label: String,
    selectedIcon: ImageVector,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = Glow.colors
    val tint by androidx.compose.animation.animateColorAsState(if (selected) c.text else c.textSecondary, tween(220), label = "tabTint")
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        Modifier
            .weight(1f)
            .fillMaxSize()
            .clickable(interaction, null, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (selected) selectedIcon else icon,
            null,
            tint = tint,
            modifier = Modifier.size(24.dp).pressScale(interaction, 0.8f).popOnChange(selected, enabled = selected),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = GlowType.caption.copy(fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = tint,
        )
    }
}

/** Content padding that clears the floating mini player and tab bar. */
@Composable
fun pageContentPadding(top: androidx.compose.ui.unit.Dp = 0.dp): PaddingValues {
    val g = LocalGlow.current
    val hasSong = g.player.song != null
    val tabs = if (g.wide) 72.dp else 0.dp
    return PaddingValues(top = top, bottom = (if (hasSong) BottomChrome + 20.dp else 110.dp) - tabs)
}

/** A shelf card's song, when the card is a track rather than a collection. */
fun ShelfItem.asSong(): Song? = videoId?.let {
    Song(
        videoId = it,
        title = title,
        artist = InnertubeParser.artistFromSubtitle(subtitle),
        thumbnailUrl = thumbnailUrl,
    )
}

/** Taps on a shelf card: play a track, or open the page behind a collection. */
fun GlowState.openShelfItem(item: ShelfItem, shelfTitle: String) {
    val song = item.asSong()
    when {
        song != null -> playSingle(song, shelfTitle)
        item.browseId != null -> openDetail(item.browseId!!, item.title, item.subtitle, item.thumbnailUrl)
    }
}
