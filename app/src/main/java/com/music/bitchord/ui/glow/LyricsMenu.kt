/*
 * The lyrics screen's menu, after Metrolist's LyricsMenu
 * (https://github.com/metrolistgroup/metrolist, GPL-3.0): refetch, edit,
 * choose another source, nudge the timing, translate.
 */
package com.music.bitchord.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.toEnhancedLrc
import com.music.bitchord.data.lyrics.toLrc
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.LyricsProviderState
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.glow.lyrics.TranslationPrefs

@Composable
fun LyricsMenuSheet(onDismiss: () -> Unit) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val source by vm.lyricsSource.collectAsStateWithLifecycle()
    val edited by vm.lyricsEdited.collectAsStateWithLifecycle()
    val states by vm.lyricsProviderStates.collectAsStateWithLifecycle()
    val enabled by AppSettings.lyricsSources.collectAsStateWithLifecycle()
    val order by AppSettings.lyricsSourceOrder.collectAsStateWithLifecycle()
    val offset by AppSettings.lyricsOffsetMs.collectAsStateWithLifecycle()
    val translate by TranslationPrefs.showTranslation.collectAsStateWithLifecycle()
    val song = g.player.song
    val close: (() -> Unit) -> Unit = { action -> onDismiss(); action() }

    GlowSheet(onDismiss, color = playerSheetColor(song?.thumbnailUrl)) {
        Text("Lyrics", style = GlowType.title, color = c.text)
        Text(
            when {
                edited -> "Your edit"
                source != null -> "From ${source?.label}"
                lyrics.isNullOrEmpty() -> "No lyrics found yet"
                else -> "From this song's file"
            },
            style = GlowType.secondary,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        MenuRow(Icons.Rounded.Refresh, "Refetch lyrics") { close { vm.refetchLyrics() } }
        MenuRow(Icons.Rounded.Edit, if (lyrics.isNullOrEmpty()) "Write lyrics" else "Edit lyrics") { close { g.lyricsEditorOpen = true } }
        // The video's artist is often the channel that uploaded it, not the
        // artist on the record, so the automatic lookup can miss. This asks
        // the sources about a name the user types instead.
        MenuRow(Icons.Rounded.Search, "Search for lyrics") { close { g.lyricsSearchOpen = true } }
        if (edited) MenuRow(Icons.Rounded.Restore, "Undo my edit") { close { vm.resetEditedLyrics() } }
        MenuRow(Icons.Rounded.Translate, if (translate) "Hide translation" else "Translate") {
            close { TranslationPrefs.setShowTranslation(!translate) }
        }
        // Timing: nudge in half-second steps, the way the lines run early or late.
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Timing", style = GlowType.bodyRegular.copy(fontSize = 16.sp), color = c.text)
                Text(
                    when {
                        offset == 0 -> "In time"
                        offset > 0 -> "Lyrics %.1f s later".format(offset / 1000f)
                        else -> "Lyrics %.1f s earlier".format(-offset / 1000f)
                    },
                    style = GlowType.secondary,
                    color = c.textSecondary,
                )
            }
            PillButton("−0.5 s", primary = false, height = 38.dp) { AppSettings.setLyricsOffsetMs(offset - 500) }
            Spacer(Modifier.width(8.dp))
            PillButton("+0.5 s", primary = false, height = 38.dp) { AppSettings.setLyricsOffsetMs(offset + 500) }
        }
        GroupLabel("Source", Modifier.padding(start = 0.dp))
        val sources = LyricsSource.ordered(order).filter { it in enabled }
        LazyColumn(Modifier.heightIn(max = 300.dp)) {
            items(sources, key = { it.name }) { s ->
                val state = states[s]
                val current = !edited && source == s
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (current) c.glass else androidx.compose.ui.graphics.Color.Transparent)
                        .then(if (state == LyricsProviderState.NOT_FOUND) Modifier else Modifier.clickableNoRipple { vm.selectLyricsProvider(s) })
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.label, style = GlowType.body, color = if (state == LyricsProviderState.NOT_FOUND) c.textTertiary else c.text)
                        Text(
                            when (state) {
                                LyricsProviderState.FOUND -> if (s.wordSynced) "Found · may be word-by-word" else "Found"
                                LyricsProviderState.FETCHING -> "Looking…"
                                LyricsProviderState.NOT_FOUND -> "Nothing for this song"
                                else -> "Tap to try"
                            },
                            style = GlowType.secondary,
                            color = c.textSecondary,
                        )
                    }
                    when {
                        current -> Icon(Icons.Rounded.Check, "In use", tint = c.text, modifier = Modifier.size(20.dp))
                        state == LyricsProviderState.FETCHING -> CircularProgressIndicator(Modifier.size(16.dp), color = c.textSecondary, strokeWidth = 2.dp)
                    }
                }
            }
        }
        if (song != null) {
            Spacer(Modifier.height(6.dp))
            MenuRow(Icons.Rounded.MoreHoriz, "Song options") { close { g.openSongMenu(song, fromPlayer = true) } }
        }
    }
}

/**
 * A plain editor over the lyrics as LRC: word stamps kept when the source had
 * them, line stamps otherwise, so a fix to one word doesn't throw the timing away.
 */
@Composable
fun LyricsEditor(onDismiss: () -> Unit) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    var text by remember {
        mutableStateOf(
            lyrics.orEmpty().let { lines ->
                when {
                    lines.isEmpty() -> ""
                    lines.any { it.isWordSynced } -> lines.toEnhancedLrc()
                    lines.any { it.timeMs > 0 } -> lines.toLrc()
                    else -> lines.joinToString("\n") { it.text }
                }
            },
        )
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(c.base).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTap(Icons.Rounded.Close, "Cancel", onClick = onDismiss)
                Column(Modifier.weight(1f)) {
                    Text("Edit lyrics", style = GlowType.titleSmall, color = c.text)
                    Text(g.player.song?.title.orEmpty(), style = GlowType.secondary, color = c.textSecondary, maxLines = 1)
                }
                PillButton("Save", height = 40.dp) {
                    vm.saveEditedLyrics(text)
                    g.notify("Lyrics saved for this song")
                    onDismiss()
                }
            }
            Text(
                "Keep the [mm:ss.xx] stamps to stay in sync. Without them, the lyrics show as plain text.",
                style = GlowType.secondary,
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Box(
                Modifier
                    .padding(16.dp)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.glass)
                    .padding(14.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = GlowType.bodyRegular.copy(color = c.text, fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 21.sp),
                    cursorBrush = SolidColor(c.text),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit) = this.then(Modifier.clickable(onClick = onClick))

/**
 * "Find lyrics": searches the enabled sources by a title and artist the user
 * types, instead of by the video's own metadata. A found result is applied to
 * the playing song straight away and cached against it, so the fix lasts.
 */
@Composable
fun LyricsSearchDialog(onDismiss: () -> Unit) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val song = g.player.song
    val state by vm.lyricsSearch.collectAsStateWithLifecycle()
    var title by rememberSaveable { mutableStateOf(song?.title.orEmpty()) }
    var artist by rememberSaveable { mutableStateOf(song?.artist.orEmpty()) }
    val close: () -> Unit = { vm.clearLyricsSearch(); onDismiss() }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(c.base).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTap(Icons.Rounded.Close, "Close", onClick = close)
                Column(Modifier.weight(1f)) {
                    Text("Find lyrics", style = GlowType.titleSmall, color = c.text)
                    Text(
                        song?.title.orEmpty(),
                        style = GlowType.secondary,
                        color = c.textSecondary,
                        maxLines = 1,
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    "The artist on a YouTube upload is often the channel, not the one on the record. " +
                        "Type the song and its artist as they appear on the release.",
                    style = GlowType.bodyRegular,
                    color = c.textSecondary,
                )
                Spacer(Modifier.height(20.dp))
                GlowTextField(title, { title = it }, label = "Song title")
                Spacer(Modifier.height(12.dp))
                GlowTextField(artist, { artist = it }, label = "Artist")
                Spacer(Modifier.height(18.dp))
                PillButton(
                    if (state is MainViewModel.LyricsSearchState.Searching) "Searching…" else "Search",
                    Modifier.fillMaxWidth(),
                    enabled = title.isNotBlank() && state !is MainViewModel.LyricsSearchState.Searching,
                ) { vm.searchLyrics(title, artist) }
                Spacer(Modifier.height(18.dp))
                when (val s = state) {
                    is MainViewModel.LyricsSearchState.Searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = c.textSecondary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Asking the sources…", style = GlowType.secondary, color = c.textSecondary)
                    }
                    is MainViewModel.LyricsSearchState.Found -> {
                        Text("Found · ${s.source.label}", style = GlowType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.text)
                        Spacer(Modifier.height(4.dp))
                        Text("These lyrics are showing now, and saved for this song.", style = GlowType.secondary, color = c.textSecondary)
                        Spacer(Modifier.height(12.dp))
                        PillButton("Back to the song", Modifier.fillMaxWidth(), primary = false, height = 46.dp) { close() }
                    }
                    MainViewModel.LyricsSearchState.Idle -> Unit
                    is MainViewModel.LyricsSearchState.NotFound -> {
                        Text("Nothing found", style = GlowType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.text)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "No source had lyrics for \"${s.title}\" by ${s.artist.ifBlank { "that artist" }}. Try a shorter title, or the artist's name only.",
                            style = GlowType.secondary,
                            color = c.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}
