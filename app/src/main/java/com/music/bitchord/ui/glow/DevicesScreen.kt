package com.music.bitchord.ui.glow

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.listentogether.JamInvite
import com.music.bitchord.data.listentogether.JamInviteLink
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.PartyPreview
import com.music.bitchord.playback.AudioRouting
import com.music.bitchord.playback.cast.CastController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MEMBER_COLORS = listOf(0xFF9CC9BA, 0xFFF2A341, 0xFFF08CAE, 0xFF8FB3E0, 0xFFC7A6F2)

@Composable
fun DevicesScreen() {
    val g = LocalGlow.current
    val c = Glow.colors
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val cast by CastController.state.collectAsStateWithLifecycle()
    val castVolume by CastController.volume.collectAsStateWithLifecycle()
    val selected by AudioRouting.selectedId.collectAsStateWithLifecycle()
    var outputs by remember { mutableStateOf(AudioRouting.outputs(audio)) }
    LaunchedEffect(selected, cast.connectedName) {
        while (true) {
            outputs = AudioRouting.outputs(audio)
            delay(2000)
        }
    }
    DisposableEffect(Unit) {
        CastController.ensureStarted(context)
        val stop = CastController.discover(true)
        onDispose { stop() }
    }
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max) }

    Box(Modifier.fillMaxSize().background(c.base)) {
        CoverGlow(g.player.song?.thumbnailUrl, height = 800.dp, intensity = 0.45f)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            TopBar(onBack = { g.devicesOpen = false }, title = "Devices & listening", centered = true, backIcon = Icons.Rounded.KeyboardArrowDown)
            GroupLabel("Playing on")
            GlassCard(Modifier.padding(horizontal = 20.dp)) {
                outputs.forEachIndexed { i, device ->
                    val active = !cast.casting && device.isActive
                    DeviceRow(
                        name = if (device.kind == AudioRouting.Kind.PHONE) "This phone" else device.name,
                        detail = when (device.kind) {
                            AudioRouting.Kind.PHONE -> "Speaker"
                            AudioRouting.Kind.BLUETOOTH -> "Bluetooth"
                            AudioRouting.Kind.WIRED -> "Wired"
                            AudioRouting.Kind.USB -> "USB audio"
                            AudioRouting.Kind.HDMI -> "HDMI"
                            AudioRouting.Kind.OTHER -> "Output"
                        },
                        icon = when (device.kind) {
                            AudioRouting.Kind.PHONE -> Icons.Rounded.PhoneAndroid
                            AudioRouting.Kind.BLUETOOTH, AudioRouting.Kind.WIRED -> Icons.Rounded.Headphones
                            AudioRouting.Kind.USB -> Icons.Rounded.Usb
                            else -> Icons.Rounded.Speaker
                        },
                        active = active,
                    ) {
                        if (cast.casting) CastController.disconnect(resumeHere = true)
                        AudioRouting.select(if (device.kind == AudioRouting.Kind.PHONE) null else device.id)
                    }
                    if (i < outputs.lastIndex || cast.devices.isNotEmpty()) CardDivider()
                }
                cast.devices.forEachIndexed { i, device ->
                    DeviceRow(
                        name = device.name,
                        detail = when {
                            device.connected -> "Casting"
                            device.connecting -> "Connecting…"
                            else -> "Cast"
                        },
                        icon = Icons.Rounded.Cast,
                        active = device.connected,
                    ) {
                        if (device.connected) CastController.disconnect(resumeHere = true) else CastController.connect(device.id)
                    }
                    if (i < cast.devices.lastIndex) CardDivider()
                }
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.VolumeDown, null, tint = c.text, modifier = Modifier.size(22.dp))
                Slider(
                    value = if (cast.casting) castVolume else volume,
                    onValueChange = {
                        if (cast.casting) {
                            CastController.setVolume(it)
                        } else {
                            volume = it
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).toInt(), 0)
                        }
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    colors = SliderDefaults.colors(thumbColor = c.text, activeTrackColor = c.text, inactiveTrackColor = c.glassStrong),
                )
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = c.text, modifier = Modifier.size(22.dp))
            }
            GroupLabel("Listen together")
            ListenTogetherCard()
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun DeviceRow(
    name: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
) {
    val c = Glow.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (active) c.primary else c.glassStrong),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (active) c.onPrimary else c.text, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            GlowText(name, GlowType.body.copy(fontSize = 16.sp), maxLines = 1)
            GlowText(detail, GlowType.secondary, color = c.textSecondary)
        }
        if (active) Icon(Icons.Rounded.Check, "Playing here", tint = c.text, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ListenTogetherCard() {
    val g = LocalGlow.current
    val c = Glow.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val party by ListenTogether.state.collectAsStateWithLifecycle()
    val signedIn by g.vm.signedIn.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }

    GlassCard(Modifier.padding(horizontal = 20.dp)) {
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
                    PillButton("Sign in", Modifier.fillMaxWidth(), height = 48.dp) { g.devicesOpen = false; g.signInOpen = true }
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
                            g.devicesOpen = false
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

// ---- Join a session ----

@Composable
fun JoinSessionScreen() {
    val g = LocalGlow.current
    val c = Glow.colors
    val scope = rememberCoroutineScope()
    val invite by JamInviteLink.pending.collectAsStateWithLifecycle()
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var preview by remember { mutableStateOf<PartyPreview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var joining by remember { mutableStateOf(false) }
    LaunchedEffect(invite) {
        invite?.let {
            val code = it.code.filter(Char::isLetterOrDigit).uppercase().take(6)
            field = TextFieldValue(code, TextRange(code.length))
            JamInviteLink.handled()
        }
    }
    val code = field.text
    LaunchedEffect(code) {
        preview = null
        error = null
        if (code.length == 6) {
            ListenTogether.previewParty(code)
                .onSuccess { preview = it }
                .onFailure { error = "No session with that code" }
        }
    }

    Box(Modifier.fillMaxSize().background(c.base)) {
        CoverGlow(null, height = 520.dp, intensity = 0.5f)
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar(onBack = { g.pop() }, title = "Listen together", centered = true)
            Column(Modifier.padding(horizontal = 20.dp).weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(16.dp))
                Text("Join a session", style = GlowType.display.copy(fontSize = 30.sp), color = c.text)
                Text(
                    "Enter the code your friend shared. You'll hear the same song at the same moment.",
                    style = GlowType.bodyRegular.copy(fontSize = 16.sp),
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(24.dp))
                Text("Session code", style = GlowType.secondary, color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = field,
                    onValueChange = { v ->
                        val clean = v.text.filter(Char::isLetterOrDigit).uppercase().take(6)
                        field = TextFieldValue(clean, TextRange(clean.length))
                    },
                    singleLine = true,
                    cursorBrush = SolidColor(Color.Transparent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    decorationBox = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            (0 until 6).forEach { i ->
                                if (i == 3) {
                                    Text("-", style = GlowType.title, color = c.textTertiary, modifier = Modifier.padding(horizontal = 6.dp))
                                }
                                val focused = i == code.length.coerceAtMost(5)
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(58.dp)
                                        .padding(horizontal = 3.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(c.glass)
                                        .border(1.5.dp, if (focused) c.text else c.divider, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(code.getOrNull(i)?.toString() ?: "", style = GlowType.title.copy(fontSize = 24.sp), color = c.text)
                                }
                            }
                        }
                    },
                )
                Spacer(Modifier.height(20.dp))
                error?.let { Text(it, style = GlowType.secondary, color = c.danger) }
                preview?.let { p ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.glass).padding(16.dp)) {
                        Text("Session found", style = GlowType.secondary, color = c.textSecondary)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InitialAvatar(p.hostName, size = 32.dp, background = Color(MEMBER_COLORS[1]), textColor = Color(0xFF111111))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Hosted by ${p.hostName} · ${p.memberCount} listening",
                                style = GlowType.bodyRegular,
                                color = c.text.copy(alpha = 0.85f),
                            )
                        }
                        if (p.isFull) Text("This session is full", style = GlowType.secondary, color = c.danger, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PillButton(
                    if (joining) "Joining…" else "Join session",
                    Modifier.fillMaxWidth(),
                    enabled = code.length == 6 && !joining && preview?.isFull != true,
                ) {
                    if (!ListenTogether.canJoin()) {
                        g.signInOpen = true
                        return@PillButton
                    }
                    joining = true
                    scope.launch {
                        ListenTogether.joinParty(code)
                            .onSuccess {
                                g.pop()
                                g.playerOpen = true
                            }
                            .onFailure { error = it.message ?: "Couldn't join" }
                        joining = false
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "The host controls playback. You can leave any time.",
                    style = GlowType.secondary,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
