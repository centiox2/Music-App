package com.metrolist.shared.ui.crash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.metrolist.shared.crash.CrashLogsState
import com.metrolist.shared.crash.CrashReport
import com.metrolist.shared.crash.CrashStore
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.crash_clear
import com.metrolist.shared.generated.resources.crash_close
import com.metrolist.shared.generated.resources.crash_copy_logs
import com.metrolist.shared.generated.resources.crash_logs
import com.metrolist.shared.generated.resources.crash_no_log
import com.metrolist.shared.generated.resources.crash_share_logs
import com.metrolist.shared.ui.utils.DragScrollableColumn
import com.metrolist.shared.ui.sonder.SonderGroup
import com.metrolist.shared.ui.sonder.SonderTopBar
import com.metrolist.shared.ui.utils.setText
import com.metrolist.shared.utils.logTextChunks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@Composable
fun CrashLogsScreen(
    store: CrashStore,
    onClose: () -> Unit,
    onShare: (String) -> Unit,
) {
    val state = remember { CrashLogsState(store) }
    val reports by state.reports.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(state) { state.load() }
    var selected by remember { mutableStateOf<CrashReport?>(null) }
    val chunks by produceState(emptyList<String>(), selected) {
        value = withContext(Dispatchers.Default) { selected?.let { logTextChunks(it.text) }.orEmpty() }
    }
    val clipboard = LocalClipboard.current
    val reportToShare = selected ?: reports.firstOrNull()
    CrashScreenshotHint(
        onCopy = if (reportToShare == null) null else ({ scope.launch { clipboard.setText(reportToShare.text) } }),
        onShare = reportToShare?.let { report -> { onShare(report.text) } },
    )
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        SonderTopBar(stringResource(Res.string.crash_logs), onBack = {
            if (selected != null) selected = null else onClose()
        }, actions = {
            if (selected == null && reports.isNotEmpty()) {
                CrashLogTextAction(stringResource(Res.string.crash_clear)) {
                    scope.launch { state.clear() }
                }
            }
        })
        Column(Modifier.weight(1f).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).padding(horizontal = 16.dp)) {
            CrashScreenshotHintText()
            if (selected != null) {
                Row {
                    CrashLogTextAction(stringResource(Res.string.crash_copy_logs)) {
                        scope.launch { clipboard.setText(selected!!.text) }
                    }
                    CrashLogTextAction(stringResource(Res.string.crash_share_logs)) { onShare(selected!!.text) }
                }
            }
            val report = selected
            if (report != null) {
                SelectionContainer(Modifier.weight(1f)) {
                    Surface(
                        Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        DragScrollableColumn(Modifier.padding(16.dp)) {
                            items(chunks.size, key = { "log-chunk:$it" }) {
                                Text(
                                    chunks[it].removeSuffix("\n"),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            } else if (reports.isEmpty()) {
                Text(stringResource(Res.string.crash_no_log), Modifier.padding(16.dp))
            } else {
                DragScrollableColumn(Modifier.fillMaxWidth()) {
                    items(reports.size, key = { index -> "crash-report:${reports[index].id}:$index" }) { index ->
                        val entry = reports[index]
                        SonderGroup(Modifier.padding(vertical = 4.dp)) {
                            Text(
                                "${Instant.fromEpochMilliseconds(entry.timestamp)} ·\u00a0${entry.version}",
                                Modifier.fillMaxWidth().clickable { selected = entry }.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashLogTextAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.labelLarge,
    )
}
