package com.music.bitchord.data.stats

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.music.bitchord.data.PlayHistory
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Listening history from a ViVi Music backup (also Metrolist / InnerTune / OuterTune, which share
 * the format): a zip holding `song.db`, whose `event` table has one row per play.
 *
 * Only the listening is read: plays become [ListeningStats] months and the newest of them fill
 * out this phone's history. The backup's settings are never applied: they belong to another app
 * and carry its YouTube sign-in.
 *
 * Added to what is already kept ([ListeningStats.MergeMode.ADD]), never in place of it. To keep a
 * second import of the same file (or an older one) from counting plays twice, the newest play
 * imported is remembered and only later plays are taken next time.
 */
object ViViImport {

    /** One play, as read from the backup. [localTime] is the phone's own clock time of the play. */
    data class Play(
        val videoId: String,
        val title: String,
        val artists: List<Pair<String, String?>>,
        val artistArt: String?,
        val albumId: String?,
        val albumName: String?,
        val art: String?,
        val durationSec: Int,
        val localTime: LocalDateTime,
        val stamp: Long,
        val playedMs: Long,
    )

    data class Summary(val plays: Int, val skipped: Int, val months: Int, val totalMs: Long)

    /** Whether [head] (the file's first bytes) is a zip, the shape of these backups. */
    fun looksLikeZip(head: ByteArray): Boolean =
        head.size >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
            head[2].toInt() == 3 && head[3].toInt() == 4

    suspend fun import(context: Context, source: Uri): Result<Summary> = withContext(Dispatchers.IO) {
        runCatching {
            val db = File(context.cacheDir, "vivi_import.db")
            try {
                val found = context.contentResolver.openInputStream(source)?.use { extractSongDb(it, db) }
                    ?: error("Couldn't open that file")
                require(found) { "That backup has no listening history (song.db)" }
                val plays = readPlays(db)
                val after = watermark(context)
                val fresh = plays.filter { it.stamp > after }
                if (fresh.isNotEmpty()) {
                    val buckets = toBuckets(fresh)
                    ListeningStats.mergeAll(buckets, ListeningStats.MergeMode.ADD)
                    setWatermark(context, fresh.maxOf { it.stamp })
                    PlayHistory.mergeOlder(
                        fresh.sortedByDescending { it.stamp }.take(PlayHistory.MAX_ENTRIES).map { p ->
                            PlayHistory.Entry(p.toSong(), p.localTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                        },
                    )
                    Summary(fresh.size, plays.size - fresh.size, buckets.size, fresh.sumOf { it.playedMs })
                } else {
                    Summary(0, plays.size, 0, 0L)
                }
            } finally {
                db.delete()
                File(db.path + "-journal").delete()
            }
        }
    }

    /** Copies `song.db` out of the zip into [into]; false when the zip has none. */
    private fun extractSongDb(input: InputStream, into: File): Boolean {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return false
                if (!entry.isDirectory && entry.name.substringAfterLast('/') == "song.db") {
                    into.outputStream().use { zip.copyTo(it) }
                    return true
                }
            }
        }
    }

    private fun readPlays(file: File): List<Play> {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        db.use {
            // Credited artists per song, in billing order, with their pictures.
            val artists = HashMap<String, MutableList<Triple<Int, String, String?>>>()
            val artistArt = HashMap<String, String?>()
            it.rawQuery(
                "SELECT m.songId, m.position, a.name, a.id, a.thumbnailUrl FROM song_artist_map m JOIN artist a ON a.id = m.artistId",
                null,
            ).use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(3)
                    artists.getOrPut(c.getString(0)) { mutableListOf() } += Triple(c.getInt(1), c.getString(2), id)
                    artistArt[id] = if (c.isNull(4)) null else c.getString(4)
                }
            }
            val out = ArrayList<Play>()
            it.rawQuery(
                "SELECT e.songId, e.timestamp, e.playTime, s.title, s.duration, s.thumbnailUrl, s.albumId, s.albumName " +
                    "FROM event e JOIN song s ON s.id = e.songId ORDER BY e.timestamp",
                null,
            ).use { c ->
                while (c.moveToNext()) {
                    val songId = c.getString(0)
                    val credits = artists[songId].orEmpty().sortedBy { a -> a.first }
                    val stamp = c.getLong(1)
                    out += Play(
                        videoId = songId,
                        title = c.getString(3),
                        artists = credits.map { a -> a.second to a.third },
                        artistArt = credits.firstOrNull()?.third?.let(artistArt::get),
                        albumId = if (c.isNull(6)) null else c.getString(6),
                        albumName = if (c.isNull(7)) null else c.getString(7),
                        art = if (c.isNull(5)) null else c.getString(5),
                        durationSec = c.getInt(4),
                        localTime = localTimeOf(stamp),
                        stamp = stamp,
                        playedMs = c.getLong(2).coerceAtLeast(0L),
                    )
                }
            }
            return out
        }
    }

    /**
     * These apps store a play's time as the phone's local clock time written as if it were UTC
     * (`LocalDateTime` → epoch at offset 0), so it is read back the same way.
     */
    fun localTimeOf(stamp: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(stamp), ZoneOffset.UTC)

    /** The plays as monthly buckets, in exactly the shape [ListeningStats.record] writes. */
    fun toBuckets(plays: List<Play>): List<StoredBucket> =
        plays.groupBy { YearMonth.from(it.localTime) }.toSortedMap().map { (month, monthPlays) ->
            val tracks = LinkedHashMap<String, TrackEntry>()
            val artists = LinkedHashMap<String, NameEntry>()
            val albums = LinkedHashMap<String, NameEntry>()
            val hours = LongArray(24)
            val hourPlays = IntArray(24)
            val days = HashMap<Int, Long>()
            val trackDays = HashMap<String, DayStats>()
            val artistDays = HashMap<String, DayStats>()
            val albumDays = HashMap<String, DayStats>()
            monthPlays.forEach { p ->
                val t = p.localTime
                val dayKey = "%04d-%02d-%02d".format(t.year, t.monthValue, t.dayOfMonth)
                val ms = p.playedMs
                val credit = p.artists.joinToString(", ") { it.first }
                val leadId = p.artists.firstOrNull()?.second?.takeIf { it.startsWith("UC") }
                val track = tracks.getOrPut(p.videoId) {
                    TrackEntry(
                        id = p.videoId,
                        title = p.title,
                        artist = credit,
                        album = p.albumName,
                        albumId = p.albumId,
                        artistId = leadId,
                        art = p.art,
                    )
                }
                track.ms += ms
                track.plays++
                track.last = maxOf(track.last, t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                track.days.getOrPut(dayKey) { DayStats() }.add(ms)
                trackDays.getOrPut(dayKey) { DayStats() }.add(ms)

                ListeningStats.primaryArtist(credit)?.let { name ->
                    val a = artists.getOrPut(name.lowercase(Locale.ROOT)) { NameEntry(name = name, art = p.artistArt ?: p.art) }
                    a.ms += ms
                    a.plays++
                    if (a.id == null) a.id = leadId
                    a.days.getOrPut(dayKey) { DayStats() }.add(ms)
                    artistDays.getOrPut(dayKey) { DayStats() }.add(ms)
                }
                p.albumName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
                    val key = p.albumId ?: (name.lowercase(Locale.ROOT) + "\u001f" + credit.lowercase(Locale.ROOT))
                    val a = albums.getOrPut(key) { NameEntry(name = name, sub = credit, art = p.art, id = p.albumId) }
                    a.ms += ms
                    a.plays++
                    a.days.getOrPut(dayKey) { DayStats() }.add(ms)
                    albumDays.getOrPut(dayKey) { DayStats() }.add(ms)
                }
                hours[t.hour] += ms
                hourPlays[t.hour]++
                days[t.dayOfMonth] = (days[t.dayOfMonth] ?: 0L) + ms
            }
            StoredBucket(
                month = month.toString(),
                tracks = tracks.values.toList(),
                artists = artists.values.toList(),
                albums = albums.values.toList(),
                hours = hours.toList(),
                hourPlays = hourPlays.toList(),
                days = days,
                trackDays = trackDays,
                artistDays = artistDays,
                albumDays = albumDays,
            )
        }

    private fun DayStats.add(playedMs: Long) {
        ms += playedMs
        plays++
    }

    private fun Play.toSong() = Song(
        videoId = videoId,
        title = title,
        artist = artists.joinToString(", ") { it.first },
        thumbnailUrl = art,
        durationText = if (durationSec > 0) "%d:%02d".format(durationSec / 60, durationSec % 60) else null,
        artistId = artists.firstOrNull()?.second?.takeIf { it.startsWith("UC") },
        albumId = albumId,
        albumName = albumName,
    )

    // The newest play already imported, so the same plays are never added twice. Carried in
    // Sonder's own backups too, so a restore on a new phone remembers what was imported.
    private fun markFile(context: Context) = File(context.filesDir, "listening_imports.json")

    private fun watermark(context: Context): Long = runCatching {
        JSONObject(markFile(context).readText()).optLong("vivi", Long.MIN_VALUE)
    }.getOrDefault(Long.MIN_VALUE)

    private fun setWatermark(context: Context, stamp: Long) {
        val o = runCatching { JSONObject(markFile(context).readText()) }.getOrDefault(JSONObject())
        markFile(context).writeText(o.put("vivi", maxOf(stamp, o.optLong("vivi", Long.MIN_VALUE))).toString())
    }

    /** The import marks, for a backup. */
    fun exportMarks(context: Context): JSONObject =
        runCatching { JSONObject(markFile(context).readText()) }.getOrDefault(JSONObject())

    /** Restores marks from a backup, keeping the later of each. */
    fun restoreMarks(context: Context, marks: JSONObject) {
        val here = exportMarks(context)
        marks.keys().forEach { k -> here.put(k, maxOf(here.optLong(k, Long.MIN_VALUE), marks.optLong(k, Long.MIN_VALUE))) }
        markFile(context).writeText(here.toString())
    }
}
