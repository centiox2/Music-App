package com.music.bitchord.ui.glow

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.listentogether.JamInvite
import com.music.bitchord.data.listentogether.ListenTogether
import kotlinx.coroutines.launch

private val MEMBER_COLORS = listOf(0xFF9CC9BA, 0xFFF2A341, 0xFFF08CAE, 0xFF8FB3E0, 0xFFC7A6F2)

/**
 * Listen together, on a page of its own: the sheet where it used to live is
 * about output devices, and starting or joining a session meant scrolling past
 * the cast list to find it.
 */
@Composable
fun ListenTogetherScreen() {
    val g = LocalGlow.current
    val c = Glow.colors
    GlowScreen(glowUrl = g.player.song?.thumbnailUrl) {
        Column(Modifier.fillMaxSize()) {
            TopBar(onBack = { g.pop() }, title = "Listen together", centered = true)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(6.dp))
                Text("Listen together", style = GlowType.display.copy(fontSize = 34.sp), color = c.text)
                Text(
                    "Everyone hears the same song, at the same moment. Start a session and friends join with a code.",
                    style = GlowType.bodyRegular.copy(fontSize = 16.sp),
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(22.dp))
                ListenTogetherCard()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** The session: no party yet, or the live one with its code and who is listening. */
@Composable
fun ListenTogetherCard() {
    val g = LocalGlow.current
    val c = Glow.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val party by ListenTogether.state.collectAsStateWithLifecycle()
    val signedIn by g.vm.signedIn.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }

    GlassCard {
        Column(Modifier.padding(16.dp)) {
            val code = party.code
            if (code == null) {
                Text("Start a session", style = GlowType.title, color = c.text)
                Text(
                    "Everyone hears the same song, in sync. Friends join with a code.",
                    style = GlowType.bodyRegular,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                party.error?.let { Text(it, style = GlowType.secondary, color = c.danger, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(16.dp))
                if (!signedIn) {
                    Text("Sign in to start or join a session.", style = GlowType.secondary, color = c.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    PillButton("Sign in", Modifier.fillMaxWidth(), height = 48.dp) {
                        g.playerOpen = false
                        g.signInOpen = true
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton(if (busy) "Starting…" else "Start session", Modifier.weight(1f), height = 48.dp, enabled = !busy) {
                            busy = true
                            scope.launch {
                                ListenTogether.createParty().onFailure { g.notify(it.message ?: "Couldn't start a session") }
                                busy = false
                            }
                        }
                        PillButton("Join", Modifier.weight(1f), primary = false, height = 48.dp) {
                            g.playerOpen = false
                            g.push(Route.JoinSession)
                        }
                    }
                }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Session is live", style = GlowType.title, color = c.text)
                    Text("Everyone hears the same song, in sync", style = GlowType.bodyRegular, color = c.textSecondary)
                }
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF5FD9A6)))
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.25f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Join code", style = GlowType.secondary, color = c.textSecondary)
                    Text(
                        code.chunked(3).joinToString(" - ").uppercase(),
                        style = GlowType.title.copy(fontSize = 24.sp, letterSpacing = 4.sp),
                        color = c.text,
                    )
                }
                Text(
                    "Copy link",
                    style = GlowType.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.text,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.glassStrong).clickable {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Invite", JamInvite.url(code)))
                        g.notify("Invite link copied")
                    }.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            party.members.forEachIndexed { i, member ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    InitialAvatar(
                        member.displayName.ifBlank { "?" },
                        size = 40.dp,
                        imageUrl = member.avatarUrl,
                        background = Color(MEMBER_COLORS[i % MEMBER_COLORS.size]),
                        textColor = Color(0xFF111111),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        if (member.memberId == party.you?.memberId) "You" else member.displayName,
                        style = GlowType.body.copy(fontSize = 16.sp),
                        color = c.text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        when {
                            member.isHost -> "Host"
                            !member.connected -> "Away"
                            else -> "Listening"
                        },
                        style = GlowType.secondary,
                        color = c.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Invite friends", Modifier.weight(1f), height = 48.dp) { g.shareUrl("Listen with me", JamInvite.url(code)) }
                PillButton(if (party.you?.isHost == true) "End session" else "Leave", Modifier.weight(1f), primary = false, height = 48.dp) {
                    scope.launch { ListenTogether.leaveParty() }
                }
            }
        }
    }
}

/** The devices sheet's way in: one row naming the session, or offering to start one. */
@Composable
fun ListenTogetherEntry(onOpen: () -> Unit) {
    val c = Glow.colors
    val party by ListenTogether.state.collectAsStateWithLifecycle()
    GlassCard {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(c.glassStrong),
                contentAlignment = Alignment.Center,
            ) { androidx.compose.material3.Icon(Icons.Rounded.Groups, null, tint = c.text, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                GlowText("Listen together", GlowType.body.copy(fontSize = 16.sp), maxLines = 1)
                GlowText(
                    party.code?.let { "Session ${it.uppercase()} · live" } ?: "Everyone hears the same song, in sync",
                    GlowType.secondary,
                    color = c.textSecondary,
                    maxLines = 1,
                )
            }
            ChevronRight()
        }
    }
}
