package com.music.bitchord.data

import android.content.Context
import android.util.Log
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.ui.glow.artCandidates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * Playlist covers kept on the phone, so the Library shows them straight away, offline too,
 * without asking YouTube for them again on every visit.
 *
 * The image cache Coil keeps is shared with every other picture in the app and lives in the
 * cache folder, which Android empties when space runs low, so covers kept going missing and
 * were fetched again. These are files of their own, refreshed only when the library is synced
 * with YouTube ([sync]), and only for playlists whose cover address has changed since.
 */
object PlaylistCovers {
    private const val TAG = "Sonder"
    private const val PX = 544

    private var dir: File? = null
    private val lock = Mutex()

    /** browseId → the cover address it was saved from. */
    private val sources = HashMap<String, String>()

    private val _local = MutableStateFlow<Map<String, String>>(emptyMap())

    /** browseId → `file://` address of the saved cover. */
    val local: StateFlow<Map<String, String>> = _local.asStateFlow()

    fun init(context: Context) {
        if (dir != null) return
        val d = File(context.filesDir, "playlist_covers").apply { mkdirs() }
        dir = d
        runCatching {
            val index = File(d, "index.json").takeIf { it.exists() }?.readText() ?: return@runCatching
            val o = JSONObject(index)
            val shown = HashMap<String, String>()
            o.keys().forEach { id ->
                val url = o.getString(id)
                val file = fileFor(d, id, url)
                if (file.exists()) {
                    sources[id] = url
                    shown[id] = android.net.Uri.fromFile(file).toString()
                }
            }
            _local.value = shown
        }
    }

    /** The cover to show for [item]: the saved copy when there is one, else its address. */
    fun coverFor(item: ShelfItem, saved: Map<String, String>): String? =
        item.browseId?.let { saved[it] } ?: item.thumbnailUrl

    /**
     * Brings the saved covers in line with the playlists just read from YouTube: new or changed
     * covers are downloaded, covers of playlists no longer in the library are deleted.
     */
    suspend fun sync(playlists: List<ShelfItem>) = withContext(Dispatchers.IO) {
        val d = dir ?: return@withContext
        lock.withLock {
            val wanted = playlists.filter { it.browseId != null && !it.thumbnailUrl.isNullOrBlank() }
                .associate { it.browseId!! to it.thumbnailUrl!! }
            var changed = false
            (sources.keys - wanted.keys).forEach { gone ->
                fileFor(d, gone, sources.getValue(gone)).delete()
                sources.remove(gone)
                changed = true
            }
            wanted.forEach { (id, url) ->
                val file = fileFor(d, id, url)
                if (sources[id] == url && file.exists()) return@forEach
                if (download(url, file)) {
                    // A changed cover is a new file, so nothing shows the old picture from memory.
                    sources[id]?.takeIf { it != url }?.let { fileFor(d, id, it).delete() }
                    sources[id] = url
                    changed = true
                }
            }
            if (changed) {
                runCatching { File(d, "index.json").writeText(JSONObject(sources as Map<*, *>).toString()) }
                _local.value = sources.mapValues { (id, url) -> android.net.Uri.fromFile(fileFor(d, id, url)).toString() }
            }
        }
    }

    fun clear() {
        dir?.listFiles()?.forEach { it.delete() }
        sources.clear()
        _local.value = emptyMap()
    }

    private fun download(url: String, into: File): Boolean {
        for (candidate in artCandidates(url, PX)) {
            val ok = runCatching {
                Http.client.newCall(Request.Builder().url(candidate).build()).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) return@use false
                    val tmp = File(into.parentFile, into.name + ".tmp")
                    tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                    if (tmp.length() == 0L) { tmp.delete(); return@use false }
                    into.delete()
                    tmp.renameTo(into)
                }
            }.onFailure { Log.w(TAG, "playlist cover: couldn't save $candidate", it) }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    private fun fileFor(d: File, browseId: String, url: String) =
        File(d, "c_" + browseId.hashCode().toUInt() + "_" + url.hashCode().toUInt() + ".img")
}
