package com.music.bitchord.ui.glow

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferences that belong to the Cover Glow interface alone. Everything about
 * playback, lyrics, accounts and audio lives in [com.music.bitchord.data.settings.AppSettings];
 * these only change how the screens look.
 */
object GlowPrefs {
    /** Colours the user can pin the glow to instead of following the artwork. */
    val fixedPalette = listOf(0xFF2F5A55, 0xFF5B2A86, 0xFFB8741F, 0xFF8E1C17, 0xFF1F3A63, 0xFF3A3A3A)

    private var prefs: SharedPreferences? = null

    private val _pureBlack = MutableStateFlow(false)
    val pureBlack: StateFlow<Boolean> = _pureBlack.asStateFlow()

    private val _glowFromArtwork = MutableStateFlow(true)
    val glowFromArtwork: StateFlow<Boolean> = _glowFromArtwork.asStateFlow()

    private val _fixedColor = MutableStateFlow(fixedPalette.first())
    val fixedColor: StateFlow<Long> = _fixedColor.asStateFlow()

    /** 0 = Home, 1 = Search, 2 = Library. */
    private val _startTab = MutableStateFlow(0)
    val startTab: StateFlow<Int> = _startTab.asStateFlow()

    private val _notificationsAsked = MutableStateFlow(false)
    val notificationsAsked: StateFlow<Boolean> = _notificationsAsked.asStateFlow()

    private val _signInSkipped = MutableStateFlow(false)
    val signInSkipped: StateFlow<Boolean> = _signInSkipped.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("cover_glow", Context.MODE_PRIVATE)
        prefs = p
        _pureBlack.value = p.getBoolean("pure_black", false)
        _glowFromArtwork.value = p.getBoolean("glow_from_artwork", true)
        _fixedColor.value = p.getLong("fixed_color", fixedPalette.first())
        _startTab.value = p.getInt("start_tab", 0)
        _notificationsAsked.value = p.getBoolean("notifications_asked", false)
        _signInSkipped.value = p.getBoolean("sign_in_skipped", false)
    }

    fun setPureBlack(value: Boolean) { _pureBlack.value = value; prefs?.edit()?.putBoolean("pure_black", value)?.apply() }
    fun setGlowFromArtwork(value: Boolean) { _glowFromArtwork.value = value; prefs?.edit()?.putBoolean("glow_from_artwork", value)?.apply() }
    fun setFixedColor(value: Long) { _fixedColor.value = value; prefs?.edit()?.putLong("fixed_color", value)?.apply() }
    fun setStartTab(value: Int) { _startTab.value = value; prefs?.edit()?.putInt("start_tab", value)?.apply() }
    fun setNotificationsAsked() { _notificationsAsked.value = true; prefs?.edit()?.putBoolean("notifications_asked", true)?.apply() }
    fun setSignInSkipped(value: Boolean) { _signInSkipped.value = value; prefs?.edit()?.putBoolean("sign_in_skipped", value)?.apply() }
}
