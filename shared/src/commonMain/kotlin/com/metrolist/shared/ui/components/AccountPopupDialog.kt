package com.metrolist.shared.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.metrolist.shared.generated.resources.*
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.navigation.LocalUseDesktopUi
import com.metrolist.shared.ui.navigation.blockInput
import org.jetbrains.compose.resources.stringResource

private val AccountPopupMaxWidth = 480.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPopupDialog(
    onDismiss: () -> Unit,
    isSignedIn: Boolean,
    accountName: String?,
    accountSubtitle: String?,
    accountImageUrl: String?,
    onSignIn: () -> Unit,
    onTokenLogin: () -> Unit,
    onSwitchChannel: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToIntegrations: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: (onSuccess: () -> Unit) -> Unit,
    onNavigateToHistory: (() -> Unit)? = null,
    isLoggingOut: Boolean = false,
    showStatistics: Boolean = true,
    showIntegrations: Boolean = true,
    initialFocusRequester: FocusRequester? = null,
    focusModifier: @Composable (Shape) -> Modifier = { Modifier },
    afterMenuContent: (@Composable ColumnScope.((() -> Unit) -> Unit) -> Unit)? = null,
) {
    var confirmLogout by remember { mutableStateOf(false) }
    val navigate: (() -> Unit) -> Unit = { action -> onDismiss(); action() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(accountImageUrl, Modifier.size(48.dp).clip(CircleShape))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(accountName ?: stringResource(Res.string.account_sign_in), style = MaterialTheme.typography.titleLarge)
                    accountSubtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            val entries = buildList<Pair<String, () -> Unit>> {
                add(stringResource(if (isSignedIn) Res.string.switch_youtube_channel else Res.string.account_sign_in) to { navigate(if (isSignedIn) onSwitchChannel else onSignIn) })
                add(stringResource(Res.string.account_login_with_token) to { navigate(onTokenLogin) })
                onNavigateToHistory?.let { action -> add(stringResource(Res.string.menu_history) to { navigate(action) }) }
                if (showStatistics) add(stringResource(Res.string.statistics) to { navigate(onNavigateToStatistics) })
                if (showIntegrations) add(stringResource(Res.string.integrations) to { navigate(onNavigateToIntegrations) })
                add(stringResource(Res.string.settings) to { navigate(onNavigateToSettings) })
                if (isSignedIn) add(stringResource(Res.string.action_logout) to { confirmLogout = true })
            }
            com.metrolist.shared.ui.sonder.SonderGroup {
                entries.forEachIndexed { index, (title, action) ->
                    Text(title, Modifier.fillMaxWidth().clickable(enabled = !isLoggingOut, onClick = action).padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                    if (index < entries.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                }
            }
            afterMenuContent?.invoke(this) { action -> navigate(action) }
        }
    }
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text(stringResource(Res.string.action_logout)) }, text = { Text(stringResource(Res.string.account_logout_confirm)) },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout(onDismiss) }) { Text(stringResource(Res.string.action_logout)) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(Res.string.cancel)) } })
}
