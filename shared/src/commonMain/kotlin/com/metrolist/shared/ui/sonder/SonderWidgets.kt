package com.metrolist.shared.ui.sonder

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.generated.resources.*
import com.metrolist.shared.model.*
import com.metrolist.shared.settings.displayName
import com.metrolist.shared.ui.components.focusOutline
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.icons.MetrolistIcons
import org.jetbrains.compose.resources.stringResource

/** Presentation primitives ported from Sonder's ui/glow, independent of playback and networking. */
@Composable
fun SonderIcon(icon: ImageVector, label: String?, onClick: () -> Unit, modifier: Modifier = Modifier, glass: Boolean = false, enabled: Boolean = true, selected: Boolean? = null) {
    Box(modifier.size(44.dp).clip(CircleShape)
        .background(if (selected == true) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f) else if (glass) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f) else Color.Transparent)
        .focusOutline(CircleShape).semantics { selected?.let { this.selected = it } }.clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
    }
}

@Composable
fun SonderTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onBack != null) SonderIcon(MetrolistIcons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back), onBack, glass = true)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun SonderSection(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        if (action != null && onAction != null) Text(action, Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 8.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SonderPill(text: String, selected: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.clip(CircleShape).background(if (selected) colors.primary else colors.onSurface.copy(alpha = 0.09f))
        .focusOutline(CircleShape).semantics { this.selected = selected }.clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(text, color = if (selected) colors.onPrimary else colors.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
fun SonderSearchField(value: String, placeholder: String, onChange: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier, focusRequester: androidx.compose.ui.focus.FocusRequester? = null) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.onSurface.copy(alpha = 0.07f)).padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(MetrolistIcons.Outlined.Search, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
        BasicTextField(value, onChange, Modifier.weight(1f).padding(12.dp).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier), singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface, fontSize = 15.sp), cursorBrush = SolidColor(colors.onSurface),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { input -> Box { if (value.isBlank()) Text(placeholder, color = colors.onSurfaceVariant, fontSize = 15.sp); input() } })
        if (value.isNotEmpty()) SonderIcon(MetrolistIcons.Outlined.Close, stringResource(Res.string.clear), { onChange("") })
    }
}

fun YTItem.sonderSubtitle(): String = when (this) {
    is SongItem -> artists.joinToString { it.displayName }
    is AlbumItem -> artists.joinToString { it.displayName }
    is ArtistItem -> subscribers.orEmpty()
    is PlaylistItem -> author.orEmpty()
    is PodcastItem -> author.orEmpty()
    is BrowseItem -> subtitle.orEmpty()
    else -> ""
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SonderMediaRow(item: YTItem, onClick: () -> Unit, onMore: (() -> Unit)? = null, current: Boolean = false, enabled: Boolean = true, onLongClick: (() -> Unit)? = onMore, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(shape).background(if (current) colors.onSurface.copy(alpha = 0.09f) else Color.Transparent)
        .focusOutline(shape).combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        leading?.invoke()
        val coverShape = if (item is ArtistItem) CircleShape else RoundedCornerShape(8.dp)
        Box(Modifier.size(48.dp).clip(coverShape).background(colors.onSurface.copy(alpha = 0.09f)), contentAlignment = Alignment.Center) {
            AsyncImage(item.thumbnail, Modifier.fillMaxSize())
            if (current) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { Icon(MetrolistIcons.Outlined.Equalizer, null, tint = Color.White) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, fontSize = 15.sp, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurface.copy(alpha = if (enabled) 1f else 0.4f))
            val subtitle = item.sonderSubtitle()
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 13.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
        if (onMore != null) SonderIcon(MetrolistIcons.Filled.MoreHoriz, stringResource(Res.string.more_options), onMore)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SonderCard(item: YTItem, onClick: () -> Unit, onMore: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.width(140.dp).clip(shape).focusOutline(shape).combinedClickable(onClick = onClick, onLongClick = onMore)) {
        AsyncImage(item.thumbnail, Modifier.fillMaxWidth().aspectRatio(1f).clip(if (item is ArtistItem) CircleShape else shape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)))
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(item.sonderSubtitle(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SonderGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f)), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonderToolbar(
    title: @Composable () -> Unit, modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {}, actions: @Composable RowScope.() -> Unit = {},
    subtitle: (@Composable () -> Unit)? = null,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    expandedHeight: androidx.compose.ui.unit.Dp = 64.dp,
    collapsedHeight: androidx.compose.ui.unit.Dp = 64.dp,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    Row(modifier.fillMaxWidth().windowInsetsPadding(windowInsets).height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        navigationIcon()
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) { title() }
            subtitle?.let { ProvideTextStyle(MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) { it() } }
        }
        actions()
    }
}
