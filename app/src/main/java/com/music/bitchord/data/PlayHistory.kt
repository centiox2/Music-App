package com.music.bitchord.data

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * What was played on this device, newest first, with when. The account's own
 * history comes from YouTube Music; this one also covers listening while
 * signed out, offline, or from downloads.
 */
object PlayHistory {
    data class Entry(val song: Song, val playedAtMs: Long)

    const val MAX_ENTRIES = 300
    private const val KEY = "entries"

    private var prefs: SharedPreferences? = null
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("play_history", Context.MODE_PRIVATE)
        prefs = p
        _entries.value = runCatching { parse(p.getString(KEY, null)) }.getOrDefault(emptyList())
    }

    /** Records [song] as played now, unless it is already the latest entry. */
    fun record(song: Song, nowMs: Long = System.currentTimeMillis()) {
        val current = _entries.value
        if (current.firstOrNull()?.song?.videoId == song.videoId) return
        val next = (listOf(Entry(song, nowMs)) + current).take(MAX_ENTRIES)
        _entries.value = next
        persist(next)
    }

    fun remove(entry: Entry) {
        val next = _entries.value - entry
        _entries.value = next
        persist(next)
    }

    fun clear() {
        _entries.value = emptyList()
        persist(emptyList())
    }

    /**
     * Adds plays from elsewhere (an imported backup). Kept newest first; anything already listed
     * at the same moment is not added twice, and only the newest [MAX_ENTRIES] remain.
     */
    fun mergeOlder(more: List<Entry>) {
        if (more.isEmpty()) return
        val merged = (_entries.value + more)
            .distinctBy { it.song.videoId + "@" + it.playedAtMs / 1000 }
            .sortedByDescending { it.playedAtMs }
            .take(MAX_ENTRIES)
        _entries.value = merged
        persist(merged)
    }

    /** The whole history as stored, for a backup. */
    fun exportRaw(): String = prefs?.getString(KEY, null) ?: "[]"

    /** Replaces the history with one from a backup ([exportRaw]'s form). */
    fun importRaw(raw: String) {
        val list = runCatching { parse(raw) }.getOrNull() ?: return
        val trimmed = list.take(MAX_ENTRIES)
        _entries.value = trimmed
        persist(trimmed)
    }

    private fun persist(list: List<Entry>) {
        val array = JSONArray()
        list.forEach { e ->
            array.put(
                JSONObject()
                    .put("videoId", e.song.videoId)
                    .put("title", e.song.title)
                    .put("artist", e.song.artist)
                    .put("thumb", e.song.thumbnailUrl ?: "")
                    .put("duration", e.song.durationText ?: "")
                    .put("artistId", e.song.artistId ?: "")
                    .put("albumId", e.song.albumId ?: "")
                    .put("albumName", e.song.albumName ?: "")
                    .put("isVideo", e.song.isVideo)
                    .put("at", e.playedAtMs),
            )
        }
        prefs?.edit()?.putString(KEY, array.toString())?.apply()
    }

    private fun parse(raw: String?): List<Entry> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Entry(
                Song(
                    videoId = o.getString("videoId"),
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    thumbnailUrl = o.optString("thumb").ifBlank { null },
                    durationText = o.optString("duration").ifBlank { null },
                    artistId = o.optString("artistId").ifBlank { null },
                    albumId = o.optString("albumId").ifBlank { null },
                    albumName = o.optString("albumName").ifBlank { null },
                    isVideo = o.optBoolean("isVideo"),
                ),
                o.optLong("at"),
            )
        }
    }
}
