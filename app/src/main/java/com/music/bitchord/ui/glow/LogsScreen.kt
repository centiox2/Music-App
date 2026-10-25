package com.music.bitchord.ui.glow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Settings → Logs: this session's log, the last session's, and any crash reports. */
@Composable
fun LogsScreen() {
    val g = LocalGlow.current
    val c = Glow.colors
    var refresh by remember { mutableIntStateOf(0) }
    val entries by produceState(emptyList<AppLog.Entry>(), refresh) {
        value = withContext(Dispatchers.IO) { AppLog.entries(g.context) }
    }
    SettingsPage("Logs") {
        Text(
            "What the app has been doing, kept on this phone. If something goes wrong, share the log and it shows what led up to it.",
            style = GlowType.bodyRegular.copy(fontSize = 16.sp),
            color = c.textSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            entries.forEachIndexed { i, entry ->
                if (i > 0) CardDivider()
                val crash = entry.title.startsWith("Crash")
                SettingRow(
                    entry.title,
                    icon = if (crash) Icons.Rounded.BugReport else Icons.Rounded.Description,
                    subtitle = when {
                        crash -> "The app closed unexpectedly"
                        entry.title == "Last session" -> "Up to when the app last closed"
                        else -> "Since the app opened"
                    },
                    chevron = true,
                ) { g.push(Route.LogView(entry.file.path, entry.title)) }
            }
        }
        if (entries.size > 1) {
            Spacer(Modifier.height(14.dp))
            GlassCard(Modifier.padding(horizontal = 20.dp)) {
                SettingRow("Clear crash reports and last session", titleColor = c.danger) {
                    AppLog.clear(g.context)
                    refresh++
                    g.notify("Logs cleared")
                }
            }
        }
    }
}

/** One log, newest lines at the bottom, with share and copy. */
@Composable
fun LogViewScreen(path: String, title: String) {
    val g = LocalGlow.current
    val c = Glow.colors
    val entry = remember(path, title) { AppLog.Entry(title, File(path)) }
    var refresh by remember { mutableIntStateOf(0) }
    // The tail is enough on screen; sharing sends the whole log.
    val lines by produceState<List<String>?>(null, entry, refresh) {
        value = withContext(Dispatchers.IO) { AppLog.read(g.context, entry, maxChars = 400_000).lines() }
    }
    var copied by remember { mutableStateOf(false) }
    GlowScreen(glowUrl = null, showGlow = false) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar(onBack = { g.pop() }, title = title) {
                if (entry.title == "This session") {
                    IconTap(Icons.Rounded.Refresh, "Refresh") { refresh++ }
                }
                IconTap(Icons.Rounded.ContentCopy, "Copy") {
                    val text = lines.orEmpty().joinToString("\n").takeLast(100_000)
                    val clipboard = g.context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Sonder log", text))
                    copied = true
                    g.notify("Copied the last part of the log")
                }
                IconTap(Icons.Rounded.IosShare, "Share") {
                    runCatching { AppLog.share(g.context, entry) }.onFailure { g.notify("Couldn't share the log") }
                }
            }
            val shown = lines
            when {
                shown == null -> SkeletonRows(6)
                shown.all { it.isBlank() } -> Text(
                    "Nothing logged yet.",
                    style = GlowType.bodyRegular,
                    color = c.textSecondary,
                    modifier = Modifier.padding(20.dp),
                )
                else -> {
                    val state = rememberLazyListState()
                    LaunchedEffect(shown.size) { state.scrollToItem((shown.size - 1).coerceAtLeast(0)) }
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), state = state) {
                        itemsIndexed(shown) { _, line ->
                            Text(
                                line,
                                style = GlowType.secondary.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
                                color = lineColor(line, c.text, c.textSecondary),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// Errors red, warnings amber, the rest as plain text; logcat's level is the letter after the ids.
private val LEVEL = Regex("""^\S+ \S+\s+\d+\s+\d+ ([VDIWEF]) """)

private fun lineColor(line: String, text: Color, secondary: Color): Color =
    when (LEVEL.find(line)?.groupValues?.get(1)) {
        "E", "F" -> Color(0xFFFF6B6B)
        "W" -> Color(0xFFFFB454)
        "D", "V" -> secondary
        else -> text
    }
