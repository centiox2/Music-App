@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.action_like
import com.metrolist.shared.generated.resources.copied
import com.metrolist.shared.generated.resources.link_copied_to_clipboard
import com.metrolist.shared.generated.resources.more_options
import com.metrolist.shared.generated.resources.next
import com.metrolist.shared.generated.resources.previous
import com.metrolist.shared.generated.resources.share
import com.metrolist.shared.settings.PlayerBackgroundStyle
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.LocalIsTv
import com.metrolist.shared.ui.haptics.HapticEvent
import com.metrolist.shared.ui.haptics.rememberMetrolistHaptics
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.navigation.navigationPopupSource
import com.metrolist.shared.ui.tv.tvFocusHighlight
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

data class PlayerControlAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val checked: Boolean? = null,
    val enabled: Boolean = true,
    val text: String? = null,
    val wide: Boolean = false,
    val trailingText: String? = null,
)

@Composable
internal fun playerInactiveControlContainerColor(): Color {
    val darkTheme = Settings.shared.getCurrentThemeIsDark()
    return MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (darkTheme) 0.5f else 1f)
}

@Composable
fun Modifier.playerControlBlend(): Modifier {
    val playerBackground = Settings.shared.playerBackgroundStyle
    val darkTheme = Settings.shared.getCurrentThemeIsDark()
    return graphicsLayer {
        blendMode = if (playerBackground != PlayerBackgroundStyle.SOLID && darkTheme) BlendMode.Screen else BlendMode.SrcOver
        compositingStrategy = CompositingStrategy.Offscreen
    }
}

@Composable
fun PlayerPrimaryControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    playText: String,
    pauseText: String,
    onSkipPrevious: (() -> Unit)?,
    onTogglePlay: () -> Unit,
    onSkipNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
    buttonHeight: Dp = 96.dp,
    sideButtonHeight: Dp? = null,
    showLabels: Boolean = true,
    sideButtonContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    sideButtonContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    centerButtonContainerColor: Color = MaterialTheme.colorScheme.primary,
    centerButtonContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    useRoundedRect: Boolean = false,
    playPauseFocusRequester: FocusRequester? = null,
    previousText: String = stringResource(Res.string.previous),
    nextText: String = stringResource(Res.string.next),
    showLoadingIndicator: Boolean = true,
) {
    val haptics = rememberMetrolistHaptics()
    val playSize = buttonHeight.coerceIn(48.dp, 88.dp)
    val playShape = if (useRoundedRect) RoundedCornerShape(16.dp) else CircleShape
    val sideSize = (sideButtonHeight ?: 56.dp).coerceIn(44.dp, 64.dp)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(
            onClick = { onSkipPrevious?.invoke() },
            enabled = onSkipPrevious != null,
            modifier = Modifier.size(sideSize).tvFocusHighlight(CircleShape, scale = 1f),
        ) {
            Icon(
                MetrolistIcons.Rounded.SkipPrevious,
                previousText,
                tint = sideButtonContentColor.copy(alpha = if (onSkipPrevious != null) 1f else 0.38f),
                modifier = Modifier.size(36.dp),
            )
        }
        Surface(
            onClick = {
                haptics.toggle(!isPlaying)
                onTogglePlay()
            },
            shape = playShape,
            color = centerButtonContainerColor,
            contentColor = centerButtonContentColor,
            modifier = Modifier.size(playSize)
                .tvFocusHighlight(playShape, scale = 1f, color = centerButtonContentColor)
                .then(if (playPauseFocusRequester != null) Modifier.focusRequester(playPauseFocusRequester) else Modifier),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isLoading && showLoadingIndicator) {
                    CircularProgressIndicator(
                        color = centerButtonContentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp).semantics { contentDescription = if (isPlaying) pauseText else playText },
                    )
                } else {
                    Icon(
                        if (isPlaying) MetrolistIcons.Rounded.Pause else MetrolistIcons.Rounded.PlayArrow,
                        if (isPlaying) pauseText else playText,
                        modifier = Modifier.size(playSize * 0.48f),
                    )
                }
            }
        }
        IconButton(
            onClick = { onSkipNext?.invoke() },
            enabled = onSkipNext != null,
            modifier = Modifier.size(sideSize).tvFocusHighlight(CircleShape, scale = 1f),
        ) {
            Icon(
                MetrolistIcons.Rounded.SkipNext,
                nextText,
                tint = sideButtonContentColor.copy(alpha = if (onSkipNext != null) 1f else 0.38f),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
fun PlayerLikeShareButtons(
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
    groupHeight: Dp = 36.dp,
    buttonWidth: Dp = groupHeight,
    iconSize: Dp = 18.dp,
    onMoreClick: (() -> Unit)? = null,
    isMoreActive: Boolean = false,
    onAltShareClick: (() -> Unit)? = null,
) {
    val isTv = LocalIsTv.current
    val darkTheme = Settings.shared.getCurrentThemeIsDark()

    var isCopied by remember { mutableStateOf(false) }
    var copyJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var isShiftPressed by remember { mutableStateOf(false) }

    val shareInteractionSource = remember { MutableInteractionSource() }
    val likeInteractionSource = remember { MutableInteractionSource() }
    val hapticFeedback = rememberMetrolistHaptics()

    val linkCopiedDesc = stringResource(Res.string.link_copied_to_clipboard)
    val shareDesc = stringResource(Res.string.share)
    val likeDesc = stringResource(Res.string.action_like)
    val moreDesc = stringResource(Res.string.more_options)

    val triggerCopied = {
        isCopied = true
        copyJob?.cancel()
        copyJob =
            coroutineScope.launch {
                delay(800.milliseconds)
                isCopied = false
            }
    }

    val handleAltShareClick = {
        hapticFeedback.perform(HapticEvent.LONG_PRESS)
        if (onAltShareClick != null) {
            onAltShareClick()
        } else {
            onShareClick()
        }
        triggerCopied()
    }

    val handleShareClick = {
        if (isShiftPressed && onAltShareClick != null) {
            handleAltShareClick()
        } else {
            onShareClick()
        }
    }

    val buttonColors =
        ToggleButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (darkTheme) 0.5f else 1f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    val likeButtonColors =
        ToggleButtonDefaults.colors(
            containerColor = playerInactiveControlContainerColor(),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        )

    Row(
        modifier = modifier.height(groupHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ButtonGroup(
            overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
            expandedRatio = 1f,
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .height(groupHeight)
                    .playerControlBlend(),
        ) {
            if (!isTv) {
                customItem(
                    buttonGroupContent = {
                        val shareShape = ButtonGroupDefaults.connectedLeadingButtonShape
                        Surface(
                            shape = shareShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier =
                                Modifier
                                    .size(width = buttonWidth, height = groupHeight)
                                    .animateWidth(shareInteractionSource, 12.dp)
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                isShiftPressed = event.keyboardModifiers.isShiftPressed
                                            }
                                        }
                                    }.clip(shareShape)
                                    .combinedClickable(
                                        hapticFeedbackEnabled = false,
                                        interactionSource = shareInteractionSource,
                                        indication = ripple(),
                                        onClick = { handleShareClick() },
                                        onLongClick = onAltShareClick?.let { { handleAltShareClick() } },
                                    ).tvFocusHighlight(shareShape, scale = 1f)
                                    .semantics {
                                        contentDescription = if (isCopied) linkCopiedDesc else shareDesc
                                        role = Role.Button
                                        if (onAltShareClick != null) {
                                            onLongClick {
                                                handleAltShareClick()
                                                true
                                            }
                                        }
                                    },
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    if (isCopied) MetrolistIcons.Default.Check else MetrolistIcons.Outlined.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(iconSize),
                                )
                            }
                        }
                    },
                    menuContent = { menuState ->
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    if (isCopied) MetrolistIcons.Default.Check else MetrolistIcons.Outlined.Share,
                                    contentDescription = null,
                                )
                            },
                            text = { Text(if (isCopied) stringResource(Res.string.copied) else stringResource(Res.string.share)) },
                            onClick = {
                                if (isShiftPressed && onAltShareClick != null) {
                                    handleAltShareClick()
                                } else {
                                    handleShareClick()
                                }
                                menuState.dismiss()
                            },
                        )
                    },
                )
            }
            customItem(
                buttonGroupContent = {
                    ToggleButton(
                        checked = isLiked,
                        onCheckedChange = { onLikeClick() },
                        shapes =
                            if (isTv) {
                                ToggleButtonDefaults.shapesFor(groupHeight)
                            } else {
                                ButtonGroupDefaults.connectedTrailingButtonShapes(
                                    checkedShape = ButtonGroupDefaults.connectedTrailingButtonShape,
                                )
                            },
                        contentPadding = PaddingValues(0.dp),
                        colors = likeButtonColors,
                        interactionSource = likeInteractionSource,
                        modifier =
                            Modifier
                                .size(width = buttonWidth, height = groupHeight)
                                .animateWidth(likeInteractionSource, 12.dp)
                                .tvFocusHighlight(
                                    if (isTv) RoundedCornerShape(50) else ButtonGroupDefaults.connectedTrailingButtonShape,
                                    scale = 1f,
                                    color = if (isLiked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                ).semantics { contentDescription = likeDesc },
                    ) {
                        Icon(
                            if (isLiked) MetrolistIcons.Default.Favorite else MetrolistIcons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                        )
                    }
                },
                menuContent = { menuState ->
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                if (isLiked) MetrolistIcons.Default.Favorite else MetrolistIcons.Outlined.FavoriteBorder,
                                contentDescription = null,
                            )
                        },
                        text = { Text(stringResource(Res.string.action_like)) },
                        onClick = {
                            onLikeClick()
                            menuState.dismiss()
                        },
                    )
                },
            )
        }

        if (onMoreClick != null) {
            Spacer(Modifier.width(8.dp))
            ToggleButton(
                checked = isMoreActive,
                onCheckedChange = { onMoreClick() },
                shapes =
                    ToggleButtonDefaults.shapesFor(groupHeight),
                contentPadding = PaddingValues(0.dp),
                colors = buttonColors,
                modifier =
                    Modifier
                        .size(buttonWidth, groupHeight)
                        .semantics { contentDescription = moreDesc }
                        .navigationPopupSource(),
            ) {
                Icon(
                    MetrolistIcons.Default.MoreVert,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}
