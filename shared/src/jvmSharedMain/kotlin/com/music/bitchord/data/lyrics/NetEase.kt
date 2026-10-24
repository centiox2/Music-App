package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import kotlin.math.abs

/**
 * NetEase Cloud Music's public web endpoints: a very large catalogue, strongest
 * on Chinese, Japanese and Korean music. Unauthenticated: search, then fetch
 * the lyric for the closest-length match that actually has one. The public
 * endpoint gives whole lines; its word-timed `yrc` is read when it is there.
 */
object NetEase {
    private const val DURATION_TOLERANCE_MS = 6_000L

    suspend fun lyrics(title: String, artist: String, durationMs: Long): List<LyricLine>? = withContext(Dispatchers.IO) {
        val ids = search("${title.stripBrackets()} ${artist.substringBefore(",").substringBefore(" & ")}", durationMs)
        for (id in ids.take(4)) {
            val body = get("https://music.163.com/api/song/lyric?id=$id&lv=1&yv=1&tv=-1") ?: continue
            val root = runCatching { lyricsJson.parseToJsonElement(body).jsonObject }.getOrNull() ?: continue
            val yrc = root["yrc"]?.jsonObject?.get("lyric")?.jsonPrimitive?.contentOrNull
            parseYrc(yrc.orEmpty()).takeIf { it.size >= 3 }?.let { return@withContext it }
            val lrc = root["lrc"]?.jsonObject?.get("lyric")?.jsonPrimitive?.contentOrNull.orEmpty()
            val lines = LrcLib.parseLrc(lrc.stripCredits()).filter { it.text.isNotBlank() || it.isGap }
            if (lines.count { it.text.isNotBlank() } >= 3) return@withContext lines
        }
        null
    }

    private fun search(query: String, durationMs: Long): List<Long> {
        val url = "https://music.163.com/api/search/get".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("type", "1")
            .addQueryParameter("limit", "10")
            .build()
        val body = get(url.toString()) ?: return emptyList()
        val songs = runCatching {
            lyricsJson.parseToJsonElement(body).jsonObject["result"]?.jsonObject?.get("songs")?.jsonArray
        }.getOrNull() ?: return emptyList()
        return songs.mapNotNull { element ->
            val song = element.jsonObject
            val id = song["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val length = song["duration"]?.jsonPrimitive?.longOrNull ?: 0L
            id to length
        }
            // A different cut of the same title is the usual wrong answer; length rules it out.
            .filter { (_, length) -> durationMs <= 0 || length <= 0 || abs(length - durationMs) <= DURATION_TOLERANCE_MS }
            .sortedBy { (_, length) -> if (durationMs > 0 && length > 0) abs(length - durationMs) else 0 }
            .map { it.first }
    }

    /**
     * `[lineStart,lineDuration](wordStart,wordDuration,0)word(…)word…`, one
     * line per row; rows starting with `{` are credits in JSON and skipped.
     */
    internal fun parseYrc(text: String): List<LyricLine> {
        val lineHeader = Regex("""^\[(\d+),(\d+)]""")
        val word = Regex("""\((\d+),(\d+),\d+\)([^(]*)""")
        return text.lineSequence().mapNotNull { row ->
            val header = lineHeader.find(row) ?: return@mapNotNull null
            val start = header.groupValues[1].toLong()
            val duration = header.groupValues[2].toLong()
            val words = word.findAll(row.substring(header.range.last + 1)).mapNotNull { m ->
                val ws = m.groupValues[1].toLong()
                val wd = m.groupValues[2].toLong()
                val t = m.groupValues[3]
                if (t.isEmpty()) null else Triple(ws, ws + wd, t)
            }.toList()
            if (words.isEmpty()) return@mapNotNull null
            val lineText = words.joinToString("") { it.third }
            if (lineText.isBlank() || CREDIT.containsMatchIn(lineText)) return@mapNotNull null
            LyricLine(
                timeMs = start,
                text = lineText.trim(),
                // Words carry their own trailing spaces in yrc; the line keeps them, each word is trimmed.
                words = words.filter { it.third.isNotBlank() }.map { (s, e, t) -> LyricWord(s, e, t.trim()) },
                sungUntilMs = start + duration,
            )
        }.toList()
    }

    private val CREDIT = Regex("""^\s*(作词|作曲|编曲|制作人|混音|母带|Lyricist|Composer|Arranger|Producer)\s*[:：]""")

    private fun String.stripBrackets(): String = replace(Regex("""[(\[（].*?[)\]）]"""), "").trim().ifBlank { this }

    private fun String.stripCredits(): String = lines().filterNot { line ->
        val text = line.substringAfterLast(']')
        CREDIT.containsMatchIn(text)
    }.joinToString("\n")

    private fun get(url: String): String? = lyricsGetWithHeaders(url, mapOf("Referer" to "https://music.163.com/"))
}
