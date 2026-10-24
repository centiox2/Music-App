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

    private val _lyricsTextSize = MutableStateFlow(30f)
    /** Size of the lyrics in the player, in sp. */
    val lyricsTextSize: StateFlow<Float> = _lyricsTextSize.asStateFlow()

    private val _lyricsKeepScreenOn = MutableStateFlow(true)
    val lyricsKeepScreenOn: StateFlow<Boolean> = _lyricsKeepScreenOn.asStateFlow()

    private val _pauseListenHistory = MutableStateFlow(false)
    val pauseListenHistory: StateFlow<Boolean> = _pauseListenHistory.asStateFlow()

    private val _pauseSearchHistory = MutableStateFlow(false)
    val pauseSearchHistory: StateFlow<Boolean> = _pauseSearchHistory.asStateFlow()

    private val _hideStatusBarInPlayer = MutableStateFlow(false)
    val hideStatusBarInPlayer: StateFlow<Boolean> = _hideStatusBarInPlayer.asStateFlow()

    /** How strongly the cover colours wash the screen, 0.3–1.3. */
    private val _glowIntensity = MutableStateFlow(1f)
    val glowIntensity: StateFlow<Float> = _glowIntensity.asStateFlow()

    private val _swipeMiniPlayer = MutableStateFlow(true)
    /** Swipe the mini player sideways to skip. */
    val swipeMiniPlayer: StateFlow<Boolean> = _swipeMiniPlayer.asStateFlow()

    private val _liquidBackground = MutableStateFlow(true)
    /** The player's moving mesh of cover colours; off falls back to the still glow. */
    val liquidBackground: StateFlow<Boolean> = _liquidBackground.asStateFlow()

    private val _coverShadow = MutableStateFlow(0.7f)
    /** Drop shadow under large covers, 0 (off) to 1 (deep). */
    val coverShadow: StateFlow<Float> = _coverShadow.asStateFlow()

    private val _autoSync = MutableStateFlow(true)
    /** Sync the YouTube library by itself when the app returns after a while. */
    val autoSync: StateFlow<Boolean> = _autoSync.asStateFlow()

    private val _lastSync = MutableStateFlow(0L)
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()

    private val _haptics = MutableStateFlow(true)
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()

    fun init(context: Context) {
        com.music.bitchord.ui.glow.lyrics.TranslationPrefs.init(context)
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("cover_glow", Context.MODE_PRIVATE)
        prefs = p
        _pureBlack.value = p.getBoolean("pure_black", false)
        _glowFromArtwork.value = p.getBoolean("glow_from_artwork", true)
        _fixedColor.value = p.getLong("fixed_color", fixedPalette.first())
        _startTab.value = p.getInt("start_tab", 0)
        _notificationsAsked.value = p.getBoolean("notifications_asked", false)
        _signInSkipped.value = p.getBoolean("sign_in_skipped", false)
        _lyricsTextSize.value = p.getFloat("lyrics_text_size", 30f)
        _lyricsKeepScreenOn.value = p.getBoolean("lyrics_keep_screen_on", true)
        _pauseListenHistory.value = p.getBoolean("pause_listen_history", false)
        _pauseSearchHistory.value = p.getBoolean("pause_search_history", false)
        com.music.bitchord.data.settings.SearchHistory.paused = _pauseSearchHistory.value
        _hideStatusBarInPlayer.value = p.getBoolean("hide_status_bar_in_player", false)
        _glowIntensity.value = p.getFloat("glow_intensity", 1f)
        _swipeMiniPlayer.value = p.getBoolean("swipe_mini_player", true)
        _haptics.value = p.getBoolean("haptics", true)
        _autoSync.value = p.getBoolean("auto_sync", true)
        _lastSync.value = p.getLong("last_sync", 0L)
        _liquidBackground.value = p.getBoolean("liquid_background", true)
        _coverShadow.value = p.getFloat("cover_shadow", 0.7f)
    }

    /** Every Cover Glow preference, for a settings backup. */
    fun export(): Map<String, Any?> = prefs?.all.orEmpty().filterKeys { it != "tr_api_key" && it != "tr_deepl_key" }

    fun import(values: Map<String, Any?>, context: Context) {
        val p = prefs ?: return
        p.edit().apply {
            values.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                    else -> Unit
                }
            }
        }.commit()
        prefs = null
        com.music.bitchord.ui.glow.lyrics.TranslationPrefs.reload(context)
        init(context)
    }

    fun setPureBlack(value: Boolean) { _pureBlack.value = value; prefs?.edit()?.putBoolean("pure_black", value)?.apply() }
    fun setGlowFromArtwork(value: Boolean) { _glowFromArtwork.value = value; prefs?.edit()?.putBoolean("glow_from_artwork", value)?.apply() }
    fun setFixedColor(value: Long) { _fixedColor.value = value; prefs?.edit()?.putLong("fixed_color", value)?.apply() }
    fun setStartTab(value: Int) { _startTab.value = value; prefs?.edit()?.putInt("start_tab", value)?.apply() }
    fun setNotificationsAsked() { _notificationsAsked.value = true; prefs?.edit()?.putBoolean("notifications_asked", true)?.apply() }
    fun setSignInSkipped(value: Boolean) { _signInSkipped.value = value; prefs?.edit()?.putBoolean("sign_in_skipped", value)?.apply() }
    fun setLyricsTextSize(value: Float) { _lyricsTextSize.value = value; prefs?.edit()?.putFloat("lyrics_text_size", value)?.apply() }
    fun setLyricsKeepScreenOn(value: Boolean) { _lyricsKeepScreenOn.value = value; prefs?.edit()?.putBoolean("lyrics_keep_screen_on", value)?.apply() }
    fun setPauseListenHistory(value: Boolean) { _pauseListenHistory.value = value; prefs?.edit()?.putBoolean("pause_listen_history", value)?.apply() }
    fun setPauseSearchHistory(value: Boolean) {
        _pauseSearchHistory.value = value
        com.music.bitchord.data.settings.SearchHistory.paused = value
        prefs?.edit()?.putBoolean("pause_search_history", value)?.apply()
    }
    fun setHideStatusBarInPlayer(value: Boolean) { _hideStatusBarInPlayer.value = value; prefs?.edit()?.putBoolean("hide_status_bar_in_player", value)?.apply() }
    fun setGlowIntensity(value: Float) { _glowIntensity.value = value; prefs?.edit()?.putFloat("glow_intensity", value)?.apply() }
    fun setSwipeMiniPlayer(value: Boolean) { _swipeMiniPlayer.value = value; prefs?.edit()?.putBoolean("swipe_mini_player", value)?.apply() }
    fun setHaptics(value: Boolean) { _haptics.value = value; prefs?.edit()?.putBoolean("haptics", value)?.apply() }
    fun setLiquidBackground(value: Boolean) { _liquidBackground.value = value; prefs?.edit()?.putBoolean("liquid_background", value)?.apply() }
    fun setCoverShadow(value: Float) { _coverShadow.value = value; prefs?.edit()?.putFloat("cover_shadow", value)?.apply() }
    fun setAutoSync(value: Boolean) { _autoSync.value = value; prefs?.edit()?.putBoolean("auto_sync", value)?.apply() }
    fun setLastSync(value: Long) { _lastSync.value = value; prefs?.edit()?.putLong("last_sync", value)?.apply() }
}
