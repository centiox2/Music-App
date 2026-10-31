package com.music.bitchord.data

import android.content.Context
import android.net.Uri
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.SearchHistory
import com.music.bitchord.data.stats.Backup
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.StoredBucket
import com.music.bitchord.ui.glow.GlowPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Sonder's backup: one JSON file with everything that lives only on this phone.
 *
 * - every setting (BitChord's and Sonder's own), without passwords, keys or sign-ins;
 * - listening statistics, month by month (what the Statistics page draws);
 * - the listening history kept on this phone;
 * - lyrics you edited by hand.
 *
 * Not included: downloads, cached audio and pictures (they come back by themselves), and the
 * account's library (it lives on YouTube).
 *
 * Restoring replaces what is on the phone with what is in the file. Older Sonder settings
 * backups ("sonder-settings", "cover-glow-settings") and BitChord's own backups restore too.
 */
object SonderBackup {
    const val FORMAT = "sonder-backup"
    private const val VERSION = 2

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** What a backup held, for the line shown once it has been written or read. */
    data class Summary(
        val settings: Int,
        val months: Int,
        val history: Int,
        val lyrics: Int,
        /** Set for a ViVi / Metrolist import: plays added, and plays already imported before. */
        val importedPlays: Int? = null,
        val alreadyImported: Int = 0,
        val importedMs: Long = 0L,
    ) {
        fun describe(): String = if (importedPlays != null) {
            when {
                importedPlays > 0 -> "%,d plays (%s) from ViVi Music, added to your statistics".format(
                    importedPlays,
                    com.music.bitchord.ui.glow.formatHoursMinutes(importedMs),
                ) + if (alreadyImported > 0) "; %,d were already imported".format(alreadyImported) else ""
                else -> "nothing new: those %,d plays were already imported".format(alreadyImported)
            }
        } else buildList {
            add(if (settings == 1) "1 setting" else "$settings settings")
            if (months > 0) add(if (months == 1) "1 month of statistics" else "$months months of statistics")
            if (history > 0) add(if (history == 1) "1 song of history" else "$history songs of history")
            if (lyrics > 0) add(if (lyrics == 1) "1 edited lyric" else "$lyrics edited lyrics")
        }.joinToString(", ")
    }

    fun suggestedName(): String =
        "sonder-backup-" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Instant.now().atZone(ZoneId.systemDefault())) + ".json"

    suspend fun export(context: Context, target: Uri): Result<Summary> = withContext(Dispatchers.IO) {
        runCatching {
            val app = AppSettings.exportPrefs().toTypedJson()
            val glow = GlowPrefs.export().toTypedJson()
            val buckets = ListeningStats.exportAll()
            val history = JSONArray(PlayHistory.exportRaw())
            val lyrics = JSONObject().also { out ->
                lyricsDir(context).listFiles { f -> f.isFile && f.name.endsWith(".lrc") }
                    ?.forEach { out.put(it.name, it.readText()) }
            }
            val file = JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("exportedAt", Instant.now().toString())
                .put("app", app)
                .put("glow", glow)
                .put("listening", JSONArray(json.encodeToString(ListSerializer(StoredBucket.serializer()), buckets)))
                .put("playHistory", history)
                .put("lyricsEdits", lyrics)
                .put("listeningImports", com.music.bitchord.data.stats.ViViImport.exportMarks(context))
            context.contentResolver.openOutputStream(target, "wt")?.use { it.write(file.toString(2).toByteArray()) }
                ?: error("Couldn't open that file for writing")
            Summary(app.length() + glow.length(), buckets.size, history.length(), lyrics.length())
        }
    }

    /**
     * Reads [source] and restores it. Checked whole before anything is written, so a file that
     * isn't a backup changes nothing.
     */
    suspend fun restore(context: Context, source: Uri): Result<Summary> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                ?: error("Couldn't open that file")
            // ViVi Music / Metrolist backups are zips with the listening in song.db.
            if (com.music.bitchord.data.stats.ViViImport.looksLikeZip(bytes)) {
                val r = com.music.bitchord.data.stats.ViViImport.import(context, source).getOrThrow()
                return@runCatching Summary(0, r.months, 0, 0, importedPlays = r.plays, alreadyImported = r.skipped, importedMs = r.totalMs)
            }
            val text = bytes.decodeToString()
            val root = runCatching { JSONObject(text) }.getOrElse { error("That file isn't a Sonder backup") }
            when {
                root.optString("format") == FORMAT -> restoreFull(context, root)
                root.optString("format") in setOf("sonder-settings", "cover-glow-settings") -> restoreSettings(context, root)
                root.optString("app") == "bitchord" -> {
                    val s = Backup.importFrom(context, source).getOrThrow()
                    Summary(s.settings, s.months, 0, 0)
                }
                else -> error("That file isn't a Sonder backup")
            }
        }
    }

    private suspend fun restoreFull(context: Context, root: JSONObject): Summary {
        require(root.optInt("version", 1) <= VERSION) { "That backup was made by a newer version of Sonder" }
        val app = root.optJSONObject("app")?.fromTypedJson().orEmpty()
        val glow = root.optJSONObject("glow")?.fromTypedJson().orEmpty()
        // Parsed up front, so a damaged section stops the restore before anything changes.
        val buckets = root.optJSONArray("listening")?.let {
            json.decodeFromString(ListSerializer(StoredBucket.serializer()), it.toString())
        }
        val history = root.optJSONArray("playHistory")
        val lyrics = root.optJSONObject("lyricsEdits")

        withContext(Dispatchers.Main) {
            AppSettings.importPrefs(app)
            GlowPrefs.import(glow, context)
            SearchHistory.reload()
        }
        // Merged, never replaced: statistics already on this phone are kept (each figure keeps
        // the larger of the two), so restoring can't lose listening or count it twice.
        buckets?.let { ListeningStats.mergeAll(it, ListeningStats.MergeMode.MAX) }
        root.optJSONObject("listeningImports")?.let { com.music.bitchord.data.stats.ViViImport.restoreMarks(context, it) }
        // Merged with this phone's history rather than replacing it.
        history?.let { raw ->
            val before = PlayHistory.entries.value
            PlayHistory.importRaw(raw.toString())
            PlayHistory.mergeOlder(before)
        }
        var lyricCount = 0
        lyrics?.let { edits ->
            val dir = lyricsDir(context)
            edits.keys().forEach { name ->
                // Only plain file names, never a path out of the folder.
                if (!name.matches(Regex("[A-Za-z0-9_-]+\\.lrc"))) return@forEach
                File(dir, name).writeText(edits.getString(name))
                lyricCount++
            }
        }
        return Summary(app.size + glow.size, buckets?.size ?: 0, history?.length() ?: 0, lyricCount)
    }

    private suspend fun restoreSettings(context: Context, root: JSONObject): Summary {
        val app = root.getJSONObject("app").fromTypedJson()
        val glow = root.optJSONObject("glow")?.fromTypedJson().orEmpty()
        withContext(Dispatchers.Main) {
            AppSettings.importPrefs(app)
            GlowPrefs.import(glow, context)
        }
        return Summary(app.size + glow.size, 0, 0, 0)
    }

    private fun lyricsDir(context: Context) = File(context.filesDir, "lyrics_edits").apply { mkdirs() }

    /** Each value with its type, so it goes back as exactly the type it left as. */
    internal fun Map<String, Any?>.toTypedJson(): JSONObject = JSONObject().also { out ->
        forEach { (key, value) ->
            val (type, v) = when (value) {
                is Boolean -> "b" to value
                is Int -> "i" to value
                is Long -> "l" to value
                is Float -> "f" to value.toDouble()
                is String -> "s" to value
                is Set<*> -> "set" to JSONArray(value.filterIsInstance<String>())
                else -> return@forEach
            }
            out.put(key, JSONObject().put("t", type).put("v", v))
        }
    }

    internal fun JSONObject.fromTypedJson(): Map<String, Any?> = buildMap {
        keys().forEach { key ->
            val entry = optJSONObject(key) ?: return@forEach
            put(
                key,
                when (entry.optString("t")) {
                    "b" -> entry.getBoolean("v")
                    "i" -> entry.getInt("v")
                    "l" -> entry.getLong("v")
                    "f" -> entry.getDouble("v").toFloat()
                    "s" -> entry.getString("v")
                    "set" -> entry.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                    else -> return@forEach
                },
            )
        }
    }
}
