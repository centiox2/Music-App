@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.ui.components.focusOutline

val LocalFloatingNavBar = compositionLocalOf { false }

@Composable
internal fun <T> FloatingNavigation(
    items: List<AdaptiveNavigationItem<T>>,
    selectedKey: T?,
    onSelect: (T) -> Unit,
    navigationVisible: Boolean,
    separateKey: T?,
    itemModifier: @Composable (T) -> Modifier,
    onBarHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    AnimatedVisibility(
        visible = navigationVisible,
        modifier = modifier,
        enter =
            expandVertically(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
                expandFrom = Alignment.Bottom,
            ),
        exit =
            shrinkVertically(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
                shrinkTowards = Alignment.Bottom,
            ),
    ) {
        FloatingNavigationBar(
            items = items,
            selectedKey = selectedKey,
            onSelect = onSelect,
            separateKey = separateKey,
            itemModifier = itemModifier,
            modifier =
                Modifier.onSizeChanged { size ->
                    onBarHeightChanged(with(density) { size.height.toDp() })
                },
        )
    }
}

@Composable
private fun <T> FloatingNavigationBar(
    items: List<AdaptiveNavigationItem<T>>,
    selectedKey: T?,
    onSelect: (T) -> Unit,
    separateKey: T?,
    itemModifier: @Composable (T) -> Modifier,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(ShortNavigationBarDefaults.windowInsets)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(64.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = item.key == selectedKey
            val tint by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "sonderTabTint",
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxSize()
                    .then(itemModifier(item.key))
                    .focusOutline(shape)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item.key) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                Text(
                    item.label,
                    color = tint,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
