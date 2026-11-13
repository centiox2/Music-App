package com.music.bitchord.ui.glow

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/*
 * Remote control (D-pad) support, for Android TV and anything else driven by arrow keys.
 *
 * Compose already moves focus between clickable things with the arrow keys and clicks the focused
 * one with OK; what it does not do is show where focus is. Every clickable in the app is given
 * [FocusRing] (directly, or through the ripple, [glowRipple], which carries it): a white ring with
 * a dark edge, so it reads on light and dark backgrounds alike. Focus is only ever taken from a
 * remote or keyboard; touch never shows the ring.
 */

/** The focus ring on its own, for clickables that have no ripple. */
val FocusRing: IndicationNodeFactory = FocusRingIndication(null)

/** The ripple every pressable uses, with the focus ring on top. */
fun glowRipple(bounded: Boolean = true): IndicationNodeFactory = FocusRingIndication(ripple(bounded = bounded))

private class FocusRingIndication(private val inner: IndicationNodeFactory?) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = FocusRingNode(interactionSource, inner)
    override fun equals(other: Any?) = other is FocusRingIndication && other.inner == inner
    override fun hashCode() = inner.hashCode()
}

private class FocusRingNode(
    private val source: InteractionSource,
    inner: IndicationNodeFactory?,
) : DelegatingNode(), DrawModifierNode {
    init {
        inner?.let { delegate(it.create(source)) }
    }

    private var focused = false

    override fun onAttach() {
        coroutineScope.launch {
            var count = 0
            source.interactions.collect { i ->
                when (i) {
                    is FocusInteraction.Focus -> count++
                    is FocusInteraction.Unfocus -> count = (count - 1).coerceAtLeast(0)
                    else -> return@collect
                }
                val now = count > 0
                if (now != focused) {
                    focused = now
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!focused) return
        val w = 3.dp.toPx()
        val small = minOf(size.width, size.height)
        // Round things (icon buttons) get a round ring; the rest a rounded rectangle no tighter
        // than the corners they are clipped to, so the ring isn't cut off at the corners.
        val radius = if (size.width <= 80.dp.toPx() && size.height <= 80.dp.toPx()) small / 2 else minOf(16.dp.toPx(), small / 2)
        drawRoundRect(Color.White.copy(alpha = 0.14f), cornerRadius = CornerRadius(radius))
        val edge = w + 2.dp.toPx()
        drawRoundRect(
            Color.Black.copy(alpha = 0.55f),
            topLeft = Offset(edge / 2, edge / 2),
            size = Size(size.width - edge, size.height - edge),
            cornerRadius = CornerRadius((radius - edge / 2).coerceAtLeast(0f)),
            style = Stroke(edge),
        )
        drawRoundRect(
            Color.White,
            topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius((radius - w / 2).coerceAtLeast(0f)),
            style = Stroke(w),
        )
    }
}

/**
 * A slider or scrubber reached with the remote: focusable, shows the ring, and left/right (or
 * up/down, for a vertical one) step it. Touch input is untouched.
 */
@Composable
fun Modifier.dpadAdjust(
    enabled: Boolean = true,
    vertical: Boolean = false,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .onKeyEvent { e ->
            if (!enabled || e.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (e.key) {
                (if (vertical) Key.DirectionDown else Key.DirectionLeft) -> { onDecrease(); true }
                (if (vertical) Key.DirectionUp else Key.DirectionRight) -> { onIncrease(); true }
                else -> false
            }
        }
        .indication(source, FocusRing)
        .focusable(enabled, source)
}

/**
 * The remote's Menu key (☰ / ⋮ on most TV remotes) does [action] on the focused item: the song
 * menu on a song, which otherwise needs a long press of OK, awkward on a remote. Put it before
 * the item's clickable so the key reaches it from the focused item.
 */
fun Modifier.menuKey(action: (() -> Unit)?): Modifier =
    if (action == null) this else onKeyEvent { e ->
        val menu = e.key == Key.Menu || e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU
        if (!menu) return@onKeyEvent false
        if (e.type == KeyEventType.KeyUp) action()
        true
    }

/**
 * Keeps the remote out of what is underneath an overlay (the player, the devices sheet, the
 * sign-in flow): Compose looks for the next thing to focus across the whole screen, covered or
 * not, so without this the arrows wandered into the page behind the player.
 */
fun Modifier.focusBlocked(blocked: Boolean): Modifier =
    if (!blocked) this else focusProperties { onEnter = { cancelFocusChange() } }.focusGroup()

/**
 * An overlay (the player, the devices sheet): the arrows move around inside it and never leave it
 * for the page underneath. [requester] is how focus is put into it when it opens.
 */
fun Modifier.focusTrap(requester: FocusRequester): Modifier =
    focusRequester(requester).focusProperties { onExit = { cancelFocusChange() } }.focusGroup()

/** A region focus can be sent into as a whole (its first focusable child takes it). */
fun Modifier.focusArea(requester: FocusRequester): Modifier = focusRequester(requester).focusGroup()

/** Moves focus into [requester]'s region, when the app is being driven by a remote or keyboard. */
fun FocusRequester.enterIfRemote(mode: InputMode): Boolean =
    mode == InputMode.Keyboard && runCatching { requestFocus(FocusDirection.Enter) }.getOrDefault(false)

/**
 * How far lists scroll to show what the remote just moved to. Compose's own rule scrolls only
 * until the item is inside the list, which on most pages is under the mini player and tab bar
 * that float over the bottom of every list; this keeps the focused item off the edges (the
 * trailing third stays clear), the way TV apps scroll. Only used while a remote is driving.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal object RemoteBringIntoViewSpec : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val start = containerSize * 0.12f
        val end = containerSize * 0.70f
        return when {
            size > end - start -> offset - start
            offset < start -> offset - start
            offset + size > end -> offset + size - end
            else -> 0f
        }
    }
}

/** Gives every list [RemoteBringIntoViewSpec] while the app is driven by a remote or keyboard. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RemoteScrolling(content: @Composable () -> Unit) {
    val mode = androidx.compose.ui.platform.LocalInputModeManager.current.inputMode
    if (mode == InputMode.Keyboard) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides RemoteBringIntoViewSpec,
            content = content,
        )
    } else {
        content()
    }
}

/**
 * Text boxes with a remote. Compose opens the keyboard as soon as a text field takes focus,
 * so arrowing past a search box (Settings, Search) popped the keyboard up over the page. With
 * a remote, the box itself is what focus lands on (it shows the ring); OK opens it for typing
 * and brings the keyboard up; leaving it puts it back. Touch is unchanged.
 */
class RemoteTextGate internal constructor(
    /** Goes on the box around the field: focusable and clickable with a remote. */
    val container: Modifier,
    /** Goes on the text field itself. */
    val field: Modifier,
)

@Composable
fun rememberRemoteTextGate(): RemoteTextGate {
    val remote = androidx.compose.ui.platform.LocalInputModeManager.current.inputMode == InputMode.Keyboard
    var armed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val source = remember { MutableInteractionSource() }
    var hadFocus by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(armed) {
        if (armed) {
            // The field becomes focusable this frame; ask once it is.
            kotlinx.coroutines.yield()
            runCatching { requester.requestFocus() }
        }
    }
    val container = if (remote && !armed) {
        Modifier.clickable(interactionSource = source, indication = FocusRing) { armed = true }
    } else {
        Modifier
    }
    val field = Modifier
        .focusRequester(requester)
        .focusProperties { canFocus = !remote || armed }
        .onFocusChanged {
            if (it.isFocused) {
                hadFocus = true
            } else if (hadFocus) {
                hadFocus = false
                armed = false
            }
        }
    return RemoteTextGate(container, field)
}

