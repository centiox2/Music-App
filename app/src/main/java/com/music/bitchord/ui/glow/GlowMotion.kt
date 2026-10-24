package com.music.bitchord.ui.glow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The app's small motions, in one place so they all feel alike and all stand
 * down together when Reduce motion is on.
 */
object GlowMotion {
    /** Presses and toggles: quick, with a little give. */
    val press = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    /** Things settling into place. */
    val settle = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
}

@Composable
fun rememberReduceMotion(): Boolean {
    val reduce by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    return reduce
}

/**
 * Shrinks a little while pressed and springs back on release: the touch
 * response every tappable card, button and cover in the app shares.
 */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.94f): Modifier = composed {
    val reduce = rememberReduceMotion()
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed && !reduce) pressed else 1f, GlowMotion.press, label = "pressScale")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Fades and lifts an item in the first time it appears, a beat after the one
 * before it, so a screen of shelves or rows arrives as a cascade instead of
 * all at once. Items scrolled into view later just appear.
 */
fun Modifier.staggeredEntrance(index: Int, key: Any? = Unit): Modifier = composed {
    val reduce = rememberReduceMotion()
    var shown by rememberSaveable(key) { mutableBooleanSaver(reduce || index > 12) }
    val progress = remember(key) { Animatable(if (shown) 1f else 0f) }
    val lift = with(LocalDensity.current) { 18.dp.toPx() }
    LaunchedEffect(key) {
        if (!shown) {
            delay((index * 45L).coerceAtMost(400L))
            progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            shown = true
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * lift
    }
}

private fun mutableBooleanSaver(value: Boolean) = androidx.compose.runtime.mutableStateOf(value)

/**
 * A light tick for play, skip and like — off when the user turned haptics off.
 */
@Composable
fun rememberHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    val enabled by GlowPrefs.haptics.collectAsStateWithLifecycle()
    return remember(haptics, enabled) {
        { if (enabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
}

/**
 * A pop: grows past full size and settles, for a heart that's just been
 * filled or a button that's just changed state. Runs each time [trigger] changes.
 */
fun Modifier.popOnChange(trigger: Any?, enabled: Boolean = true): Modifier = composed {
    val reduce = rememberReduceMotion()
    val scale = remember { Animatable(1f) }
    var first by remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(trigger) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (reduce || !enabled) return@LaunchedEffect
        launch {
            scale.snapTo(0.8f)
            scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        }
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
