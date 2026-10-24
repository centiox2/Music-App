package com.music.bitchord.ui.glow

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.settings.DownloadQuality
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.playback.EqLayout
import com.music.bitchord.playback.EqualizerPreset
import com.music.bitchord.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** A pushed settings page: back + title, scrolling content, no tab bar. */
@Composable
internal fun SettingsPage(
    title: String,
    large: Boolean = false,
    glow: Boolean = false,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val g = LocalGlow.current
    GlowScreen(glowUrl = g.player.song?.thumbnailUrl, showGlow = glow) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            TopBar(onBack = { g.pop() }, title = if (large) null else title, actions = actions)
            if (large) {
                Text(title, style = GlowType.display.copy(fontSize = 34.sp), color = Glow.colors.text, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            content()
            Spacer(Modifier.height(40.dp))
        }
    }
}

private data class SettingEntry(val key: String, val section: String, val row: @Composable () -> Unit)

@Composable
fun SettingsScreen() {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val account by vm.account.collectAsStateWithLifecycle()
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val theme by AppSettings.themeMode.collectAsStateWithLifecycle()
    val fromArtwork by GlowPrefs.glowFromArtwork.collectAsStateWithLifecycle()
    val pureBlack by GlowPrefs.pureBlack.collectAsStateWithLifecycle()
    val reduceMotion by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val startTab by GlowPrefs.startTab.collectAsStateWithLifecycle()
    val wifiQuality by AppSettings.audioQualityWifi.collectAsStateWithLifecycle()
    val eqEnabled by AppSettings.equalizerEnabled.collectAsStateWithLifecycle()
    val eqPreset by AppSettings.equalizerPreset.collectAsStateWithLifecycle()
    val autoplay by AppSettings.autoplay.collectAsStateWithLifecycle()
    val synced by AppSettings.syncedLyrics.collectAsStateWithLifecycle()
    val wifiOnly by AppSettings.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val lastfmUser by AppSettings.lastfmUsername.collectAsStateWithLifecycle()
    val lastfmKey by AppSettings.lastfmSessionKey.collectAsStateWithLifecycle()
    val translate by com.music.bitchord.ui.glow.lyrics.TranslationPrefs.showTranslation.collectAsStateWithLifecycle()
    val translateProvider by com.music.bitchord.ui.glow.lyrics.TranslationPrefs.provider.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var accountMenu by remember { mutableStateOf(false) }

    val entries = listOf(
        SettingEntry("Theme colour", "Appearance") {
            SettingRow("Theme colour", icon = Icons.Rounded.Palette, subtitle = "Tints the app with the playing cover", value = if (fromArtwork) "From album art" else "Fixed", chevron = true) { g.push(Route.SettingsTheme) }
        },
        SettingEntry("Dark mode", "Appearance") {
            SettingRow("Dark mode", icon = Icons.Rounded.DarkMode, value = when (theme) { ThemeMode.DARK -> "Always"; ThemeMode.LIGHT -> "Never"; ThemeMode.SYSTEM -> "System" }, chevron = true) { g.push(Route.SettingsTheme) }
        },
        SettingEntry("Pure black", "Appearance") {
            SwitchRow("Pure black", pureBlack, GlowPrefs::setPureBlack, icon = Icons.Rounded.Contrast, subtitle = "Saves battery on OLED screens")
        },
        SettingEntry("Reduce motion", "Appearance") {
            SwitchRow("Reduce motion", reduceMotion, AppSettings::setReduceAnimation, icon = Icons.Rounded.Animation)
        },
        SettingEntry("Start on", "Appearance") {
            SettingRow("Start on", icon = Icons.Rounded.Home, value = listOf("Home", "Search", "Library")[startTab], chevron = true) {
                GlowPrefs.setStartTab((startTab + 1) % 3)
            }
        },
        SettingEntry("Streaming quality", "Player & audio") {
            SettingRow("Streaming quality", icon = Icons.Rounded.GraphicEq, subtitle = "Wi-Fi and mobile data", value = wifiQuality.label, chevron = true) { g.push(Route.SettingsPlayback) }
        },
        SettingEntry("Equalizer & audio", "Player & audio") {
            SettingRow("Equalizer & audio", icon = Icons.Rounded.Tune, value = if (eqEnabled) eqPreset.label() else "Off", chevron = true) { g.push(Route.Equalizer) }
        },
        SettingEntry("Autoplay", "Player & audio") {
            SwitchRow("Autoplay", autoplay, AppSettings::setAutoplay, icon = Icons.Rounded.Autorenew, subtitle = "Keep playing similar songs when the queue ends")
        },
        SettingEntry("Playback", "Player & audio") {
            SettingRow("Playback", icon = Icons.Rounded.PlayCircle, subtitle = "Automix, sound, queue and player", chevron = true) { g.push(Route.SettingsPlaybackOptions) }
        },
        SettingEntry("Lyrics sources", "Lyrics") {
            SettingRow("Lyrics sources", icon = Icons.Rounded.Lyrics, chevron = true) { g.push(Route.SettingsLyrics) }
        },
        SettingEntry("Synced lyrics", "Lyrics") {
            SwitchRow("Synced lyrics", synced, AppSettings::setSyncedLyrics, icon = Icons.Rounded.Lyrics, subtitle = "Follow along line by line")
        },
        SettingEntry("Lyrics translation", "Lyrics") {
            SettingRow("Translation", icon = Icons.Rounded.Translate, value = if (translate) translateProvider.label else "Off", chevron = true) { g.push(Route.SettingsTranslation) }
        },
        SettingEntry("Sync now", "Account sync") {
            val lastSync by GlowPrefs.lastSync.collectAsStateWithLifecycle()
            SettingRow(
                "Sync now",
                icon = Icons.Rounded.Refresh,
                subtitle = if (!signedIn) "Sign in to sync your library" else if (lastSync == 0L) "Library, playlists and likes" else "Last synced " +
                    android.text.format.DateUtils.getRelativeTimeSpanString(lastSync, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS),
            ) { g.syncAccount() }
        },
        SettingEntry("Sync automatically", "Account sync") {
            val auto by GlowPrefs.autoSync.collectAsStateWithLifecycle()
            SwitchRow("Sync automatically", auto, GlowPrefs::setAutoSync, icon = Icons.Rounded.Autorenew, subtitle = "When you come back to the app after a while. You can also pull down on Library.")
        },
        SettingEntry("Downloads & storage", "Downloads") {
            SettingRow("Downloads & storage", icon = Icons.Rounded.Storage, chevron = true) { g.push(Route.SettingsStorage) }
        },
        SettingEntry("Download on Wi-Fi only", "Downloads") {
            SwitchRow("Download on Wi-Fi only", wifiOnly, AppSettings::setWifiOnlyDownloads, icon = Icons.Rounded.Wifi)
        },
        SettingEntry("Last.fm & ListenBrainz", "Scrobbling") {
            SettingRow("Last.fm & ListenBrainz", icon = Icons.Rounded.Link, value = if (lastfmKey.isNotBlank()) lastfmUser else "Off", chevron = true) { g.push(Route.SettingsLastFm) }
        },
        SettingEntry("Privacy", "Privacy & data") {
            SettingRow("History", icon = Icons.Rounded.History, subtitle = "Pause or clear listening and search history", chevron = true) { g.push(Route.SettingsPrivacy) }
        },
        SettingEntry("Backup & restore", "Privacy & data") {
            SettingRow("Backup & restore", icon = Icons.Rounded.Backup, chevron = true) { g.push(Route.SettingsBackup) }
        },
        SettingEntry("About", "About") {
            SettingRow("Version", icon = Icons.Rounded.Info, value = BuildConfig.VERSION_NAME)
        },
    )
    val shown = if (query.isBlank()) entries else entries.filter { it.key.contains(query, true) || it.section.contains(query, true) }

    SettingsPage("Settings", large = true, glow = true) {
        SearchField(query, { query = it }, "Search settings", Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth(), height = 42.dp)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.glass)
                .clickable { if (signedIn) accountMenu = true else g.signInOpen = true }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InitialAvatar(account?.name ?: "?", size = 50.dp, imageUrl = account?.thumbnailUrl, background = c.accent, textColor = Color(0xFF111111))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                GlowText(if (signedIn) account?.name ?: "Your account" else "Sign in", GlowType.titleSmall, maxLines = 1)
                GlowText(
                    if (signedIn) account?.email?.ifBlank { null } ?: "Signed in with YouTube Music" else "Sync your library, playlists and likes",
                    GlowType.secondary,
                    color = c.textSecondary,
                    maxLines = 1,
                )
            }
            ChevronRight()
        }
        shown.groupBy { it.section }.forEach { (section, rows) ->
            GroupLabel(section)
            GlassCard(Modifier.padding(horizontal = 20.dp)) {
                rows.forEachIndexed { i, entry ->
                    entry.row()
                    if (i < rows.lastIndex) CardDivider(start = 58.dp)
                }
            }
        }
        if (query.isBlank()) {
            Text(
                "Sonder is built on BitChord, with parts from Metrolist and ViVi Music (all GPL-3.0). Not affiliated with YouTube or Google.",
                style = GlowType.caption.copy(fontSize = 12.sp),
                color = c.textTertiary,
                modifier = Modifier.padding(20.dp).clickable {
                    runCatching {
                        g.context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kushagrasinghx/BitChord")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        }
    }

    if (accountMenu) {
        GlowSheet({ accountMenu = false }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialAvatar(account?.name ?: "?", size = 48.dp, imageUrl = account?.thumbnailUrl, background = c.accent, textColor = Color(0xFF111111))
                Spacer(Modifier.width(14.dp))
                Column {
                    GlowText(account?.name ?: "Your account", GlowType.titleSmall)
                    GlowText(account?.email.orEmpty(), GlowType.secondary, color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(10.dp))
            MenuRow(Icons.Rounded.SwitchAccount, "Switch channel") {
                accountMenu = false
                g.switchChannelOpen = true
            }
            MenuRow(Icons.Rounded.Refresh, "Add another account") {
                accountMenu = false
                g.signInOpen = true
            }
            MenuRow(Icons.AutoMirrored.Rounded.Logout, "Sign out", danger = true) {
                accountMenu = false
                vm.signOut()
            }
        }
    }
}

private fun EqualizerPreset.label(): String = when (this) {
    EqualizerPreset.FLAT -> "Flat"
    EqualizerPreset.CUSTOM -> "Custom"
    else -> name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

// ---- Theme ----

@Composable
fun ThemeScreen() {
    val c = Glow.colors
    val g = LocalGlow.current
    val theme by AppSettings.themeMode.collectAsStateWithLifecycle()
    val fromArtwork by GlowPrefs.glowFromArtwork.collectAsStateWithLifecycle()
    val fixed by GlowPrefs.fixedColor.collectAsStateWithLifecycle()
    val pureBlack by GlowPrefs.pureBlack.collectAsStateWithLifecycle()
    SettingsPage("Theme") {
        // Live preview: a small phone wearing the current glow.
        Box(
            Modifier
                .padding(vertical = 10.dp)
                .align(Alignment.CenterHorizontally)
                .size(width = 156.dp, height = 266.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(c.base)
                .border(3.dp, c.glassStrong, RoundedCornerShape(24.dp)),
        ) {
            CoverGlow(g.player.song?.thumbnailUrl, height = 266.dp, intensity = 0.9f)
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(18.dp))
                Cover(g.player.song?.thumbnailUrl, size = 122.dp, shape = RoundedCornerShape(8.dp))
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(0.66f).height(8.dp).clip(CircleShape).background(c.text).align(Alignment.Start))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.4f).height(6.dp).clip(CircleShape).background(c.textSecondary).align(Alignment.Start))
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(c.textTertiary))
                Spacer(Modifier.height(12.dp))
                Box(Modifier.size(32.dp).clip(CircleShape).background(c.primary))
            }
        }
        GroupLabel("Colour")
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            SettingRow("From album art", subtitle = "Changes with every song", onClick = { GlowPrefs.setGlowFromArtwork(true) }, trailing = { GlowRadio(fromArtwork) })
            CardDivider()
            SettingRow("Fixed colour", onClick = { GlowPrefs.setGlowFromArtwork(false) }, trailing = { GlowRadio(!fromArtwork) })
            Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlowPrefs.fixedPalette.forEach { color ->
                    val chosen = !fromArtwork && fixed == color
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(if (chosen) 3.dp else 1.5.dp, if (chosen) c.text else Color(color).copy(alpha = 0.6f), CircleShape)
                            .clickable {
                                GlowPrefs.setFixedColor(color)
                                GlowPrefs.setGlowFromArtwork(false)
                            },
                    )
                }
            }
        }
        GroupLabel("Glow strength")
        val intensity by GlowPrefs.glowIntensity.collectAsStateWithLifecycle()
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Subtle", style = GlowType.secondary, color = c.textSecondary)
            GlowSlider(
                value = intensity,
                onValueChange = { GlowPrefs.setGlowIntensity((it * 10).roundToInt() / 10f) },
                valueRange = 0.3f..1.5f,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Text("Vivid", style = GlowType.secondary, color = c.textSecondary)
        }
        GroupLabel("Player")
        val liquid by GlowPrefs.liquidBackground.collectAsStateWithLifecycle()
        val shadow by GlowPrefs.coverShadow.collectAsStateWithLifecycle()
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            SwitchRow("Liquid background", liquid, GlowPrefs::setLiquidBackground, subtitle = "The cover's colours flow slowly behind the player")
            CardDivider()
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cover shadow", style = GlowType.bodyRegular.copy(fontSize = 16.sp), color = c.text, modifier = Modifier.weight(1f))
                    Text(if (shadow < 0.05f) "Off" else "${(shadow * 100).roundToInt()}%", style = GlowType.body, color = c.textSecondary)
                }
                GlowSlider(value = shadow, onValueChange = { GlowPrefs.setCoverShadow((it * 20).roundToInt() / 20f) })
            }
        }
        GroupLabel("Mode")
        Segmented(listOf("System", "Dark", "Light"), when (theme) { ThemeMode.SYSTEM -> 0; ThemeMode.DARK -> 1; ThemeMode.LIGHT -> 2 }, Modifier.padding(horizontal = 20.dp)) {
            AppSettings.setThemeMode(listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT)[it])
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 6.dp)) {
            SwitchRow("Pure black", pureBlack, GlowPrefs::setPureBlack, subtitle = "Saves battery on OLED screens")
        }
    }
}

// ---- Lyrics sources ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsSourcesScreen() {
    val c = Glow.colors
    val order by AppSettings.lyricsSourceOrder.collectAsStateWithLifecycle()
    val enabled by AppSettings.lyricsSources.collectAsStateWithLifecycle()
    val syllable by AppSettings.prioritizeSyllableSync.collectAsStateWithLifecycle()
    val synced by AppSettings.syncedLyrics.collectAsStateWithLifecycle()
    val offset by AppSettings.lyricsOffsetMs.collectAsStateWithLifecycle()
    val sources = order.filter { it in LyricsSource.offered }
    SettingsPage("Lyrics sources") {
        Text(
            "The app tries each source from top to bottom and uses the first one that has lyrics. Use the arrows to change the order.",
            style = GlowType.bodyRegular.copy(fontSize = 16.sp),
            color = c.textSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            sources.forEachIndexed { i, source ->
                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = GlowType.body.copy(fontWeight = FontWeight.SemiBold), color = c.textSecondary, modifier = Modifier.width(26.dp))
                    Column(Modifier.weight(1f)) {
                        Text(source.label, style = GlowType.bodyRegular.copy(fontSize = 16.sp), color = c.text)
                        Text(if (source.wordSynced) "Synced · word-by-word" else source.detail, style = GlowType.secondary, color = c.textSecondary, maxLines = 2)
                    }
                    GlowSwitch(source in enabled, { on ->
                        AppSettings.setLyricsSources(if (on) enabled + source else enabled - source)
                    })
                    Column {
                        IconTap(Icons.Rounded.ArrowUpward, "Move up", size = 30.dp, iconSize = 18.dp, enabled = i > 0) {
                            AppSettings.setLyricsSourceOrder(order.toMutableList().apply { val j = indexOf(source); add(j - 1, removeAt(j)) })
                        }
                        IconTap(Icons.Rounded.ArrowDownward, "Move down", size = 30.dp, iconSize = 18.dp, enabled = i < sources.lastIndex) {
                            AppSettings.setLyricsSourceOrder(order.toMutableList().apply { val j = indexOf(source); add(j + 1, removeAt(j)) })
                        }
                    }
                }
                if (i < sources.lastIndex) CardDivider()
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.padding(horizontal = 6.dp)) {
            Column {
                SwitchRow("Synced lyrics", synced, AppSettings::setSyncedLyrics, subtitle = "Follow along line by line")
                SwitchRow("Prefer word-by-word", syllable, AppSettings::setPrioritizeSyllableSync, subtitle = "Use karaoke timing when a source has it")
            }
        }
        GroupLabel("Timing offset")
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            GlowSlider(
                value = offset.toFloat(),
                onValueChange = { AppSettings.setLyricsOffsetMs((it / 100).roundToInt() * 100) },
                valueRange = AppSettings.MIN_LYRICS_OFFSET_MS.toFloat()..AppSettings.MAX_LYRICS_OFFSET_MS.toFloat(),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text("%+.1f s".format(offset / 1000f), style = GlowType.body, color = c.text, modifier = Modifier.width(60.dp))
        }
        LyricsDisplaySection()
    }
}

// ---- Storage ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageScreen() {
    val g = LocalGlow.current
    val c = Glow.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val limit by AppSettings.audioCacheLimitBytes.collectAsStateWithLifecycle()
    val saved by Downloads.saved.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    val sizes by produceState(Triple(0L, 0L, 0L), refresh, saved) {
        value = withContext(Dispatchers.IO) {
            val songCache = runCatching { AudioCache.cachedSongs().sumOf { it.bytes } }.getOrDefault(0L)
            val images = context.cacheDir.resolve("image_cache").walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val downloads = saved.values.sumOf { uri ->
                runCatching {
                    val parsed = Uri.parse(uri)
                    if (parsed.scheme == "file" || parsed.scheme == null) java.io.File(parsed.path ?: uri).length()
                    else context.contentResolver.openFileDescriptor(parsed, "r")?.use { it.statSize } ?: 0L
                }.getOrDefault(0L)
            }
            Triple(downloads, songCache, images)
        }
    }
    val (downloads, songCache, images) = sizes
    val total = (downloads + songCache + images).coerceAtLeast(1L)
    var confirm by remember { mutableStateOf<String?>(null) }

    SettingsPage("Downloads & storage") {
        Column(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.glass).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text((downloads + songCache + images).formatBytes(), style = GlowType.display.copy(fontSize = 32.sp), color = c.text)
                Spacer(Modifier.width(8.dp))
                Text("used by the app", style = GlowType.bodyRegular, color = c.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(c.glassStrong)) {
                listOf(downloads to c.text, songCache to c.accent, images to Color(0xFF5FB3AD)).forEach { (bytes, color) ->
                    if (bytes > 0) Box(Modifier.weight(bytes.toFloat() / total).fillMaxSize().background(color))
                }
                val rest = 1f - (downloads + songCache + images).toFloat() / total
                if (rest > 0.001f) Spacer(Modifier.weight(rest))
            }
            Spacer(Modifier.height(12.dp))
            listOf(Triple("Downloaded songs", downloads, c.text), Triple("Song cache", songCache, c.accent), Triple("Images", images, Color(0xFF5FB3AD))).forEach { (label, bytes, color) ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
                    Spacer(Modifier.width(10.dp))
                    Text(label, style = GlowType.bodyRegular, color = c.text, modifier = Modifier.weight(1f))
                    Text(bytes.formatBytes(), style = GlowType.bodyRegular, color = c.textSecondary)
                }
            }
        }
        GroupLabel("Song cache limit")
        val options = listOf(
            512L shl 20 to "512 MB",
            1L shl 30 to "1 GB",
            2L shl 30 to "2 GB",
            5L shl 30 to "5 GB",
            AppSettings.UNLIMITED_CACHE_LIMIT_BYTES to "No limit",
        )
        FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (bytes, label) ->
                GlowChip(label, limit == bytes, pill = true) { AppSettings.setAudioCacheLimitBytes(bytes) }
            }
        }
        Text(
            "Songs you play are kept so they start instantly next time.",
            style = GlowType.secondary,
            color = c.textSecondary,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
        )
        val quality by AppSettings.downloadQuality.collectAsStateWithLifecycle()
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            SettingRow("Manage downloads", chevron = true) { g.push(Route.Downloads) }
            CardDivider()
            SettingRow("Download quality", value = quality.label) {
                val all = DownloadQuality.entries
                AppSettings.setDownloadQuality(all[(all.indexOf(quality) + 1) % all.size])
            }
            CardDivider()
            SettingRow("Clear song cache", titleColor = c.danger) { confirm = "song" }
            CardDivider()
            SettingRow("Clear image cache", titleColor = c.danger) { confirm = "image" }
        }
    }

    confirm?.let { which ->
        GlowDialog({ confirm = null }) {
            Text(if (which == "song") "Clear song cache?" else "Clear image cache?", style = GlowType.title, color = c.text)
            Spacer(Modifier.height(8.dp))
            Text(
                if (which == "song") "Songs will stream again the next time you play them. Downloads stay." else "Covers will load again as you browse.",
                style = GlowType.bodyRegular,
                color = c.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            PillButton("Clear", Modifier.fillMaxWidth(), height = 48.dp) {
                if (which == "song") {
                    AudioCache.clear { refresh++ }
                } else {
                    scope.launch(Dispatchers.IO) {
                        SingletonImageLoader.get(context).diskCache?.clear()
                        refresh++
                    }
                }
                confirm = null
            }
            Spacer(Modifier.height(8.dp))
            PillButton("Cancel", Modifier.fillMaxWidth(), primary = false, height = 48.dp) { confirm = null }
        }
    }
}

// ---- Streaming quality ----

@Composable
fun StreamingQualityScreen() {
    val c = Glow.colors
    val wifi by AppSettings.audioQualityWifi.collectAsStateWithLifecycle()
    val cell by AppSettings.audioQualityCellular.collectAsStateWithLifecycle()
    SettingsPage("Streaming quality") {
        GroupLabel("On Wi-Fi")
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            AudioQuality.entries.forEachIndexed { i, q ->
                SettingRow(q.label, subtitle = "${q.detail} · ${q.hourly}", onClick = { AppSettings.setAudioQualityWifi(q) }, trailing = { GlowRadio(wifi == q) })
                if (i < AudioQuality.entries.lastIndex) CardDivider()
            }
        }
        GroupLabel("On mobile data")
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            AudioQuality.entries.forEachIndexed { i, q ->
                SettingRow(q.label, subtitle = "${q.detail} · ${q.hourly}", onClick = { AppSettings.setAudioQualityCellular(q) }, trailing = { GlowRadio(cell == q) })
                if (i < AudioQuality.entries.lastIndex) CardDivider()
            }
        }
        Text(
            "Higher quality uses more data on mobile networks.",
            style = GlowType.secondary,
            color = c.textSecondary,
            modifier = Modifier.padding(22.dp),
        )
    }
}

// ---- Last.fm ----

@Composable
fun LastFmScreen() {
    val g = LocalGlow.current
    val c = Glow.colors
    val scope = rememberCoroutineScope()
    val username by AppSettings.lastfmUsername.collectAsStateWithLifecycle()
    val sessionKey by AppSettings.lastfmSessionKey.collectAsStateWithLifecycle()
    val scrobble by AppSettings.lastfmScrobbleEnabled.collectAsStateWithLifecycle()
    val nowPlaying by AppSettings.lastfmNowPlaying.collectAsStateWithLifecycle()
    val primaryOnly by AppSettings.lastfmPrimaryArtistOnly.collectAsStateWithLifecycle()
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val connected = sessionKey.isNotBlank()

    SettingsPage("Scrobbling") {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(c.glass), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Link, null, tint = c.text)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                if (connected) "Connected as $username. Every song you play is added to your Last.fm profile."
                else "Scrobbling adds every song you play to your Last.fm profile.",
                style = GlowType.bodyRegular.copy(fontSize = 16.sp),
                color = c.textSecondary,
            )
        }
        if (!connected) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(8.dp))
                GlowTextField(user, { user = it }, label = "Username")
                Spacer(Modifier.height(12.dp))
                GlowTextField(password, { password = it }, label = "Password", password = true)
                error?.let { Text(it, style = GlowType.secondary, color = c.danger, modifier = Modifier.padding(top = 10.dp)) }
                Spacer(Modifier.height(16.dp))
                PillButton(if (loading) "Connecting…" else "Connect", Modifier.fillMaxWidth(), enabled = !loading && user.isNotBlank() && password.isNotBlank()) {
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            LastFM.initialize(apiKey = AppSettings.lastfmApiKey.value, secret = AppSettings.lastfmSecret.value)
                            LastFM.getMobileSession(user.trim(), password)
                                .onSuccess { auth ->
                                    AppSettings.setLastfmSessionKey(auth.session.key)
                                    AppSettings.setLastfmUsername(auth.session.name)
                                    AppSettings.setLastfmEnabled(true)
                                    AppSettings.setLastfmScrobbleEnabled(true)
                                    password = ""
                                }
                                .onFailure { error = it.message ?: "Couldn't sign in" }
                        } catch (e: Exception) {
                            error = e.message ?: "Couldn't sign in"
                        } finally {
                            loading = false
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sign in through the browser instead",
                    style = GlowType.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.text.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            scope.launch {
                                runCatching {
                                    LastFM.initialize(apiKey = AppSettings.lastfmApiKey.value, secret = AppSettings.lastfmSecret.value)
                                    val token = LastFM.getToken().getOrThrow().token
                                    g.context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(LastFM.getAuthUrl(token))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                    pendingLastFmToken = token
                                }.onFailure { error = it.message ?: "Couldn't open the browser" }
                            }
                        }
                        .padding(12.dp),
                )
                if (pendingLastFmToken != null) {
                    PillButton("I've approved it — finish", Modifier.fillMaxWidth(), primary = false, height = 48.dp) {
                        val token = pendingLastFmToken ?: return@PillButton
                        scope.launch {
                            LastFM.getSession(token)
                                .onSuccess { auth ->
                                    AppSettings.setLastfmSessionKey(auth.session.key)
                                    AppSettings.setLastfmUsername(auth.session.name)
                                    AppSettings.setLastfmEnabled(true)
                                    AppSettings.setLastfmScrobbleEnabled(true)
                                    pendingLastFmToken = null
                                }
                                .onFailure { error = it.message ?: "Not approved yet" }
                        }
                    }
                }
            }
        }
        GroupLabel(if (connected) "Scrobbling" else "After connecting")
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            SwitchRow("Scrobble songs", scrobble, AppSettings::setLastfmScrobbleEnabled)
            CardDivider()
            SwitchRow("Show \"Now playing\"", nowPlaying, AppSettings::setLastfmNowPlaying)
            CardDivider()
            SwitchRow("Main artist only", primaryOnly, AppSettings::setLastfmPrimaryArtistOnly, subtitle = "Leave featured artists out of scrobbles")
        }
        if (connected) {
            Spacer(Modifier.height(16.dp))
            PillButton("Disconnect Last.fm", Modifier.padding(horizontal = 20.dp).fillMaxWidth(), danger = true, height = 48.dp) {
                AppSettings.setLastfmSessionKey("")
                AppSettings.setLastfmUsername("")
                AppSettings.setLastfmEnabled(false)
            }
        }
        ScrobbleRulesSection()
    }
}

private var pendingLastFmToken by mutableStateOf<String?>(null)

// ---- Equalizer & audio ----

@Composable
fun EqualizerScreen() {
    val c = Glow.colors
    val enabled by AppSettings.equalizerEnabled.collectAsStateWithLifecycle()
    val bands by AppSettings.equalizerBands.collectAsStateWithLifecycle()
    val preset by AppSettings.equalizerPreset.collectAsStateWithLifecycle()
    val speed by AppSettings.playbackSpeed.collectAsStateWithLifecycle()
    val normalize by AppSettings.loudnessNormalization.collectAsStateWithLifecycle()
    val skipSilence by AppSettings.skipSilence.collectAsStateWithLifecycle()
    val crossfade by AppSettings.crossfadeSeconds.collectAsStateWithLifecycle()
    val presets = listOf(
        EqualizerPreset.FLAT, EqualizerPreset.BASS_BOOST, EqualizerPreset.VOCAL, EqualizerPreset.ROCK,
        EqualizerPreset.ELECTRONIC, EqualizerPreset.HIP_HOP, EqualizerPreset.JAZZ, EqualizerPreset.CLASSICAL,
        EqualizerPreset.ACOUSTIC, EqualizerPreset.LOUDNESS, EqualizerPreset.LATE_NIGHT, EqualizerPreset.SMALL_SPEAKERS,
    )
    SettingsPage("Equalizer & audio", glow = true, actions = {
        IconTap(Icons.Rounded.Refresh, "Reset") {
            AppSettings.setEqualizerPreset(EqualizerPreset.FLAT)
            AppSettings.setPlaybackSpeed(1f)
        }
    }) {
        Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.glass).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Equalizer", style = GlowType.title.copy(fontSize = 22.sp), color = c.text, modifier = Modifier.weight(1f))
                GlowSwitch(enabled, {
                    AppSettings.setEqualizerEnabled(it)
                    if (it) AppSettings.setEqualizerMode(com.music.bitchord.data.settings.EqualizerMode.MANUAL)
                })
            }
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets + EqualizerPreset.CUSTOM) { p ->
                    GlowChip(p.label(), preset == p) {
                        if (p != EqualizerPreset.CUSTOM) {
                            AppSettings.setEqualizerEnabled(true)
                            AppSettings.setEqualizerMode(com.music.bitchord.data.settings.EqualizerMode.MANUAL)
                            AppSettings.setEqualizerPreset(p)
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().height(260.dp)) {
                bands.forEachIndexed { i, db ->
                    BandSlider(
                        db = db,
                        hz = EqLayout.MANUAL_BANDS_HZ[i],
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) { value ->
                        AppSettings.setEqualizerMode(com.music.bitchord.data.settings.EqualizerMode.MANUAL)
                        AppSettings.setEqualizerBands(bands.toMutableList().also { it[i] = value })
                    }
                }
            }
        }
        GroupLabel("Playback")
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row {
                    Text("Tempo", style = GlowType.bodyRegular.copy(fontSize = 16.sp), color = c.text, modifier = Modifier.weight(1f))
                    Text("%.2f×".format(speed), style = GlowType.body.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp), color = c.text)
                }
                GlowSlider(
                    value = speed,
                    onValueChange = { AppSettings.setPlaybackSpeed((it * 20).roundToInt() / 20f) },
                    valueRange = 0.5f..2f,
                )
            }
            CardDivider()
            SwitchRow("Normalize volume", normalize, AppSettings::setLoudnessNormalization, subtitle = "Keep songs at a similar loudness")
            CardDivider()
            SwitchRow("Skip silence", skipSilence, AppSettings::setSkipSilence, subtitle = "Trim quiet gaps in songs")
            CardDivider()
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Crossfade", style = GlowType.bodyRegular.copy(fontSize = 16.sp), color = c.text)
                        Text(if (crossfade == 0) "Off" else "$crossfade s between songs", style = GlowType.secondary, color = c.textSecondary)
                    }
                    GlowSwitch(crossfade > 0, { AppSettings.setCrossfadeSeconds(if (it) 4 else 0) })
                }
                if (crossfade > 0) {
                    GlowSlider(
                        value = crossfade.toFloat(),
                        onValueChange = { AppSettings.setCrossfadeSeconds(it.roundToInt()) },
                        valueRange = 1f..12f,
                        steps = 10,
                    )
                }
            }
        }
    }
}

/** One vertical band: dB label on top, the fader, the frequency below. */
@Composable
private fun BandSlider(db: Float, hz: Float, enabled: Boolean, modifier: Modifier, onChange: (Float) -> Unit) {
    val c = Glow.colors
    val range = EqLayout.MANUAL_RANGE_DB
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%+.0f".format(db).replace("+0", "0") + " dB", style = GlowType.caption.copy(fontSize = 11.sp), color = c.textSecondary, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .width(36.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures { change, _ ->
                        val f = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        onChange(((f * 2 - 1) * range * 2).roundToInt() / 2f)
                    }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            val h = maxHeight
            val frac = (db / range + 1) / 2
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                drawLine(c.glassStrong, Offset(x, 0f), Offset(x, size.height), strokeWidth = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                val mid = size.height / 2
                val y = size.height * (1 - frac)
                drawLine(if (enabled) c.text else c.textTertiary, Offset(x, mid), Offset(x, y), strokeWidth = 4.dp.toPx())
                drawLine(c.textTertiary, Offset(x - 8.dp.toPx(), mid), Offset(x + 8.dp.toPx(), mid), strokeWidth = 1.dp.toPx())
            }
            Box(
                Modifier
                    .offset(y = (h - 26.dp) * (1 - frac))
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (enabled) c.text else c.textTertiary),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(if (hz >= 1000) "${(hz / 1000).let { if (it % 1 == 0f) it.toInt().toString() else "%.1f".format(it) }}k" else hz.toInt().toString(), style = GlowType.caption.copy(fontSize = 12.sp), color = c.textSecondary)
    }
}
