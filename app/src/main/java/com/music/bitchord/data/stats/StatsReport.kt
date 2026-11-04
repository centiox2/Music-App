package com.music.bitchord.data.stats

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** The spans the Statistics page offers. */
enum class StatsPeriod(val label: String, val days: Long?) {
    WEEK("7 days", 7),
    MONTH("30 days", 30),
    HALF_YEAR("6 months", 182),
    LIFETIME("Lifetime", null),
    ;

    /** The first day this period covers, or null for all of it. */
    fun start(today: LocalDate): LocalDate? = days?.let { today.minusDays(it - 1) }
}

/**
 * Everything the Statistics page draws, for one period. Worked out from [ListeningStats]'s
 * monthly buckets: totals and charts from the per-day counts inside each entry, the hour chart
 * from the months the period touches (hours aren't kept per day).
 */
data class StatsReport(
    val period: StatsPeriod,
    val totalMs: Long,
    val plays: Int,
    val tracks: Int,
    val albums: Int,
    val artists: Int,
    val songs: List<RankedSong>,
    val topAlbums: List<RankedEntry>,
    val topArtists: List<RankedEntry>,
    /** Milliseconds per hour of the day, 0..23. */
    val hourMs: List<Long>,
    /** Plays per hour of the day; all zero for listening recorded before these were counted. */
    val hourPlays: List<Int>,
    /** Plays per day, every day with any listening (not only the period's). */
    val dailyPlays: Map<LocalDate, Int>,
    /** Milliseconds per day, every day with any listening. */
    val dailyMs: Map<LocalDate, Long>,
    val busiestWeekday: DayOfWeek?,
    val longestStreak: Int,
    /** How many days the period spans (for lifetime: since the first day with listening). */
    val spanDays: Long,
) {
    val isEmpty: Boolean get() = totalMs <= 0L && songs.isEmpty()
    val perDayMs: Long get() = if (spanDays > 0) totalMs / spanDays else 0L
}

/** Builds a [StatsReport] for [period], off the main thread. */
suspend fun ListeningStats.report(period: StatsPeriod, today: LocalDate = LocalDate.now()): StatsReport =
    withContext(Dispatchers.IO) {
        val buckets = exportAll()
        val start = period.start(today)
        val range = (start ?: LocalDate.MIN) to today
        fun inRange(d: LocalDate) = (start == null || !d.isBefore(start)) && !d.isAfter(today)

        val dailyMs = HashMap<LocalDate, Long>()
        val dailyPlays = HashMap<LocalDate, Int>()
        val hourMs = LongArray(24)
        val hourPlays = IntArray(24)
        val tracks = HashMap<String, TrackEntry>()
        val artists = HashMap<String, NameEntry>()
        val albums = HashMap<String, NameEntry>()

        buckets.forEach { bucket ->
            val month = runCatching { YearMonth.parse(bucket.month) }.getOrNull() ?: return@forEach
            bucket.days.forEach { (day, ms) ->
                runCatching { month.atDay(day) }.getOrNull()?.let { dailyMs.merge(it, ms) { a, b -> a + b } }
            }
            bucket.trackDays?.forEach { (key, stats) ->
                runCatching { LocalDate.parse(key) }.getOrNull()?.let { dailyPlays.merge(it, stats.plays) { a, b -> a + b } }
            }
            val touches = start == null || !month.atEndOfMonth().isBefore(start)
            if (!touches) return@forEach
            repeat(24) {
                hourMs[it] += bucket.hours.getOrElse(it) { 0L }
                hourPlays[it] += bucket.hourPlays.getOrElse(it) { 0 }
            }
            bucket.tracks.forEach { entry ->
                tracks.merge(entry.id, entry.copy(days = entry.days.toMutableMap())) { a, b -> a.also { it.absorb(b) } }
            }
            bucket.artists.forEach { entry ->
                val name = ListeningStats.primaryArtist(entry.name) ?: entry.name
                artists.merge(name.lowercase(Locale.ROOT), entry.copy(name = name, days = entry.days.toMutableMap())) { a, b -> a.also { it.absorb(b) } }
            }
            bucket.albums.forEach { entry ->
                // By album id where there is one, as the monthly buckets file them.
                val key = entry.id ?: (entry.name.lowercase(Locale.ROOT) + "\u0001" + entry.sub.orEmpty().lowercase(Locale.ROOT))
                albums.merge(key, entry.copy(days = entry.days.toMutableMap())) { a, b -> a.also { it.absorb(b) } }
            }
        }

        // An entry's share of the period: its per-day counts inside it, or everything for lifetime.
        fun TrackEntry.within(): Pair<Long, Int> = if (start == null) ms to plays else weekRange(range)
        fun NameEntry.within(): Pair<Long, Int> = if (start == null) ms to plays else weekRange(range)

        val songs = tracks.values.map { it to it.within() }
            .filter { it.second.first > 0 || it.second.second > 0 }
            .sortedWith(compareByDescending<Pair<TrackEntry, Pair<Long, Int>>> { it.second.second }.thenByDescending { it.second.first })
            .map { (e, mp) ->
                RankedSong(
                    Song(videoId = e.id, title = e.title, artist = e.artist, thumbnailUrl = e.art, artistId = e.artistId, albumId = e.albumId, albumName = e.album),
                    ms = mp.first,
                    plays = mp.second,
                )
            }
        val rankedArtists = artists.values.map { it to it.within() }
            .filter { it.second.first > 0 }
            .sortedWith(compareByDescending<Pair<NameEntry, Pair<Long, Int>>> { it.second.first }.thenByDescending { it.second.second })
            .map { (e, mp) ->
                RankedEntry(
                    title = e.name,
                    subtitle = null,
                    artworkUrl = ArtistFacts.imageFor(e.name) ?: e.art,
                    browseId = e.id ?: ArtistFacts.browseIdFor(e.name),
                    ms = mp.first,
                    plays = mp.second,
                )
            }
        rankedArtists.take(15).filter { it.artworkUrl == null || it.browseId == null }.forEach { ArtistFacts.noticed(it.title) }
        val rankedAlbums = albums.values.map { it to it.within() }
            .filter { it.second.first > 0 }
            .sortedWith(compareByDescending<Pair<NameEntry, Pair<Long, Int>>> { it.second.first }.thenByDescending { it.second.second })
            .map { (e, mp) -> RankedEntry(e.name, e.sub, e.art, e.id, mp.first, mp.second) }

        val periodDays = dailyMs.filterKeys(::inRange)
        val totalMs = periodDays.values.sum().takeIf { it > 0 } ?: songs.sumOf { it.ms }
        val busiest = periodDays.entries.groupBy({ it.key.dayOfWeek }, { it.value })
            .mapValues { it.value.sum() }
            .maxByOrNull { it.value }
            ?.takeIf { it.value > 0 }
            ?.key
        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        periodDays.filterValues { it > 0 }.keys.sorted().forEach { day ->
            run = if (previous != null && previous!!.plusDays(1) == day) run + 1 else 1
            longest = maxOf(longest, run)
            previous = day
        }
        val first = dailyMs.keys.minOrNull()
        val span = when {
            period.days != null -> period.days
            first != null -> java.time.temporal.ChronoUnit.DAYS.between(first, today) + 1
            else -> 0L
        }

        StatsReport(
            period = period,
            totalMs = totalMs,
            plays = songs.sumOf { it.plays },
            tracks = songs.size,
            albums = rankedAlbums.size,
            artists = rankedArtists.size,
            songs = songs,
            topAlbums = rankedAlbums,
            topArtists = rankedArtists,
            hourMs = hourMs.toList(),
            hourPlays = hourPlays.toList(),
            dailyPlays = dailyPlays,
            dailyMs = dailyMs,
            busiestWeekday = busiest,
            longestStreak = longest,
            spanDays = span,
        )
    }
