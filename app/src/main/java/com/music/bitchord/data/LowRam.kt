package com.music.bitchord.data

import android.app.ActivityManager
import android.content.Context
import android.util.Log

/**
 * Whether this device is short of memory: Android's own low-RAM flag, or under ~1.6 GB in all
 * (a 1 GB Android TV box, a budget phone). The app then keeps a smaller picture cache, analyses
 * tracks for Automix on one thread, holds the player's moving background still, and lets go of
 * cached pictures as soon as Android asks for memory back.
 */
object LowRam {
    /** Android's recommendation before the user's preference is applied. */
    @Volatile
    var recommended: Boolean = false
        private set

    @Volatile
    var active: Boolean = false
        private set

    fun init(context: Context) {
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        recommended = recommendedFor(am.isLowRamDevice, info.totalMem)
        active = recommended
        Log.i("Sonder", "memory: total=${info.totalMem / (1024 * 1024)} MB, class=${am.memoryClass} MB, lowRam=$recommended")
    }

    /** Applies the persisted override after hardware detection has supplied its default. */
    fun setEnabled(enabled: Boolean) {
        active = enabled
    }

    internal fun recommendedFor(systemLowRam: Boolean, totalBytes: Long): Boolean =
        systemLowRam || totalBytes in 1 until LOW_MEMORY_THRESHOLD_BYTES

    private const val LOW_MEMORY_THRESHOLD_BYTES = 1_600L * 1024 * 1024
}
