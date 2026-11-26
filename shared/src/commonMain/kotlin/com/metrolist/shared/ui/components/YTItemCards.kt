/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselItemDrawInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.shared.constants.CarouselItemCornerRadius
import com.metrolist.shared.constants.GridCaptionSafeSpace
import com.metrolist.shared.constants.ThumbnailCornerRadius
import com.metrolist.shared.constants.currentGridThumbnailHeight
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.album_type
import com.metrolist.shared.generated.resources.artist
import com.metrolist.shared.generated.resources.artist_type
import com.metrolist.shared.generated.resources.browse
import com.metrolist.shared.generated.resources.episode
import com.metrolist.shared.generated.resources.monthly_audience_short
import com.metrolist.shared.generated.resources.more_options
import com.metrolist.shared.generated.resources.music_video_type
import com.metrolist.shared.generated.resources.n_track
import com.metrolist.shared.generated.resources.playlist
import com.metrolist.shared.generated.resources.podcast
import com.metrolist.shared.generated.resources.song_type
import com.metrolist.shared.generated.resources.video_type
import com.metrolist.shared.ui.sonder.sonderSubtitle
import com.metrolist.shared.model.AlbumItem
import com.metrolist.shared.model.ArtistItem
import com.metrolist.shared.model.ArtistTiny
import com.metrolist.shared.model.BrowseItem
import com.metrolist.shared.model.EpisodeItem
import com.metrolist.shared.model.PlaylistItem
import com.metrolist.shared.model.PodcastItem
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.YTItem
import com.metrolist.shared.model.displayThumbnailUrl
import com.metrolist.shared.model.isOfficialMusicVideo
import com.metrolist.shared.model.isPodcast
import com.metrolist.shared.model.isVideoSong
import com.metrolist.shared.model.previewAspectRatio
import com.metrolist.shared.model.previewThumbnailUrl
import com.metrolist.shared.model.thumbnailAspectRatio
import com.metrolist.shared.settings.ArtistNameAliases
import com.metrolist.shared.settings.displayName
import com.metrolist.shared.settings.displayTitle
import com.metrolist.shared.state.SongMetadataState
import com.metrolist.shared.ui.components.images.AsyncImage
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.menu.artworkMenuActions
import com.metrolist.shared.ui.menu.onSecondaryClick
import com.metrolist.shared.ui.navigation.AlbumArtworkSource
import com.metrolist.shared.ui.navigation.navigationPageSource
import com.metrolist.shared.ui.navigation.navigationPopupSource
import com.metrolist.shared.ui.navigation.rememberAlbumArtworkSource
import com.metrolist.shared.ui.theme.gridCellInset
import com.metrolist.shared.ui.theme.gridCellShape
import com.metrolist.shared.ui.theme.uiEnhanced
import com.metrolist.shared.ui.utils.artistArtworkShape
import com.metrolist.shared.ui.utils.carouselCaption
import com.metrolist.shared.utils.formatDuration
import com.metrolist.shared.utils.isDesktop
import com.metrolist.shared.utils.monthlyAudienceText
import com.metrolist.shared.utils.withoutPlaybackCountLabel
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun MetadataLink(
    text: String,
    id: String?,
    onClick: ((String) -> Unit)?,
    style: TextStyle = MaterialTheme.typography.labelSmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    linkColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (!enabled || id == null || onClick == null) {
        Text(text, modifier, color = color, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    val currentClick = rememberUpdatedState(onClick)
    val linkStyles =
        TextLinkStyles(
            focusedStyle = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
            hoveredStyle = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
        )
    val annotatedText =
        remember(text, id, enabled, linkStyles) {
            buildAnnotatedString {
                withLink(LinkAnnotation.Clickable(id, linkStyles) { currentClick.value(id) }) { append(text) }
            }
        }
    MetadataText(annotatedText, modifier.navigationPageSource(), style, color)
}

@Composable
fun ArtistSubtitle(
    artists: List<ArtistTiny>,
    onArtistClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelSmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    clickable: Boolean = true,
    linkColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (!clickable || onArtistClick == null) {
        val text = remember(artists, ArtistNameAliases.aliases) { artists.joinToString { it.displayName } }
        Text(text, modifier, color = color, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    val currentClick = rememberUpdatedState(onArtistClick)
    val linkStyles =
        TextLinkStyles(
            style = SpanStyle(color = color),
            focusedStyle = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
            hoveredStyle = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
        )
    val text =
        remember(artists, linkStyles, ArtistNameAliases.aliases) {
            buildAnnotatedString {
                artists.forEachIndexed { index, artist ->
                    val artistId = artist.id
                    if (artistId != null) {
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = artistId,
                                styles = linkStyles,
                                linkInteractionListener = {
                                    CollectionPreviews.record(artist)
                                    currentClick.value(artistId)
                                },
                            ),
                        ) {
                            append(artist.displayName)
                        }
                    } else {
                        append(artist.displayName)
                    }
                    if (index < artists.lastIndex) append(", ")
                }
            }
        }
    MetadataText(text, modifier, style, color)
}

@Composable
private fun MetadataText(
    text: AnnotatedString,
    modifier: Modifier,
    style: TextStyle,
    color: Color,
) {
    val configuration = LocalViewConfiguration.current
    val linkConfiguration =
        remember(configuration) {
            object : ViewConfiguration by configuration {
                override val minimumTouchTargetSize = DpSize.Zero
            }
        }
    CompositionLocalProvider(LocalViewConfiguration provides linkConfiguration) {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: SongItem,
    onClick: () -> Unit,
    onShowMenu: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    index: Int? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isDownloaded: Boolean = false,
    onArtistClick: ((String) -> Unit)? = null,
    onAlbumClick: ((String) -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    clickableMetadata: Boolean = true,
    showThumbnail: Boolean = false,
    showAlbum: Boolean = false,
    longClickEnabled: Boolean = true,
    enabled: Boolean = !song.isUnavailable,
) {
    Box(modifier) {
        com.metrolist.shared.ui.sonder.SonderMediaRow(
            item = SongMetadataState.apply(song), onClick = onClick, onMore = onShowMenu,
            onLongClick = if (longClickEnabled) onLongClick ?: onShowMenu else null,
            current = isCurrent, enabled = enabled, leading = leadingContent, trailing = trailingContent,
        )
    }
}

@Composable
fun ListItemTitle(
    text: String,
    isExplicit: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    fontWeight: FontWeight = FontWeight.Medium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f, fill = false),
            style = style,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isExplicit) ExplicitBadge()
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.squareArtworkSlot(
    size: Dp,
    aspectRatio: Float,
): Modifier = this.size(size).padding(vertical = maxOf(0.dp, size * (1f - 1f / aspectRatio) / 2))

@Composable
fun YTGridItem(
    item: YTItem,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onShowMenu: ((YTItem) -> Unit)? = null,
    onArtistClick: ((String) -> Unit)? = null,
    onAlbumClick: ((String) -> Unit)? = null,
    imageSize: Dp = currentGridThumbnailHeight(),
    imageShape: Shape? = null,
    artworkModifier: Modifier = Modifier,
    artworkAspectRatio: Float? = null,
    carouselItemDrawInfo: CarouselItemDrawInfo? = null,
    contentPadding: Dp = 0.dp,
    subtitleOverride: String? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    fillWidth: Boolean = false,
    squareArtwork: Boolean = false,
    largeText: Boolean = false,
    clickableMetadata: Boolean = true,
    enabled: Boolean = !item.isUnavailable,
    onAlbumPlay: (() -> Unit)? = null,
    menuActionLabel: String = stringResource(Res.string.more_options),
    showAlbumYear: Boolean = false,
    artwork: AlbumArtworkSource? = null,
    showDurationBadge: Boolean = false,
) {
    val shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(12.dp)
    Column(
        modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(imageSize))
            .clip(RoundedCornerShape(12.dp)).focusOutline(shape)
            .combinedClickable(enabled = enabled, onClick = { onClick?.invoke() }, onLongClick = onLongClick ?: onShowMenu?.let { show -> { show(item) } }),
    ) {
        AsyncImage(item.thumbnail, Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)))
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(subtitleOverride ?: item.sonderSubtitle(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YTListItem(
    item: YTItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onShowMenu: (() -> Unit)? = null,
    onArtistClick: ((String) -> Unit)? = null,
    onAlbumClick: ((String) -> Unit)? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = null,
    clickableMetadata: Boolean = true,
    enabled: Boolean = !item.isUnavailable,
    compactSongSubtitle: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    onAlbumPlay: (() -> Unit)? = null,
) {
    Box(modifier) {
        com.metrolist.shared.ui.sonder.SonderMediaRow(
            item = item, onClick = onClick, onMore = onShowMenu ?: onLongClick,
            onLongClick = onLongClick ?: onShowMenu,
            current = isCurrent, enabled = enabled, trailing = trailingContent,
        )
    }
}

@Composable
private fun SongSubtitle(
    song: SongItem,
    onArtistClick: ((String) -> Unit)?,
    clickable: Boolean,
    style: TextStyle,
    modifier: Modifier,
    showCount: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    showDuration: Boolean = true,
) {
    val fullSuffix =
        song.duration?.takeIf { showDuration }?.let { formatDuration(it * 1000) } ?: song.playCountText.takeIf { showCount }.orEmpty()
    SuffixedSubtitle(fullSuffix, fullSuffix.withoutPlaybackCountLabel(), style, color, modifier) {
        if (song.artists.isNotEmpty()) {
            ArtistSubtitle(song.artists, onArtistClick, style = style, clickable = clickable, color = color, linkColor = linkColor)
        } else {
            Text(
                stringResource(
                    when {
                        song.isOfficialMusicVideo -> Res.string.music_video_type
                        song.isVideoSong -> Res.string.video_type
                        else -> Res.string.song_type
                    },
                ),
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SuffixedSubtitle(
    fullSuffix: String,
    compactSuffix: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier,
    artistLine: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            artistLine()
            for (suffix in listOf(fullSuffix, compactSuffix)) {
                Text(
                    if (suffix.isEmpty()) "" else " • $suffix",
                    style = style,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val full = measurables[1].measure(loose.copy(maxWidth = Constraints.Infinity))
        // Lookahead and the final measure pass can select different suffixes, so both must be measured.
        val compact = measurables[2].measure(loose)
        val artistWidth = measurables[0].maxIntrinsicWidth(constraints.maxHeight)
        val suffix = if (artistWidth.toLong() + full.width <= constraints.maxWidth) full else compact
        val artist = measurables[0].measure(loose.copy(maxWidth = (constraints.maxWidth - suffix.width).coerceAtLeast(0)))
        val width = constraints.constrainWidth(artist.width + suffix.width)
        val height = constraints.constrainHeight(maxOf(artist.height, suffix.height))
        layout(width, height) {
            artist.placeRelative(0, (height - artist.height) / 2)
            suffix.placeRelative(artist.width, (height - suffix.height) / 2)
        }
    }
}

@Composable
fun GridCard(
    title: String,
    subtitle: String,
    thumbnail: String?,
    icon: ImageVector?,
    artistId: String? = null,
    artworkModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    subtitleModifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    cover: DrawableResource? = null,
    localArtwork: List<String>? = null,
    artworkOverlay: (@Composable BoxScope.() -> Unit)? = null,
    artworkAspectRatio: Float = 1f,
    isPlaylistArtwork: Boolean = false,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
) {
    val isArtist = artistId != null
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Artwork(
                thumbnail,
                icon,
                modifier = Modifier.fillMaxWidth().aspectRatio(artworkAspectRatio).then(artworkModifier),
                cover = cover,
                localArtwork = localArtwork,
                iconSize = 56.dp,
                cornerRadius = uiEnhanced(ThumbnailCornerRadius, 12.dp),
                shape = artistId?.let { artistArtworkShape(it) } ?: RoundedCornerShape(uiEnhanced(ThumbnailCornerRadius, 12.dp)),
                iconModifier = iconModifier,
                overlay = artworkOverlay,
                isPlaylistArtwork = isPlaylistArtwork,
                isCurrent = isCurrent,
                isPlaying = isPlaying,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
            modifier = titleModifier,
        )
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
                modifier = subtitleModifier,
            )
        }
        Spacer(Modifier.height(GridCaptionSafeSpace))
    }
}

@Composable
fun ListCard(
    title: String,
    subtitle: String,
    thumbnail: String?,
    icon: ImageVector?,
    artistId: String? = null,
    artworkModifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    subtitleModifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    cover: DrawableResource? = null,
    localArtwork: List<String>? = null,
    artworkOverlay: (@Composable BoxScope.() -> Unit)? = null,
    artworkAspectRatio: Float = 1f,
    isPlaylistArtwork: Boolean = false,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            thumbnail,
            icon,
            modifier = Modifier.squareArtworkSlot(48.dp, artworkAspectRatio).then(artworkModifier),
            iconSize = 24.dp,
            cover = cover,
            localArtwork = localArtwork,
            cornerRadius = ThumbnailCornerRadius,
            shape = artistId?.let { artistArtworkShape(it) } ?: RoundedCornerShape(ThumbnailCornerRadius),
            iconModifier = iconModifier,
            overlay = artworkOverlay,
            isPlaylistArtwork = isPlaylistArtwork,
            isCurrent = isCurrent,
            isPlaying = isPlaying,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 17.sp),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = titleModifier,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = subtitleModifier,
                )
            }
        }
    }
}

@Composable
fun Artwork(
    thumbnail: String?,
    icon: ImageVector?,
    modifier: Modifier,
    iconSize: Dp,
    cornerRadius: Dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
    iconModifier: Modifier = Modifier,
    cover: DrawableResource? = null,
    localArtwork: List<String>? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    isPlaylistArtwork: Boolean = false,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
) {
    val artworkShape = animatedCurrentSongArtworkShape(isCurrent, shape)
    val artworkRotation by rememberCurrentSongArtworkRotation(isCurrent, isPlaying)
    Box(modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = artworkRotation }
                    .clip(artworkShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = -artworkRotation },
                contentAlignment = Alignment.Center,
            ) {
                if (cover != null) {
                    Image(
                        painterResource(cover),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else if (localArtwork != null) {
                    LocalPlaylistArtwork(localArtwork, Modifier.fillMaxSize(), fallbackUrl = thumbnail)
                } else if (isPlaylistArtwork) {
                    PlaylistCoverImage(thumbnail, Modifier.fillMaxSize())
                } else if (thumbnail != null) {
                    AsyncImage(thumbnail, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize).then(iconModifier),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        overlay?.invoke(this)
    }
}

@Composable
fun carouselArtworkShape(item: YTItem): Shape =
    if (item is ArtistItem) artistArtworkShape(item.id) else RoundedCornerShape(CarouselItemCornerRadius)
