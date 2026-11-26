/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.shared.ui.haptics.HapticEvent
import com.metrolist.shared.ui.haptics.rememberMetrolistHaptics
import com.metrolist.shared.ui.icons.MetrolistIcons

@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val progress by androidx.compose.animation.core.animateFloatAsState(if (checked) 1f else 0f)
    val palette = androidx.compose.material3.MaterialTheme.colorScheme
    androidx.compose.foundation.Canvas(modifier.size(44.dp, 28.dp).then(
        if (onCheckedChange != null) Modifier.toggleable(checked, enabled = enabled, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onCheckedChange) else Modifier,
    )) {
        val track = if (checked) palette.primary else palette.onSurface.copy(alpha = 0.16f)
        drawRoundRect(track.copy(alpha = if (enabled) track.alpha else track.alpha * 0.38f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        val radius = size.height / 2 - 3.dp.toPx()
        drawCircle(if (checked) palette.onPrimary else palette.onSurface, radius, androidx.compose.ui.geometry.Offset(size.height / 2 + (size.width - size.height) * progress, size.height / 2))
    }
}
