package com.music.bitchord

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.listentogether.JamInviteLink
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.MusicLink
import com.music.bitchord.playback.PlayerDeepLink
import com.music.bitchord.ui.glow.GlowApp
import com.music.bitchord.ui.glow.GlowTheme
import com.music.bitchord.ui.performance.resolvePerformanceRefreshRate

/**
 * Whether disliking [targetVideoId] should skip past it: only when it is the
 * track playing now and it wasn't already disliked.
 */
internal fun shouldSkipAfterDislike(
    previousStatus: LikeStatus,
    targetVideoId: String,
    currentVideoId: String?,
): Boolean = previousStatus != LikeStatus.DISLIKE && targetVideoId == currentVideoId

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Before the composition, so a cold launch from a widget, an invite
        // or a shared link has its request standing by when the UI reads it.
        PlayerDeepLink.consume(intent)
        JamInviteLink.consume(intent)
        MusicLink.consume(intent)
        setContent {
            val highPerformance by AppSettings.highPerformanceMode.collectAsStateWithLifecycle()
            val performanceRefreshRate by AppSettings.performanceRefreshRate.collectAsStateWithLifecycle()
            val composeView = LocalView.current
            LaunchedEffect(highPerformance, performanceRefreshRate, composeView) {
                applyPerformanceMode(highPerformance, performanceRefreshRate, composeView)
            }
            GlowTheme {
                GlowApp()
            }
        }
    }

    /**
     * Requests a window refresh rate without forcing a display mode. Android
     * may still lower it for temperature, battery or hardware limits.
     */
    private fun applyPerformanceMode(enabled: Boolean, refreshRate: Int, composeView: View) {
        val supportedRefreshRate = composeView.display.resolvePerformanceRefreshRate(refreshRate)
        window.attributes = window.attributes.apply {
            preferredRefreshRate = if (enabled) supportedRefreshRate.toFloat() else 0f
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.setFrameRatePowerSavingsBalanced(!enabled)
            composeView.requestedFrameRate = if (enabled) {
                supportedRefreshRate.toFloat()
            } else {
                View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
            }
        }
    }

    /**
     * This activity is `singleTask`, so a second tap on a widget or link
     * arrives here rather than rebuilding anything.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PlayerDeepLink.consume(intent)
        JamInviteLink.consume(intent)
        MusicLink.consume(intent)
    }
}
