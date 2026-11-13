package com.music.bitchord.data

import android.app.UiModeManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The individual performance switches (Settings → Performance), each one costing frames or
 * memory on a slow device. They are separate so a TV that copes with one effect can keep it.
 *
 * Defaults depend on the device: on an Android TV, or a device Android or [LowRam] calls short
 * of memory, every heavy effect starts off ([lightDefaults]); elsewhere they start on. A choice
 * the user makes is stored and wins from then on. "Apply TV settings" puts the light set back.
 */
object PerformanceProfile {
    private var prefs: SharedPreferences? = null

    /** Running on an Android TV (leanback, or the system in television mode). */
    @Volatile
    var isTv: Boolean = false
        private set

    /** Whether heavy effects start off here: a TV, or a device short of memory. */
    @Volatile
    var lightDefaults: Boolean = false
        private set

    private val _animatedBackground = MutableStateFlow(true)
    /** The player's moving liquid background. Off: a still glow from the cover's colours. */
    val animatedBackground: StateFlow<Boolean> = _animatedBackground.asStateFlow()

    private val _wordLyrics = MutableStateFlow(true)
    /** Lyrics lit word by word (and letter by letter). Off: the whole line lights at once. */
    val wordLyrics: StateFlow<Boolean> = _wordLyrics.asStateFlow()

    private val _lyricsBlur = MutableStateFlow(true)
    /** Lyric lines away from the one being sung are softly blurred (a GPU blur per line). */
    val lyricsBlur: StateFlow<Boolean> = _lyricsBlur.asStateFlow()

    private val _imageFade = MutableStateFlow(true)
    /** Covers fade in as they load. Off: they appear at once (less redrawing while scrolling). */
    val imageFade: StateFlow<Boolean> = _imageFade.asStateFlow()

    private val _coverShadows = MutableStateFlow(true)
    /** Soft shadows under large covers (a blurred layer drawn every frame they are on screen). */
    val coverShadows: StateFlow<Boolean> = _coverShadows.asStateFlow()

    private val _motion = MutableStateFlow(true)
    /** Small UI motion: cascades as pages open, press bounces, the colour shifts between covers. */
    val motion: StateFlow<Boolean> = _motion.asStateFlow()

    private val _hdThumbnails = MutableStateFlow(true)
    /** Video thumbnails at their full HD size everywhere, list rows included. */
    val hdThumbnails: StateFlow<Boolean> = _hdThumbnails.asStateFlow()

    private val _audioAnalysis = MutableStateFlow(true)
    /** Background decoding of upcoming tracks for Automix and version matching. */
    val audioAnalysis: StateFlow<Boolean> = _audioAnalysis.asStateFlow()

    private val _diagnosticLog = MutableStateFlow(true)
    /** The in-app log (Settings → Logs): a logcat process writing to a file all session. */
    val diagnosticLog: StateFlow<Boolean> = _diagnosticLog.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        isTv = detectTv(app)
        lightDefaults = isTv || LowRam.recommended
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        fun read(key: String, default: Boolean) = runCatching { p.getBoolean(key, default) }.getOrDefault(default)
        val rich = !lightDefaults
        _animatedBackground.value = read(KEY_BACKGROUND, rich)
        _wordLyrics.value = read(KEY_WORD_LYRICS, rich)
        _lyricsBlur.value = read(KEY_LYRICS_BLUR, rich)
        _imageFade.value = read(KEY_IMAGE_FADE, rich)
        _coverShadows.value = read(KEY_SHADOWS, rich)
        _motion.value = read(KEY_MOTION, rich)
        _hdThumbnails.value = read(KEY_HD_THUMBS, true)
        _audioAnalysis.value = read(KEY_ANALYSIS, rich)
        _diagnosticLog.value = read(KEY_LOG, true)
    }

    private fun put(key: String, value: Boolean) {
        prefs?.edit()?.putBoolean(key, value)?.apply()
    }

    fun setAnimatedBackground(value: Boolean) { _animatedBackground.value = value; put(KEY_BACKGROUND, value) }
    fun setWordLyrics(value: Boolean) { _wordLyrics.value = value; put(KEY_WORD_LYRICS, value) }
    fun setLyricsBlur(value: Boolean) { _lyricsBlur.value = value; put(KEY_LYRICS_BLUR, value) }
    fun setImageFade(value: Boolean) { _imageFade.value = value; put(KEY_IMAGE_FADE, value) }
    fun setCoverShadows(value: Boolean) { _coverShadows.value = value; put(KEY_SHADOWS, value) }
    fun setMotion(value: Boolean) { _motion.value = value; put(KEY_MOTION, value) }
    fun setHdThumbnails(value: Boolean) { _hdThumbnails.value = value; put(KEY_HD_THUMBS, value) }
    fun setAudioAnalysis(value: Boolean) { _audioAnalysis.value = value; put(KEY_ANALYSIS, value) }
    fun setDiagnosticLog(value: Boolean) { _diagnosticLog.value = value; put(KEY_LOG, value) }

    /** Every effect off except HD thumbnails and the log: what a slow TV is best with. */
    fun applyLight() {
        setAnimatedBackground(false)
        setWordLyrics(false)
        setLyricsBlur(false)
        setImageFade(false)
        setCoverShadows(false)
        setMotion(false)
        setAudioAnalysis(false)
    }

    /** Every effect on. */
    fun applyFull() {
        setAnimatedBackground(true)
        setWordLyrics(true)
        setLyricsBlur(true)
        setImageFade(true)
        setCoverShadows(true)
        setMotion(true)
        setAudioAnalysis(true)
    }

    /** [diagnosticLog] as stored, readable before [init] (the log starts first thing at launch). */
    fun storedDiagnosticLog(context: Context): Boolean = runCatching {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LOG, true)
    }.getOrDefault(true)

    private fun detectTv(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val ui = context.getSystemService(UiModeManager::class.java)
        ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            @Suppress("DEPRECATION") pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }.getOrDefault(false)

    private const val PREFS = "sonder_performance"
    private const val KEY_BACKGROUND = "animated_background"
    private const val KEY_WORD_LYRICS = "word_lyrics"
    private const val KEY_LYRICS_BLUR = "lyrics_blur"
    private const val KEY_IMAGE_FADE = "image_fade"
    private const val KEY_SHADOWS = "cover_shadows"
    private const val KEY_MOTION = "motion"
    private const val KEY_HD_THUMBS = "hd_thumbnails"
    private const val KEY_ANALYSIS = "audio_analysis"
    private const val KEY_LOG = "diagnostic_log"
}
