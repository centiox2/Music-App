package com.music.bitchord.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.model.AccountChannel

/**
 * The channels of the signed-in Google account, chosen in the app (no web page): one account can
 * hold a personal channel and brand channels, and the library, likes and history are per channel.
 * Opened from Settings → account → Switch channel, and after signing in when there is more than one.
 */
@Composable
fun ChannelPickerSheet(afterSignIn: Boolean, onDismiss: () -> Unit) {
    val g = LocalGlow.current
    val vm = g.vm
    val c = Glow.colors
    val channels by vm.channels.collectAsStateWithLifecycle()
    val loading by vm.channelsLoading.collectAsStateWithLifecycle()
    val selected by vm.selectedChannelKey.collectAsStateWithLifecycle()
    var asked by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.loadChannels(force = true)
        asked = true
    }
    // Straight after signing in, only ask when there is a real choice.
    LaunchedEffect(asked, loading, channels.size) {
        if (afterSignIn && asked && !loading && channels.size <= 1) onDismiss()
    }
    if (afterSignIn && (loading || !asked || channels.size <= 1)) return

    // The same identity the view model files a channel under (pageId, else dataSyncId). Not "any
    // id matches": every channel of one Google account carries that account's user id as its
    // dataSyncId, so matching on it marked them all as current and a press switched nothing.
    fun isSelected(ch: AccountChannel): Boolean =
        if (selected != null) selected == (ch.pageId ?: ch.dataSyncId) else ch.activeOnWeb

    GlowSheet(onDismiss) {
        GlowText(if (afterSignIn) "Which channel?" else "Switch channel", GlowType.titleSmall)
        Spacer(Modifier.height(4.dp))
        GlowText(
            "Your library, likes and history belong to the channel you choose.",
            GlowType.secondary,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(14.dp))
        when {
            loading && channels.isEmpty() -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = c.text, strokeWidth = 2.5.dp)
            }
            channels.isEmpty() -> GlowText(
                "Couldn't load your channels. You can pick one on YouTube Music instead.",
                GlowType.body,
                color = c.textSecondary,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                channels.forEach { ch ->
                    val on = isSelected(ch)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) c.glassStrong else c.glass)
                            .clickable {
                                if (!on) {
                                    vm.selectChannel(ch)
                                    g.notify("Switched to ${ch.name}")
                                }
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InitialAvatar(ch.name, size = 44.dp, imageUrl = ch.thumbnailUrl)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            GlowText(ch.name, GlowType.body, maxLines = 1)
                            if (ch.subtitle.isNotBlank()) {
                                GlowText(ch.subtitle, GlowType.secondary, color = c.textSecondary, maxLines = 1)
                            }
                        }
                        if (on) Icon(Icons.Rounded.CheckCircle, "Current channel", tint = c.text, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GlowText(
            "Choose on YouTube Music instead",
            GlowType.secondary,
            color = c.textSecondary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    onDismiss()
                    g.switchChannelOpen = true
                }
                .padding(10.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}
