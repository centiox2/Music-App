/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.metrolist.shared.ui.theme.ComponentShapes

@Composable
fun Material3MenuGroup(
    items: List<Material3MenuItemData>,
    modifier: Modifier = Modifier,
) {
    com.metrolist.shared.ui.sonder.SonderGroup(modifier) {
        items.forEachIndexed { index, item ->
            Material3MenuGroupItem(item, index, items.size)
            if (index < items.lastIndex) HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
        }
    }
}

@Composable
internal fun Material3MenuGroupItem(
    item: Material3MenuItemData,
    index: Int,
    count: Int,
) {
    Material3MenuItemRow(item, RoundedCornerShape(12.dp))
}

@Composable
private fun Material3MenuItemRow(
    item: Material3MenuItemData,
    shape: Shape,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .focusOutline(shape)
                .clickable(
                    enabled = item.onClick != null,
                    onClick = { item.onClick?.invoke() },
                ).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.icon?.let { icon ->
            icon()
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
        ) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                item.title()
            }

            item.description?.let { desc ->
                Spacer(modifier = Modifier.height(2.dp))
                ProvideTextStyle(
                    MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    desc()
                }
            }
        }
        item.trailingContent?.let { trailing ->
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

data class Material3MenuItemData(
    val icon: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    val cardColors: CardColors? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
)
