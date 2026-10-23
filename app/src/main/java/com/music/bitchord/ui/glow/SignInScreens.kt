package com.music.bitchord.ui.glow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.auth.WebSessionMode
import com.music.bitchord.auth.YtMusicLoginScreen
import com.music.bitchord.data.model.UiState

/**
 * First launch and "Sign in" from anywhere: the welcome card, then Google's
 * own sign-in page in a WebView, and the error card if it doesn't finish.
 */
@Composable
fun SignInFlow(onFinished: () -> Unit, closable: Boolean, mode: WebSessionMode = WebSessionMode.SIGN_IN) {
    val g = LocalGlow.current
    var stage by remember { mutableStateOf(if (mode == WebSessionMode.SWITCH_CHANNEL) 1 else 0) }
    var failure by remember { mutableStateOf<String?>(null) }
    BackHandler {
        when {
            stage == 1 && mode == WebSessionMode.SIGN_IN -> stage = 0
            closable || mode == WebSessionMode.SWITCH_CHANNEL -> onFinished()
            else -> Unit
        }
    }
    when (stage) {
        0 -> Welcome(onSignIn = { stage = 1 }, onSkip = onFinished, closable = closable)
        else -> WebSignIn(
            mode = mode,
            onClose = { if (mode == WebSessionMode.SIGN_IN) stage = 0 else onFinished() },
            onDone = onFinished,
            onFailed = { failure = it },
        )
    }
    failure?.let { reason ->
        SignInError(
            reason = reason,
            onRetry = {
                failure = null
                stage = 1
            },
            onSkip = {
                failure = null
                onFinished()
            },
        )
    }
}

@Composable
private fun Welcome(onSignIn: () -> Unit, onSkip: () -> Unit, closable: Boolean) {
    val g = LocalGlow.current
    val c = Glow.colors
    val home by g.vm.home.collectAsStateWithLifecycle()
    val covers = (home as? UiState.Success)?.data.orEmpty().flatMap { it.items }.mapNotNull { it.thumbnailUrl }.take(3)
    Box(
        Modifier
            .fillMaxSize()
            .background(c.base)
            .background(Brush.radialGradient(listOf(Color(0xFF2F5A55).copy(alpha = 0.55f), Color.Transparent), radius = 1100f))
            .clickable(enabled = false) {},
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        ) {
            if (closable) {
                IconTap(Icons.Rounded.Close, "Close", modifier = Modifier.offset(x = (-10).dp)) { onSkip() }
            }
            Spacer(Modifier.weight(0.6f))
            // The fanned stack of covers.
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.offset(x = (-72).dp, y = (-6).dp).rotate(-10f)) {
                    CoverTile(covers.getOrNull(1), Color(0xFF5B2A86))
                }
                Box(Modifier.offset(x = 72.dp, y = (-14).dp).rotate(9f)) {
                    CoverTile(covers.getOrNull(2), Color(0xFFD84A12))
                }
                Box(Modifier.offset(y = 30.dp)) { CoverTile(covers.getOrNull(0), Color(0xFF2F5A55)) }
            }
            Spacer(Modifier.weight(0.5f))
            Text("Your music,\neverywhere.", style = GlowType.hero.copy(fontSize = 36.sp, lineHeight = 40.sp), color = c.text)
            Spacer(Modifier.height(14.dp))
            Text(
                "Sign in to sync your library, playlists and liked songs. Or just start listening.",
                style = GlowType.bodyRegular.copy(fontSize = 17.sp, lineHeight = 24.sp),
                color = c.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            PillButton("Sign in with YouTube Music", Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayCircleOutline, onClick = onSignIn)
            Spacer(Modifier.height(10.dp))
            PillButton("Continue without an account", Modifier.fillMaxWidth(), primary = false, onClick = onSkip)
            Text(
                "You can sign in later from Settings.",
                style = GlowType.secondary,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun CoverTile(url: String?, fallback: Color) {
    Box(Modifier.size(170.dp).clip(RoundedCornerShape(16.dp)).background(fallback)) {
        if (url != null) Cover(url, size = 170.dp, shape = RoundedCornerShape(16.dp), px = 544)
    }
}

@Composable
private fun WebSignIn(mode: WebSessionMode, onClose: () -> Unit, onDone: () -> Unit, onFailed: (String) -> Unit) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val accounts by vm.googleAccounts.collectAsStateWithLifecycle()
    val activeAccountId by vm.activeAccountId.collectAsStateWithLifecycle()
    var captureRequest by remember { mutableIntStateOf(0) }
    var pageReady by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.base).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Icons.Rounded.Close, "Close", onClick = onClose)
            Column(Modifier.weight(1f)) {
                Text(
                    if (mode == WebSessionMode.SIGN_IN) "Sign in with YouTube Music" else "Choose a channel",
                    style = GlowType.titleSmall,
                    color = c.text,
                )
                if (pageReady) {
                    Text("Pick the channel to use, then confirm.", style = GlowType.secondary, color = c.textSecondary, maxLines = 2)
                }
            }
            if (pageReady) {
                PillButton(if (confirming) "Checking…" else "Use this", height = 40.dp, enabled = !confirming) {
                    confirming = true
                    captureRequest++
                }
            }
        }
        YtMusicLoginScreen(
            mode = mode,
            initialCookie = if (mode == WebSessionMode.SWITCH_CHANNEL) accounts.firstOrNull { it.accountId == activeAccountId }?.cookie else null,
            captureRequest = captureRequest,
            onPageReady = { pageReady = it },
            onCaptureUnavailable = {
                confirming = false
                onFailed("The page didn't have a signed-in session to use.")
            },
            onCaptured = { session ->
                vm.onWebSession(session, mode) { accepted ->
                    confirming = false
                    if (accepted) {
                        if (mode == WebSessionMode.SIGN_IN) g.selectTab(2)
                        onDone()
                    } else {
                        onFailed("YouTube Music didn't accept the session.")
                    }
                }
            },
        )
    }
}

@Composable
private fun SignInError(reason: String, onRetry: () -> Unit, onSkip: () -> Unit) {
    val c = Glow.colors
    var details by remember { mutableStateOf(false) }
    GlowDialog(onDismiss = onSkip) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(c.glassStrong), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.WarningAmber, null, tint = c.danger, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Couldn't sign in", style = GlowType.title.copy(fontSize = 22.sp), color = c.text)
        Spacer(Modifier.height(8.dp))
        Text(
            "The sign-in page closed before it finished, or your connection dropped. Nothing in your library was changed.",
            style = GlowType.bodyRegular,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PillButton("Try again", Modifier.fillMaxWidth(), onClick = onRetry)
        Spacer(Modifier.height(6.dp))
        Text(
            "Continue without an account",
            style = GlowType.body.copy(fontWeight = FontWeight.SemiBold),
            color = c.text,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSkip).padding(12.dp),
        )
        Text(
            if (details) reason else "Show error details",
            style = GlowType.secondary,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { details = !details }.padding(8.dp),
        )
    }
}

/** The one-time ask for notification access, with a preview of the controls. */
@Composable
fun NotificationsPrompt(onAllow: () -> Unit, onSkip: () -> Unit) {
    val g = LocalGlow.current
    val c = Glow.colors
    val song = g.player.song
    Box(Modifier.fillMaxSize().background(c.base).clickable(enabled = false) {}) {
        CoverGlow(song?.thumbnailUrl, height = 700.dp, intensity = 0.5f)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 22.dp, height = 6.dp).clip(CircleShape).background(c.text))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(6.dp).clip(CircleShape).background(c.textTertiary))
                Spacer(Modifier.weight(1f))
                TextAction("Skip", color = c.text, onClick = onSkip)
            }
            Spacer(Modifier.height(60.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.glass)
                    .border(1.dp, c.divider, RoundedCornerShape(22.dp))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Cover(song?.thumbnailUrl, size = 52.dp, shape = RoundedCornerShape(10.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(song?.title ?: "Your music", style = GlowType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp), color = c.text, maxLines = 1)
                        Text(song?.artist ?: "Playing", style = GlowType.secondary, color = c.textSecondary, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(14.dp))
                ThinProgress(0.38f)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SkipPrevious, null, tint = c.text, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(30.dp))
                    Box(Modifier.size(48.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Pause, null, tint = c.onPrimary)
                    }
                    Spacer(Modifier.width(30.dp))
                    Icon(Icons.Rounded.SkipNext, null, tint = c.text, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(40.dp))
            Text("Control music\nfrom anywhere", style = GlowType.hero.copy(fontSize = 32.sp, lineHeight = 36.sp), color = c.text)
            Spacer(Modifier.height(14.dp))
            Text(
                "Allow notifications so you can play, pause and skip from your lock screen and notification shade. We won't send ads or reminders.",
                style = GlowType.bodyRegular.copy(fontSize = 16.sp, lineHeight = 23.sp),
                color = c.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            PillButton("Allow notifications", Modifier.fillMaxWidth(), onClick = onAllow)
            Text(
                "Not now",
                style = GlowType.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                color = c.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onSkip).padding(18.dp),
            )
        }
    }
}
