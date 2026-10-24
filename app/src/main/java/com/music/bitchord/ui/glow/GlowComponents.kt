package com.music.bitchord.ui.glow

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ripple
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.player.rememberArtworkColors

/** Plain clickable without the ripple, matching the design's flat taps. */
@Composable
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )

/** Album/playlist/artist artwork with a glass placeholder while it loads. */
@Composable
fun Cover(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(8.dp),
    px: Int = 544,
) {
    val c = Glow.colors
    // Large covers sit on a soft shadow; list-row thumbnails stay flat.
    val shadowed = size >= 120.dp
    Box(
        modifier
            .size(size)
            .then(if (shadowed) Modifier.coverShadow(if (shape == CircleShape) size / 2 else 12.dp, weight = (size.value / 300f).coerceIn(0.35f, 1f)) else Modifier)
            .clip(shape)
            .background(c.glassStrong),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            Icon(Icons.Rounded.MusicNote, null, tint = c.textTertiary, modifier = Modifier.size(size / 2.4f))
        } else {
            ArtImage(url, px, Modifier.fillMaxSize())
        }
    }
}

/** Same as [Cover] but filling the width it is given, square. */
@Composable
fun SquareCover(url: String?, modifier: Modifier = Modifier, radius: Dp = 12.dp, px: Int = 720) {
    val c = Glow.colors
    Box(
        modifier
            .fillMaxWidth()
            .aspectSquare()
            .coverShadow(radius, weight = 0.6f)
            .clip(RoundedCornerShape(radius))
            .background(c.glassStrong),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            Icon(Icons.Rounded.MusicNote, null, tint = c.textTertiary, modifier = Modifier.size(40.dp))
        } else {
            ArtImage(url, px, Modifier.fillMaxSize())
        }
    }
}

fun Modifier.aspectSquare(): Modifier = aspectRatio(1f)

/**
 * The signature of the design: soft blooms of the playing cover's colours
 * sitting behind the top of a screen. Drawn as radial gradients rather than a
 * real blur, so it costs nothing on any Android version.
 */
@Composable
fun CoverGlow(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    height: Dp = 420.dp,
    intensity: Float = 0.75f,
) {
    val c = Glow.colors
    val fromArtwork by GlowPrefs.glowFromArtwork.collectAsStateWithLifecycle()
    val fixed by GlowPrefs.fixedColor.collectAsStateWithLifecycle()
    // Cached per cover, so reopening a screen starts on the right colours instead of flashing stock ones.
    val palette = rememberGlowPalette(if (fromArtwork) artworkUrl else null) ?: ArtworkPalettes.last.takeIf { artworkUrl != null }.orEmpty()
    val baseColors = if (fromArtwork && artworkUrl != null && palette.size >= 2) {
        palette
    } else {
        val f = Color(fixed)
        listOf(f, f.copy(alpha = 0.8f).compositeOverBlack(1.25f), f, f)
    }
    val a by animateColorAsState(baseColors[0], tween(1200), label = "glowA")
    val b by animateColorAsState(baseColors.getOrElse(1) { baseColors[0] }, tween(1200), label = "glowB")
    val userIntensity by GlowPrefs.glowIntensity.collectAsStateWithLifecycle()
    val strength = ((if (c.isDark) intensity else intensity * 0.55f) * userIntensity).coerceAtMost(1f)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.radialGradient(
                listOf(a.copy(alpha = strength), Color.Transparent),
                center = Offset(w * 0.3f, h * 0.18f),
                radius = w * 0.75f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(b.copy(alpha = strength * 0.85f), Color.Transparent),
                center = Offset(w * 0.8f, h * 0.3f),
                radius = w * 0.6f,
            ),
        )
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, c.base), startY = h * 0.55f, endY = h))
    }
}

private fun Color.compositeOverBlack(lift: Float): Color =
    Color(
        red = (red * lift).coerceIn(0f, 1f),
        green = (green * lift).coerceIn(0f, 1f),
        blue = (blue * lift).coerceIn(0f, 1f),
        alpha = alpha,
    )

/** A screen: base colour, optional cover glow behind the top, content above. */
@Composable
fun GlowScreen(
    glowUrl: String? = null,
    showGlow: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Glow.colors.base)) {
        if (showGlow) CoverGlow(glowUrl)
        content()
    }
}

@Composable
fun GlowText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Glow.colors.text,
    maxLines: Int = Int.MAX_VALUE,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        fontWeight = fontWeight,
    )
}

/** Large page title row: "Home", "Library", with round actions on the right. */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlowText(title, GlowType.display, Modifier.weight(1f), maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = actions)
    }
}

/** Back arrow + optional inline title + trailing actions, for pushed pages. */
@Composable
fun TopBar(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String? = null,
    centered: Boolean = false,
    backIcon: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 8.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconTap(backIcon, "Back", onClick = onBack, size = 44.dp, iconSize = 24.dp)
            } else {
                Spacer(Modifier.width(12.dp))
            }
            if (title != null && !centered) {
                GlowText(title, GlowType.title, Modifier.padding(start = 8.dp).weight(1f), maxLines = 1)
            } else {
                Spacer(Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        if (title != null && centered) {
            GlowText(title, GlowType.titleSmall, Modifier.align(Alignment.Center), maxLines = 1)
        }
    }
}

/** A bare icon with a generous touch target. */
@Composable
fun IconTap(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Glow.colors.text,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val animatedTint by animateColorAsState(tint.copy(alpha = if (enabled) tint.alpha else 0.35f), label = "iconTint")
    Box(
        modifier
            .size(size)
            .pressScale(interaction, 0.86f)
            .clip(CircleShape)
            .clickable(interaction, ripple(bounded = true), enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = animatedTint, modifier = Modifier.size(iconSize))
    }
}

/** 44px glass circle used in page headers (search, avatar, settings). */
@Composable
fun GlassCircle(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    background: Color = Glow.colors.glass,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .pressScale(interaction, 0.9f)
            .clip(CircleShape)
            .background(background)
            .clickable(interaction, ripple(), onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** The white "primary" circle with a dark glyph. */
@Composable
fun PrimaryCircle(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = Glow.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .pressScale(interaction, 0.88f)
            .clip(CircleShape)
            .background(if (enabled) c.primary else c.primary.copy(alpha = 0.45f))
            .clickable(interaction, ripple(), enabled = enabled, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        // Play ⇄ pause: the old glyph shrinks away as the new one grows in.
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (scaleIn(tween(220), initialScale = 0.6f) + fadeIn(tween(160))) togetherWith
                    (scaleOut(tween(180), targetScale = 0.6f) + fadeOut(tween(120)))
            },
            label = "primaryIcon",
        ) { glyph ->
            Icon(glyph, null, tint = c.onPrimary, modifier = Modifier.size(iconSize))
        }
    }
}

/** Filter chip: white when selected, glass otherwise. Radius 10. */
@Composable
fun GlowChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    pill: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Glow.colors
    val bg by animateColorAsState(if (selected) c.primary else c.glass, label = "chip")
    val fg by animateColorAsState(if (selected) c.onPrimary else c.text, label = "chipText")
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(if (pill) 40.dp else 34.dp)
            .pressScale(interaction, 0.93f)
            .clip(if (pill) CircleShape else RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(interaction, ripple(), onClick = onClick)
            .padding(horizontal = if (pill) 16.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = GlowType.label.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
fun ChipRow(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    onSelect: (Int) -> Unit,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding,
    ) {
        items(options.size) { i ->
            GlowChip(options[i], selected == i) { onSelect(i) }
        }
    }
}

/** Full-width pill button. Primary is white; secondary is glass. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 54.dp,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = Glow.colors
    val bg = when {
        danger -> c.danger.copy(alpha = 0.12f)
        primary -> c.primary
        else -> c.glassStrong
    }
    val fg = when {
        danger -> c.danger
        primary -> c.onPrimary
        else -> c.text
    }
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(height)
            .pressScale(interaction, 0.96f)
            .clip(CircleShape)
            .background(bg)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(interaction, ripple(), enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = GlowType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp), color = fg, maxLines = 1)
    }
}

/** A text link like "Play all", "See all", "Clear". */
@Composable
fun TextAction(text: String, modifier: Modifier = Modifier, color: Color = Glow.colors.textSecondary, onClick: () -> Unit) {
    Text(
        text,
        style = GlowType.secondary.copy(fontSize = 14.sp),
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlowText(title, GlowType.title, Modifier.weight(1f), maxLines = 1)
        if (action != null) TextAction(action, onClick = onAction)
    }
}

/** Small grey caption that heads a group in settings and sheets. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = GlowType.section,
        color = Glow.colors.textSecondary,
        modifier = modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 8.dp),
    )
}

/** Animated "now playing" bars shown in place of a track number. */
@Composable
fun PlayingBars(modifier: Modifier = Modifier, playing: Boolean = true, color: Color = Glow.colors.text) {
    val t = rememberInfiniteTransition(label = "bars")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    Canvas(modifier.size(16.dp)) {
        val bw = size.width / 5f
        val heights = if (playing) {
            listOf(0.45f + 0.5f * phase, 0.95f - 0.5f * phase, 0.6f + 0.35f * phase)
        } else {
            listOf(0.4f, 0.7f, 0.5f)
        }
        heights.forEachIndexed { i, hf ->
            val x = bw * (i * 2) + bw / 2
            drawLine(
                color,
                Offset(x, size.height),
                Offset(x, size.height * (1 - hf)),
                strokeWidth = bw,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

/**
 * The workhorse list row: cover, title, subtitle, and a trailing slot (⋯ by
 * default). [leading] replaces the cover, e.g. with a track number.
 */
@Composable
fun SongRow(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    coverSize: Dp = 48.dp,
    circleCover: Boolean = false,
    active: Boolean = false,
    dimmed: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** The row's track, when it is one: the row then marks itself while that track plays. */
    videoId: String? = null,
    selected: Boolean = false,
) {
    val c = Glow.colors
    val player = LocalGlow.current.player
    val playing = active || (videoId != null && player.song?.videoId == videoId)
    val interaction = remember { MutableInteractionSource() }
    val highlight by animateColorAsState(
        when {
            selected -> c.primary.copy(alpha = 0.16f)
            playing -> c.glass
            else -> Color.Transparent
        },
        label = "rowHighlight",
    )
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .combinedClickable(interaction, ripple(), onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(highlight)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .alpha(if (dimmed) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            GlowCheckbox(true)
            Spacer(Modifier.width(12.dp))
        }
        if (leading != null) {
            leading()
        } else {
            Box(contentAlignment = Alignment.Center) {
                Cover(artworkUrl, size = coverSize, shape = if (circleCover) CircleShape else RoundedCornerShape(8.dp))
                // The same mark everywhere a playing track is listed: bars over a dimmed cover.
                androidx.compose.animation.AnimatedVisibility(playing, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier.size(coverSize).clip(if (circleCover) CircleShape else RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) { PlayingBars(Modifier.size(18.dp), playing = player.isPlaying, color = Color.White) }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            GlowText(
                title,
                GlowType.body.copy(fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Medium),
                maxLines = 1,
            )
            if (subtitle.isNotEmpty()) GlowText(subtitle, GlowType.secondary, color = c.textSecondary, maxLines = 1)
        }
        trailing?.invoke()
    }
}

@Composable
fun MoreButton(onClick: () -> Unit) {
    IconTap(Icons.Rounded.MoreHoriz, "More options", tint = Glow.colors.textSecondary, onClick = onClick)
}

@Composable
fun ChevronRight() {
    Icon(Icons.Rounded.ChevronRight, null, tint = Glow.colors.textTertiary, modifier = Modifier.size(22.dp))
}

/** Standard row for a [Song] with ⋯ that opens the song menu. */
@Composable
fun TrackRow(
    song: Song,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = song.artist,
    active: Boolean = false,
    dimmed: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    SongRow(
        title = song.title,
        subtitle = subtitle,
        artworkUrl = song.thumbnailUrl,
        modifier = modifier,
        active = active,
        dimmed = dimmed,
        onClick = onClick,
        onLongClick = onMore,
        trailing = trailing ?: { MoreButton(onMore) },
        videoId = song.videoId,
    )
}

/** Rounded translucent card that groups rows. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 14.dp,
    color: Color = Glow.colors.glass,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(color),
        content = content,
    )
}

@Composable
fun CardDivider(modifier: Modifier = Modifier, start: Dp = 14.dp) {
    Box(
        modifier
            .padding(start = start, end = 14.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Glow.colors.divider),
    )
}

/**
 * The design's switch: a white track with a dark knob when on, a glass track
 * with a white knob when off.
 */
@Composable
fun GlowSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Glow.colors
    val pos by animateFloatAsState(if (checked) 1f else 0f, tween(180), label = "switch")
    val track by animateColorAsState(if (checked) c.primary else c.glassStrong, label = "track")
    val knob by animateColorAsState(if (checked) c.base else Color.White, label = "knob")
    Box(
        modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(CircleShape)
            .background(track)
            .alpha(if (enabled) 1f else 0.4f)
            .tap(enabled) { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .offset(x = 18.dp * pos)
                .size(22.dp)
                .clip(CircleShape)
                .background(knob)
                .then(if (!checked && !c.isDark) Modifier.border(1.dp, c.divider, CircleShape) else Modifier),
        )
    }
}

@Composable
fun GlowRadio(selected: Boolean, modifier: Modifier = Modifier) {
    val c = Glow.colors
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) c.text else c.textTertiary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(c.text))
    }
}

@Composable
fun GlowCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    val c = Glow.colors
    Box(
        modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) c.primary else Color.Transparent)
            .border(2.dp, if (checked) c.primary else c.textTertiary, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Rounded.Check, null, tint = c.onPrimary, modifier = Modifier.size(16.dp))
    }
}

/**
 * One row in a settings card. Shows an optional icon tile, a title and
 * subtitle, and either a value + chevron, a switch, or custom trailing content.
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    value: String? = null,
    chevron: Boolean = false,
    titleColor: Color = Glow.colors.text,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Glow.colors
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(c.glass),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = c.text, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = GlowType.bodyRegular.copy(fontSize = 16.sp), color = titleColor)
            if (subtitle != null) Text(subtitle, style = GlowType.secondary, color = c.textSecondary)
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, style = GlowType.secondary.copy(fontSize = 14.sp), color = c.textSecondary, maxLines = 1)
        }
        trailing?.let {
            Spacer(Modifier.width(10.dp))
            it()
        }
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            ChevronRight()
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
) {
    SettingRow(
        title = title,
        modifier = modifier,
        icon = icon,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        trailing = { GlowSwitch(checked, onCheckedChange) },
    )
}

/** Segmented control: a glass track with a white sliding pill. */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val c = Glow.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.glass)
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (sel) c.primary else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = GlowType.label.copy(fontSize = 14.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (sel) c.onPrimary else c.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Search field: glass rounded rectangle, magnifier, optional clear. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    outlined: Boolean = false,
    autoFocusModifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
) {
    val c = Glow.colors
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(c.glass)
            .then(if (outlined) Modifier.border(1.dp, c.textTertiary, RoundedCornerShape(14.dp)) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = GlowType.bodyRegular, color = c.textTertiary, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = GlowType.bodyRegular.copy(color = c.text),
                cursorBrush = SolidColor(c.text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().then(autoFocusModifier),
            )
        }
        if (value.isNotEmpty()) {
            IconTap(Icons.Rounded.Close, "Clear", tint = c.textSecondary, size = 32.dp, iconSize = 18.dp) { onValueChange("") }
        }
    }
}

/** Labelled text input used in forms (new playlist, Last.fm, edit playlist). */
@Composable
fun GlowTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    minHeight: Dp = 50.dp,
    password: Boolean = false,
) {
    val c = Glow.colors
    Column(modifier) {
        if (label != null) {
            Text(label, style = GlowType.secondary, color = c.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (singleLine) minHeight else minHeight * 1.2f)
                .clip(RoundedCornerShape(12.dp))
                .background(c.base.copy(alpha = 0.6f))
                .border(1.dp, c.textTertiary, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
        ) {
            if (value.isEmpty()) Text(placeholder, style = GlowType.bodyRegular, color = c.textTertiary)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = GlowType.bodyRegular.copy(color = c.text, fontSize = 16.sp),
                cursorBrush = SolidColor(c.text),
                visualTransformation = if (password) {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The design's bottom sheet: #13292A, radius 22, a short grab handle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlowSheet(
    onDismiss: () -> Unit,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Glow.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.sheet,
        contentColor = c.text,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(c.textTertiary),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 20.dp),
            content = content,
        )
    }
}

/** A centred dialog card for errors and confirmations. */
@Composable
fun GlowDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        val c = Glow.colors
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(c.sheet)
                .border(1.dp, c.divider, RoundedCornerShape(22.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/** Shimmering placeholder block for the loading state. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, radius: Dp = 8.dp) {
    val c = Glow.colors
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "x")
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(c.skeleton)
            .background(
                Brush.linearGradient(
                    listOf(Color.Transparent, c.skeleton, Color.Transparent),
                    start = Offset(x * 600f, 0f),
                    end = Offset(x * 600f + 400f, 0f),
                ),
            ),
    )
}

@Composable
fun SkeletonRows(count: Int = 5, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(count) { i ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBox(Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBox(Modifier.width((180 - (i % 3) * 30).dp).height(12.dp), radius = 6.dp)
                    SkeletonBox(Modifier.width((110 - (i % 2) * 20).dp).height(10.dp), radius = 5.dp)
                }
            }
        }
    }
}

/** Centred empty / error state: icon tile, headline, body, actions. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    iconTint: Color = Glow.colors.accent,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val c = Glow.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.offset(x = (-26).dp, y = 6.dp).size(92.dp).clip(RoundedCornerShape(14.dp)).background(c.glass))
            Box(Modifier.offset(x = 26.dp, y = (-4).dp).size(92.dp).clip(RoundedCornerShape(14.dp)).background(c.glass))
            Box(
                Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.miniPlayer)
                    .border(1.dp, c.divider, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(34.dp)) }
        }
        Spacer(Modifier.height(30.dp))
        Text(title, style = GlowType.title.copy(fontSize = 22.sp), color = c.text)
        Spacer(Modifier.height(10.dp))
        Text(
            body,
            style = GlowType.bodyRegular,
            color = c.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        actions()
    }
}

/** Round avatar with an initial — used for the account and party members. */
@Composable
fun InitialAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    imageUrl: String? = null,
    background: Color = Glow.colors.glass,
    textColor: Color = Glow.colors.text,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "?",
                style = GlowType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.4f).sp),
                color = textColor,
            )
        }
    }
}

/** Thin progress bar (downloads, storage). */
@Composable
fun ThinProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = Glow.colors.text, height: Dp = 4.dp) {
    val c = Glow.colors
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(c.glassStrong)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(CircleShape).background(color))
    }
}

fun Long.formatClock(): String {
    if (this < 0) return "--:--"
    val total = this / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun Long.formatBytes(): String = when {
    this >= 1L shl 30 -> "%.1f GB".format(this / (1L shl 30).toDouble())
    this >= 1L shl 20 -> "%.0f MB".format(this / (1L shl 20).toDouble())
    this >= 1L shl 10 -> "%.0f KB".format(this / (1L shl 10).toDouble())
    else -> "$this B"
}

/**
 * The Glow slider: a thin glass track, a white fill, and a round thumb that
 * swells while it's held — Material's default track is thick and dotted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlowSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val c = Glow.colors
    val interaction = remember { MutableInteractionSource() }
    val dragged by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val thumbScale by androidx.compose.animation.core.animateFloatAsState(if (dragged || pressed) 1.3f else 1f, GlowMotion.press, label = "thumb")
    androidx.compose.material3.Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interaction,
        thumb = {
            Box(
                Modifier
                    .size(18.dp)
                    .graphicsLayer { scaleX = thumbScale; scaleY = thumbScale }
                    .shadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(if (enabled) c.text else c.textTertiary),
            )
        },
        track = { state ->
            val span = (state.valueRange.endInclusive - state.valueRange.start).takeIf { it > 0f } ?: 1f
            val fraction = ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                drawRoundRect(c.glassStrong, cornerRadius = r)
                drawRoundRect(if (enabled) c.text else c.textTertiary, size = size.copy(width = size.width * fraction), cornerRadius = r)
            }
        },
    )
}
