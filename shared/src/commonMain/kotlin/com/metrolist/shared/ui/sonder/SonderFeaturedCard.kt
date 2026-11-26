package com.metrolist.shared.ui.sonder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.model.YTItem
import com.metrolist.shared.ui.components.focusOutline
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.icons.MetrolistIcons

/** Sonder's featured collection card; navigation is owned by the KMP host. */
@Composable
fun SonderFeaturedCard(shelfTitle: String, item: YTItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth().height(190.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .focusOutline(shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        AsyncImage(item.thumbnail, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxWidth().height(150.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))),
        )
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(shelfTitle, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(item.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontSize = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(MetrolistIcons.Rounded.PlayArrow, contentDescription = null, tint = Color(0xFF111111), modifier = Modifier.size(26.dp))
            }
        }
    }
}
