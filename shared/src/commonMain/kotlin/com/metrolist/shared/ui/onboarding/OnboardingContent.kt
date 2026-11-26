@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.app_name
import com.metrolist.shared.generated.resources.onboarding_notification_downloads
import com.metrolist.shared.generated.resources.onboarding_notification_playback
import com.metrolist.shared.generated.resources.onboarding_privacy_open_failed
import com.metrolist.shared.generated.resources.onboarding_privacy_policy
import com.metrolist.shared.generated.resources.onboarding_sentry_diagnostics
import com.metrolist.shared.generated.resources.onboarding_sentry_opt_in
import com.metrolist.shared.generated.resources.onboarding_sentry_private
import com.metrolist.shared.generated.resources.onboarding_step_of
import com.metrolist.shared.generated.resources.pause
import com.metrolist.shared.generated.resources.play
import com.metrolist.shared.ui.components.GroupedListItem
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.navigation.PredictiveNavigationContent
import com.metrolist.shared.ui.utils.verticalScrollWithDrag
import org.jetbrains.compose.resources.stringResource

enum class OnboardingPage {
    WELCOME,
    MIGRATION,
    MIGRATION_COMPLETE,
    CRASH_REPORTS,
    NOTIFICATIONS,
    LANGUAGE_LYRICS,
    PLAYBACK,
    FINISH,
}

data class OnboardingPageContent(
    val page: OnboardingPage,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val step: Int,
    val stepCount: Int,
    val primaryLabel: String,
    val secondaryLabel: String?,
    val isWorking: Boolean,
    val error: String?,
    val backLabel: String,
    val onPrimary: () -> Unit,
    val onSecondary: (() -> Unit)?,
    val onBack: (() -> Unit)?,
)

@Composable
fun OnboardingTransition(
    content: OnboardingPageContent,
    previousContent: OnboardingPageContent?,
    body: @Composable (OnboardingPageContent) -> Unit,
) {
    val history = remember { mutableMapOf<OnboardingPage, OnboardingPageContent>() }
    SideEffect {
        history[content.page] = content
        previousContent?.let { history[it.page] = it }
    }
    PredictiveNavigationContent(
        targetState = content.page,
        previousState = previousContent?.page ?: content.page,
        backEnabled = previousContent != null && content.onBack != null && !content.isWorking,
        onBack = { checkNotNull(content.onBack).invoke() },
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        body(
            when (page) {
                content.page -> content
                previousContent?.page -> checkNotNull(previousContent)
                else -> history.getValue(page)
            },
        )
    }
}

@Composable
fun OnboardingContent(
    content: OnboardingPageContent,
    welcomeContent: @Composable (Modifier) -> Unit,
    featureContent: @Composable (OnboardingPage) -> Unit,
    modifier: Modifier = Modifier,
    preferredActionModifier: Modifier = Modifier,
    previousContent: OnboardingPageContent? = null,
) {
    var paused by rememberSaveable { mutableStateOf(false) }
    OnboardingTransition(content, previousContent) { page ->
        OnboardingPageLayout(page, welcomeContent, featureContent, modifier, preferredActionModifier, paused) { paused = it }
    }
}

@Composable
private fun OnboardingPageLayout(
    content: OnboardingPageContent,
    welcomeContent: @Composable (Modifier) -> Unit,
    featureContent: @Composable (OnboardingPage) -> Unit,
    modifier: Modifier = Modifier,
    preferredActionModifier: Modifier,
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit,
) {
    Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        content.onBack?.let { back -> com.metrolist.shared.ui.sonder.SonderIcon(MetrolistIcons.AutoMirrored.Filled.ArrowBack, content.backLabel, back, glass = true) }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(72.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)), contentAlignment = Alignment.Center) {
            Icon(content.icon, null, Modifier.size(34.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(content.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(content.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (content.page != OnboardingPage.WELCOME) featureContent(content.page)
        content.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.weight(1f))
        Button(onClick = content.onPrimary, enabled = !content.isWorking, modifier = preferredActionModifier.fillMaxWidth().height(52.dp), shape = androidx.compose.foundation.shape.CircleShape) { Text(content.primaryLabel) }
        if (content.secondaryLabel != null && content.onSecondary != null) TextButton(onClick = content.onSecondary, enabled = !content.isWorking, modifier = Modifier.fillMaxWidth()) { Text(content.secondaryLabel) }
    }
}

@Composable
private fun PrivacyLink() {
    val uriHandler = LocalUriHandler.current
    val privacyUrl = "https://metrolist.cc/privacy"
    var openFailed by remember { mutableStateOf(false) }
    Column {
        TextButton(
            onClick = { openFailed = runCatching { uriHandler.openUri(privacyUrl) }.isFailure },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(Res.string.onboarding_privacy_policy))
        }
        if (openFailed) {
            Text(
                stringResource(Res.string.onboarding_privacy_open_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            SelectionContainer { Text(privacyUrl) }
        }
    }
}

@Composable
private fun OnboardingBottomActions(
    content: OnboardingPageContent,
    preferredActionModifier: Modifier,
    modifier: Modifier,
) {
    val secondaryWeight by animateFloatAsState(
        targetValue = if (content.secondaryLabel == null) 0f else 1f,
        animationSpec = tween(320),
        label = "Onboarding primary button width",
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        content.onBack?.let { back ->
            IconButton(onClick = back, enabled = !content.isWorking, shapes = IconButtonDefaults.shapes()) {
                Icon(MetrolistIcons.AutoMirrored.Filled.ArrowBack, contentDescription = content.backLabel)
            }
        }
        if (content.page != OnboardingPage.WELCOME) {
            content.secondaryLabel?.let { secondary ->
                TextButton(
                    onClick = content.onSecondary ?: {},
                    enabled = !content.isWorking,
                    modifier = preferredActionModifier.weight(secondaryWeight.coerceAtLeast(0.001f)).heightIn(min = 56.dp),
                    shapes = ButtonDefaults.shapes(),
                ) { Text(secondary) }
            } ?: run {
                if (secondaryWeight > 0f) Spacer(Modifier.weight(secondaryWeight))
            }
        }
        Button(
            onClick = content.onPrimary,
            enabled = !content.isWorking,
            modifier =
                (if (content.secondaryLabel == null) preferredActionModifier else Modifier)
                    .weight(if (content.page == OnboardingPage.WELCOME) 1f else 3f - secondaryWeight)
                    .heightIn(min = if (content.page == OnboardingPage.WELCOME) 64.dp else 56.dp),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(content.primaryLabel)
            if (content.page == OnboardingPage.WELCOME) {
                Spacer(Modifier.width(8.dp))
                Icon(MetrolistIcons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WelcomeBackdrop(
    paused: Boolean,
    onFinished: () -> Unit,
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val motionEnabled = !paused && lifecycleState == Lifecycle.State.RESUMED
    val motion = remember { Animatable(0f) }
    val morph = remember { Morph(MaterialShapes.Cookie7Sided, MaterialShapes.Cookie9Sided) }
    val path = remember { Path() }
    val outline = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    LaunchedEffect(motionEnabled, paused) {
        if (motionEnabled) {
            motion.animateTo(0f, tween((9000 * motion.value).toInt(), easing = FastOutSlowInEasing))
            motion.animateTo(1f, repeatable(2, tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse))
            onFinished()
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val progress = motion.value
        val diameter = minOf(size.width * 1.15f, size.height * 0.9f)
        rotate(-24f + progress * 8f, pivot = Offset(size.width * 0.2f, size.height * 0.85f)) {
            drawOval(
                outline,
                topLeft = Offset(-size.width * 0.7f, size.height * (0.43f + progress * 0.04f)),
                size = Size(size.width * 2.3f, size.height * 1.3f),
                style = Stroke(1.5.dp.toPx()),
            )
        }
        morph.toPath(progress, path)
        withTransform({
            translate(size.width * 0.8f - diameter / 2, -diameter * (0.48f - progress * 0.12f))
            scale(diameter, diameter, pivot = Offset.Zero)
            rotate(progress * 45f, pivot = Offset(0.5f, 0.5f))
        }) {
            drawPath(path, surface)
            drawPath(path, accent.copy(alpha = 0.06f))
            drawPath(path, outline, style = Stroke(1.5.dp.toPx() / diameter))
        }
    }
}
