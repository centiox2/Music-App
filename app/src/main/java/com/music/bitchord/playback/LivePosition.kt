package com.music.bitchord.playback

import android.os.Looper
import androidx.media3.common.Player

/**
 * The playing position read straight from the service's own player, for word-by-word lyrics.
 *
 * The UI's MediaController only estimates the position between the session's updates, and
 * when an update corrects that estimate the position jumps, which words show as skipped or
 * stuttering. The service runs in the app's process on the main thread, so the player can be
 * asked directly. Null whenever that isn't safe or isn't the same track: on another thread,
 * while casting, or while the player is on a different item from the one asked about.
 */
object LivePosition {
    @Volatile internal var player: (() -> Player?)? = null

    fun positionMs(mediaId: String?): Long? {
        val p = player?.invoke() ?: return null
        if (mediaId == null || Looper.myLooper() != p.applicationLooper) return null
        if (p.currentMediaItem?.mediaId != mediaId) return null
        return p.currentPosition
    }
}
