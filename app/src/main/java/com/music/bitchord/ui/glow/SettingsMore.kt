package com.music.bitchord.ui.glow

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.PlayHistory
import com.music.bitchord.data.lyrics.TRANSLATION_LANGUAGES
import com.music.bitchord.data.lyrics.translationLanguageName
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AutomixPerformanceMode
import com.music.bitchord.data.settings.OutputPcmMode
import com.music.bitchord.data.settings.SearchHistory
import com.music.bitchord.data.sources.DeviceCodecs
import com.music.bitchord.ui.glow.lyrics.TranslationMode
import com.music.bitchord.ui.glow.lyrics.TranslationPrefs
import com.music.bitchord.ui.glow.lyrics.TranslationProvider
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

// ---- Lyrics translation (Metrolist's AI settings) ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslationSettingsScreen() {
    val c = Glow.colors
    val show by TranslationPrefs.showTranslation.collectAsStateWithLifecycle()
    val provider by TranslationPrefs.provider.collectAsStateWithLifecycle()
    val mode by TranslationPrefs.mode.collectAsStateWithLifecycle()
    val apiKey by TranslationPrefs.apiKey.collectAsStateWithLifecycle()
    val model by TranslationPrefs.model.collectAsStateWithLifecycle()
    val baseUrl by TranslationPrefs.baseUrl.collectAsStateWithLifecycle()
    val deeplKey by TranslationPrefs.deeplKey.collectAsStateWithLifecycle()
    val formality by TranslationPrefs.deeplFormality.collectAsStateWithLifecycle()
    val prompt by TranslationPrefs.systemPrompt.collectAsStateWithLifecycle()
    val language by AppSettings.translationLanguage.collectAsStateWithLifecycle()
    var pickLanguage by remember { mutableStateOf(false) }
    var pickProvider by remember { mutableStateOf(false) }
    val languageLabel = if (language.isBlank()) "App language (${Locale.getDefault().displayLanguage})" else translationLanguageName(language, Locale.getDefault())

    SettingsPage("Lyrics translation") {
        Text(
            "Shows a translation under every lyric line. Tap the translate button on the lyrics screen to turn it on or off.",
            style = GlowType.bodyRegular.copy(fontSize = 16.sp),
            color = c.textSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            SwitchRow("Translate lyrics", show, TranslationPrefs::setShowTranslation, subtitle = "Translate each song's lyrics as they load")
            CardDivider()
            SettingRow("Translate into", icon = Icons.Rounded.Language, subtitle = languageLabel, chevron = true) { pickLanguage = true }
        }
        GroupLabel("Show")
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            val modes = TranslationMode.entries.filter { provider.isAi || it != TranslationMode.TRANSCRIBED }
            modes.forEachIndexed { i, m ->
                SettingRow(m.label, subtitle = m.detail, onClick = { TranslationPrefs.setMode(m) }, trailing = { GlowRadio(mode == m) })
                if (i < modes.lastIndex) CardDivider()
            }
        }
        GroupLabel("Provider")
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            SettingRow(provider.label, subtitle = provider.keyHelp, value = "Change", chevron = true) { pickProvider = true }
        }
        when {
            provider == TranslationProvider.DEEPL -> {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    GlowTextField(deeplKey, TranslationPrefs::setDeeplKey, label = "DeepL API key", password = true, placeholder = "Keys ending in :fx use the free API")
                    Spacer(Modifier.height(14.dp))
                    Text("Formality", style = GlowType.label, color = c.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("default" to "Default", "prefer_more" to "More formal", "prefer_less" to "Less formal").forEach { (v, label) ->
                            GlowChip(label, formality == v, pill = true) { TranslationPrefs.setDeeplFormality(v) }
                        }
                    }
                }
            }
            provider.isAi -> {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    GlowTextField(apiKey, TranslationPrefs::setApiKey, label = "${provider.label} API key", password = true)
                    if (provider == TranslationProvider.CUSTOM) {
                        Spacer(Modifier.height(12.dp))
                        GlowTextField(baseUrl, TranslationPrefs::setBaseUrl, label = "Endpoint URL", placeholder = "https://…/v1/chat/completions")
                    }
                    Spacer(Modifier.height(12.dp))
                    GlowTextField(model, TranslationPrefs::setModel, label = "Model", placeholder = provider.models.firstOrNull() ?: "Model name")
                    if (provider.models.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            provider.models.forEach { m -> GlowChip(m, model == m, pill = true) { TranslationPrefs.setModel(m) } }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    GlowTextField(
                        prompt,
                        TranslationPrefs::setSystemPrompt,
                        label = "Custom instructions (optional)",
                        placeholder = "Replaces the default system prompt. {lineCount} becomes the number of lines.",
                        singleLine = false,
                        minHeight = 96.dp,
                    )
                }
            }
            else -> Text(
                "Google Translate works without an account. For more natural, singable translations or transcription, choose an AI provider and add your own key.",
                style = GlowType.secondary,
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            )
        }
        Text(
            "Translations are kept on this phone, so a song is only translated once per language.",
            style = GlowType.secondary,
            color = c.textTertiary,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
        )
    }

    if (pickProvider) {
        GlowSheet({ pickProvider = false }) {
            Text("Translation provider", style = GlowType.title, color = c.text, modifier = Modifier.padding(bottom = 8.dp))
            TranslationProvider.entries.forEach { p ->
                SettingRow(p.label, subtitle = p.keyHelp, onClick = { TranslationPrefs.setProvider(p); pickProvider = false }, trailing = { GlowRadio(provider == p) })
            }
        }
    }
    if (pickLanguage) {
        GlowSheet({ pickLanguage = false }) {
            Text("Translate into", style = GlowType.title, color = c.text, modifier = Modifier.padding(bottom = 8.dp))
            var query by remember { mutableStateOf("") }
            SearchField(query, { query = it }, "Search languages", Modifier.fillMaxWidth(), height = 42.dp)
            Spacer(Modifier.height(8.dp))
            val all = remember { TRANSLATION_LANGUAGES.map { it.code to translationLanguageName(it.code, Locale.getDefault()) } }
            val shown = all.filter { query.isBlank() || it.second.contains(query, true) || it.first.contains(query, true) }
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                if (query.isBlank()) {
                    item {
                        SettingRow("App language", subtitle = Locale.getDefault().displayLanguage, onClick = {
                            AppSettings.setTranslationLanguage("")
                            pickLanguage = false
                        }, trailing = { GlowRadio(language.isBlank()) })
                    }
                }
                items(shown, key = { it.first }) { (code, name) ->
                    SettingRow(name, onClick = {
                        AppSettings.setTranslationLanguage(code)
                        pickLanguage = false
                    }, trailing = { GlowRadio(language.equals(code, true)) })
                }
            }
        }
    }
}

// ---- Lyrics display ----

@Composable
fun LyricsDisplaySection() {
    val c = Glow.colors
    val g = LocalGlow.current
    val size by GlowPrefs.lyricsTextSize.collectAsStateWithLifecycle()
    val keepOn by GlowPrefs.lyricsKeepScreenOn.collectAsStateWithLifecycle()
    val show by TranslationPrefs.showTranslation.collectAsStateWithLifecycle()
    val provider by TranslationPrefs.provider.collectAsStateWithLifecycle()
    GroupLabel("Display")
    GlassCard(Modifier.padding(horizontal = 20.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Text size", style = GlowType.bodyRegular.copy(fontSize = 16.sp), color = c.text, modifier = Modifier.weight(1f))
                Text("${size.roundToInt()}", style = GlowType.body, color = c.textSecondary)
            }
            GlowSlider(
                value = size,
                onValueChange = { GlowPrefs.setLyricsTextSize(it.roundToInt().toFloat()) },
                valueRange = 20f..40f,
            )
            Text(
                "Lyrics",
                style = GlowType.lyric.copy(fontSize = size.sp, lineHeight = (size * 1.25f).sp),
                color = c.text,
            )
        }
        CardDivider()
        SwitchRow("Keep screen on", keepOn, GlowPrefs::setLyricsKeepScreenOn, subtitle = "While lyrics are open")
        CardDivider()
        SettingRow("Translation", value = if (show) provider.label else "Off", chevron = true) { g.push(Route.SettingsTranslation) }
    }
}

// ---- Playback behaviour (BitChord's player options) ----

@Composable
fun PlaybackSettingsScreen() {
    val c = Glow.colors
    val autoplay by AppSettings.autoplay.collectAsStateWithLifecycle()
    val noRepeats by AppSettings.dontRepeatSuggestions.collectAsStateWithLifecycle()
    val musicOnly by AppSettings.preferMusicOnly.collectAsStateWithLifecycle()
    val align by AppSettings.smartVersionAlignment.collectAsStateWithLifecycle()
    val stopOnClose by AppSettings.stopOnTaskRemoved.collectAsStateWithLifecycle()
    val smartFade by AppSettings.smartFadeEnabled.collectAsStateWithLifecycle()
    val automix by AppSettings.automixPerformanceMode.collectAsStateWithLifecycle()
    val offOnSpeaker by AppSettings.loudnessOffOnSpeaker.collectAsStateWithLifecycle()
    val normalize by AppSettings.loudnessNormalization.collectAsStateWithLifecycle()
    val atmos by AppSettings.dolbyAtmos.collectAsStateWithLifecycle()
    val spatial by AppSettings.spatialAudio.collectAsStateWithLifecycle()
    val pcm by AppSettings.outputPcmMode.collectAsStateWithLifecycle()
    val usbDac by AppSettings.preferUsbDac.collectAsStateWithLifecycle()
    val hideStatus by GlowPrefs.hideStatusBarInPlayer.collectAsStateWithLifecycle()
    val swipeMini by GlowPrefs.swipeMiniPlayer.collectAsStateWithLifecycle()
    val haptics by GlowPrefs.haptics.collectAsStateWithLifecycle()

    SettingsPage("Playback") {
        GroupLabel("Queue")
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            SwitchRow("Autoplay", autoplay, AppSettings::setAutoplay, subtitle = "Keep playing similar songs when the queue ends")
            CardDivider()
            SwitchRow("Don't repeat suggestions", noRepeats, AppSettings::setDontRepeatSuggestions, subtitle = "Skip songs already played this session")
            CardDivider()
            SwitchRow("Prefer songs over videos", musicOnly, AppSettings::setPreferMusicOnly, subtitle = "Play the official audio when a music video is chosen; video soundtracks are often a weaker mix")
            CardDivider()
            SwitchRow("Match versions", align, AppSettings::setSmartVersionAlignment, subtitle = "Keep your place when switching to another version of a song")
            CardDivider()
            SwitchRow("Stop when the app is closed", stopOnClose, AppSettings::setStopOnTaskRemoved, subtitle = "Swiping the app away stops the music")
        }
        GroupLabel("Transitions")
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            SwitchRow("Automix", smartFade, AppSettings::setSmartFadeEnabled, subtitle = "Blend songs together at the best moment, like a DJ")
            if (smartFade) {
                CardDivider()
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text("Analysis", style = GlowType.bodyRegular.copy(fontSize = 16.sp), color = c.text)
                    Spacer(Modifier.height(8.dp))
                    Segmented(
                        listOf("Battery saver", "Balanced", "Fast"),
                        AutomixPerformanceMode.entries.indexOf(automix),
                    ) { AppSettings.setAutomixPerformanceMode(AutomixPerformanceMode.entries[it]) }
                }
            }
        }
        GroupLabel("Sound")
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            SwitchRow("Normalize volume", normalize, AppSettings::setLoudnessNormalization, subtitle = "Keep songs at a similar loudness")
            if (normalize) {
                CardDivider()
                SwitchRow("Not on the phone speaker", offOnSpeaker, AppSettings::setLoudnessOffOnSpeaker, subtitle = "Let quiet songs stay quiet on the built-in speaker")
            }
            if (DeviceCodecs.playsDolbyAtmos) {
                CardDivider()
                SwitchRow("Dolby Atmos", atmos, AppSettings::setDolbyAtmos, subtitle = "Use Atmos mixes when a song has one")
            }
            CardDivider()
            SwitchRow("Spatial audio", spatial, AppSettings::setSpatialAudio, subtitle = "Widen stereo for headphones")
            CardDivider()
            SwitchRow("Prefer USB DAC", usbDac, AppSettings::setPreferUsbDac, subtitle = "Send audio to a connected USB converter")
            CardDivider()
            SettingRow("Output format", value = pcm.label) {
                val all = OutputPcmMode.entries
                AppSettings.setOutputPcmMode(all[(all.indexOf(pcm) + 1) % all.size])
            }
        }
        GroupLabel("Player")
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            SwitchRow("Swipe mini player to skip", swipeMini, GlowPrefs::setSwipeMiniPlayer)
            CardDivider()
            SwitchRow("Hide status bar in player", hideStatus, GlowPrefs::setHideStatusBarInPlayer, subtitle = "More room for the artwork")
            CardDivider()
            SwitchRow("Haptic feedback", haptics, GlowPrefs::setHaptics, subtitle = "A light tap on play, skip and like")
        }
    }
}

// ---- Privacy (Metrolist's history controls) ----

@Composable
fun PrivacyScreen() {
    val c = Glow.colors
    val g = LocalGlow.current
    val pauseListen by GlowPrefs.pauseListenHistory.collectAsStateWithLifecycle()
    val pauseSearch by GlowPrefs.pauseSearchHistory.collectAsStateWithLifecycle()
    val history by PlayHistory.entries.collectAsStateWithLifecycle()
    val searches by SearchHistory.recent.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<String?>(null) }
    SettingsPage("Privacy") {
        GroupLabel("Listening history")
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            SwitchRow("Pause listening history", pauseListen, GlowPrefs::setPauseListenHistory, icon = Icons.Rounded.History, subtitle = "Songs you play aren't added to History on this phone")
            CardDivider()
            SettingRow("Clear listening history", subtitle = if (history.size == 1) "1 song" else "${history.size} songs", titleColor = c.danger) { confirm = "listen" }
        }
        GroupLabel("Search history")
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            SwitchRow("Pause search history", pauseSearch, GlowPrefs::setPauseSearchHistory, icon = Icons.Rounded.ManageSearch, subtitle = "Searches aren't remembered")
            CardDivider()
            SettingRow("Clear search history", subtitle = if (searches.size == 1) "1 search" else "${searches.size} searches", titleColor = c.danger) { confirm = "search" }
        }
        Text(
            "Your YouTube Music account keeps its own history, which you can manage at myactivity.google.com.",
            style = GlowType.secondary,
            color = c.textSecondary,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        )
    }
    confirm?.let { which ->
        GlowDialog({ confirm = null }) {
            Text(if (which == "listen") "Clear listening history?" else "Clear search history?", style = GlowType.title, color = c.text)
            Spacer(Modifier.height(8.dp))
            Text("This can't be undone.", style = GlowType.bodyRegular, color = c.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            PillButton("Clear", Modifier.fillMaxWidth(), height = 48.dp, danger = true) {
                if (which == "listen") PlayHistory.clear() else SearchHistory.clear()
                confirm = null
                g.notify("Cleared")
            }
            Spacer(Modifier.height(8.dp))
            PillButton("Cancel", Modifier.fillMaxWidth(), primary = false, height = 48.dp) { confirm = null }
        }
    }
}

// ---- Backup & restore ----

/**
 * Backs up everything that lives only on this phone (settings, listening statistics, history,
 * edited lyrics) to one file, and restores it. See [com.music.bitchord.data.SonderBackup].
 */
@Composable
fun BackupScreen() {
    val c = Glow.colors
    val g = LocalGlow.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = com.music.bitchord.data.SonderBackup.export(g.context, uri)
            busy = false
            g.notify(result.fold({ "Backed up ${it.describe()}" }, { "Couldn't save the backup" }))
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        pendingRestore = uri
    }
    SettingsPage("Backup & restore") {
        Text(
            "Save your settings, listening statistics, listening history and edited lyrics to a file, and bring them back on this or another phone. Passwords, keys and sign-ins are never included.",
            style = GlowType.bodyRegular.copy(fontSize = 16.sp),
            color = c.textSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            SettingRow(
                "Back up",
                subtitle = "Settings, statistics, history and lyrics",
                icon = Icons.Rounded.Upload,
                chevron = true,
            ) { if (!busy) export.launch(com.music.bitchord.data.SonderBackup.suggestedName()) }
            CardDivider()
            SettingRow(
                "Restore from a backup",
                subtitle = "Statistics are added to, never deleted",
                icon = Icons.Rounded.Download,
                chevron = true,
            ) { if (!busy) import.launch(arrayOf("application/json", "text/plain", "*/*")) }
            CardDivider()
            SettingRow(
                "Import from ViVi Music",
                subtitle = "Listening history from a .backup file (also Metrolist)",
                icon = Icons.Rounded.History,
                chevron = true,
            ) { if (!busy) import.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
        }
        Text(
            "Downloads, the library in your YouTube Music account and cached pictures aren't part of a backup: they come back on their own. Backups from earlier versions of Sonder and from BitChord can be restored too. From a ViVi Music backup only the listening history is taken, never its settings or sign-in, and importing the same file again adds nothing twice.",
            style = GlowType.secondary,
            color = c.textSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
    pendingRestore?.let { uri ->
        GlowDialog({ pendingRestore = null }) {
            Text("Restore from this file?", style = GlowType.title, color = c.text)
            Spacer(Modifier.height(8.dp))
            Text(
                "Settings and edited lyrics from the backup replace the ones here. Statistics and listening history are added to what's on this phone: nothing already here is deleted, and nothing is counted twice.",
                style = GlowType.bodyRegular,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            PillButton("Restore", Modifier.fillMaxWidth(), height = 48.dp) {
                pendingRestore = null
                busy = true
                scope.launch {
                    val result = com.music.bitchord.data.SonderBackup.restore(g.context, uri)
                    busy = false
                    g.notify(result.fold({ "Restored ${it.describe()}" }, { it.message ?: "That file isn't a Sonder backup" }))
                }
            }
            Spacer(Modifier.height(8.dp))
            PillButton("Cancel", Modifier.fillMaxWidth(), primary = false, height = 48.dp) { pendingRestore = null }
        }
    }
}

// ---- ListenBrainz & scrobble rules (BitChord's scrobbling) ----

@Composable
fun ScrobbleRulesSection() {
    val c = Glow.colors
    val lbEnabled by AppSettings.listenBrainzEnabled.collectAsStateWithLifecycle()
    val lbToken by AppSettings.listenBrainzToken.collectAsStateWithLifecycle()
    val minDuration by AppSettings.scrobbleMinDuration.collectAsStateWithLifecycle()
    val delayPercent by AppSettings.scrobbleDelayPercent.collectAsStateWithLifecycle()
    var token by remember(lbToken) { mutableStateOf(lbToken) }
    GroupLabel("When a song counts")
    GlassCard(Modifier.padding(horizontal = 20.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row {
                Text("After listening to", style = GlowType.bodyRegular.copy(fontSize = 16.sp), color = c.text, modifier = Modifier.weight(1f))
                Text("${(delayPercent * 100).roundToInt()}%", style = GlowType.body, color = c.textSecondary)
            }
            GlowSlider(
                value = delayPercent,
                onValueChange = { AppSettings.setScrobbleDelayPercent((it * 20).roundToInt() / 20f) },
                valueRange = 0.25f..0.9f,
            )
        }
        CardDivider()
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row {
                Text("Ignore songs shorter than", style = GlowType.bodyRegular.copy(fontSize = 16.sp), color = c.text, modifier = Modifier.weight(1f))
                Text("$minDuration s", style = GlowType.body, color = c.textSecondary)
            }
            GlowSlider(
                value = minDuration.toFloat(),
                onValueChange = { AppSettings.setScrobbleMinDuration((it / 5).roundToInt() * 5) },
                valueRange = 10f..120f,
            )
        }
    }
    GroupLabel("ListenBrainz")
    GlassCard(Modifier.padding(horizontal = 20.dp)) {
        SwitchRow("Submit listens", lbEnabled && lbToken.isNotBlank(), { on -> if (lbToken.isNotBlank()) AppSettings.setListenBrainzEnabled(on) }, icon = Icons.Rounded.Backup, subtitle = if (lbToken.isBlank()) "Add your user token below" else "Sending to listenbrainz.org")
        Column(Modifier.padding(14.dp)) {
            GlowTextField(token, { token = it }, label = "User token", password = true, placeholder = "listenbrainz.org/settings")
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                PillButton("Save", height = 40.dp, enabled = token != lbToken) {
                    AppSettings.setListenBrainzToken(token.trim())
                    AppSettings.setListenBrainzEnabled(token.isNotBlank())
                }
            }
        }
    }
    Spacer(Modifier.width(1.dp))
}
