package com.metrolist.shared.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.shared.ui.navigation.navigationPopupSource
import com.metrolist.shared.ui.tv.tvFocusHighlight

data class PlayerMenuAction(
    val icon: ImageVector,
    val title: String,
    val description: String? = null,
    val enabled: Boolean = true,
    val selected: Boolean? = null,
    val prominent: Boolean = false,
    val onLongClick: (() -> Unit)? = null,
    val isLoading: Boolean = false,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerMenuActionGroup(
    actions: List<PlayerMenuAction>,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    com.metrolist.shared.ui.sonder.SonderGroup(modifier) {
        actions.forEachIndexed { index, action ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).combinedClickable(enabled = action.enabled, onClick = action.onClick, onLongClick = action.onLongClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerMenuActionIcon(action, Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(action.title, style = MaterialTheme.typography.bodyLarge)
                    action.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (index < actions.lastIndex) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerMenuPrimaryActions(
    actions: List<PlayerMenuAction>,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.forEach { action ->
            Surface(onClick = action.onClick, enabled = action.enabled, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f), modifier = Modifier.weight(1f)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    PlayerMenuActionIcon(action, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(action.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun PlayerMenuActionIcon(
    action: PlayerMenuAction,
    modifier: Modifier = Modifier,
) {
    if (action.isLoading) {
        ContainedLoadingIndicator(modifier)
    } else {
        Icon(action.icon, contentDescription = null, modifier = modifier)
    }
}
