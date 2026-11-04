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
    @Volatile
    var active: Boolean = false
        private set

    fun init(context: Context) {
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        active = am.isLowRamDevice || info.totalMem in 1 until 1_600L * 1024 * 1024
        Log.i("Sonder", "memory: total=${info.totalMem / (1024 * 1024)} MB, class=${am.memoryClass} MB, lowRam=$active")
    }
}
