package com.metrolist.shared.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GroupedListItem(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isCurrent: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(modifier) { content() }
}
