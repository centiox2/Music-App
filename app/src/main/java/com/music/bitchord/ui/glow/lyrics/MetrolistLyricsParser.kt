/*
 * Metrolist's lyrics parser, ported as-is from LyricsUtils
 * (https://github.com/metrolistgroup/metrolist, GPL-3.0). Every lyric shown in Sonder goes
 * through it, so the timing the animation sees is exactly Metrolist's. Only the class names
 * and the entry type's package differ.
 */
package com.music.bitchord.ui.glow.lyrics

import java.util.Locale

private const val MINUTE_IN_MILLIS = 60_000L
private const val SECOND_IN_MILLIS = 1_000L

private val LINE_REGEX = "((\\[\\d\\d:\\d\\d\\.\\d{2,3}\\] ?)+)(.*)".toRegex()
private val TIME_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\]".toRegex()

// Regex for rich sync format: [MM:SS.mm]<MM:SS.mm> word <MM:SS.mm> word ...
private val RICH_SYNC_LINE_REGEX = "\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\](.*)".toRegex()
private val RICH_SYNC_WORD_REGEX = "<(\\d{1,2}):(\\d{2})\\.(\\d{2,3})>([^<]+)".toRegex()

// Regex for Paxsenix v1/v2/bg format
// [00:00.000]v1: <00:00.000>I <00:00.154>promise...
// [bg: <02:18.078>Yeah<02:19.341>]
private val PAXSENIX_AGENT_LINE_REGEX = "\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\](v\\d+):\\s*(.*)".toRegex()
private val PAXSENIX_BG_LINE_REGEX = "^\\[bg:\\s*(.*)\\]$".toRegex()

// Regex for agent and background markers (existing format)
private val AGENT_REGEX = "\\{agent:([^}]+)\\}".toRegex()
private val BACKGROUND_REGEX = "^\\{bg\\}".toRegex()

@Suppress("RegExpRedundantEscape")
object MetrolistLyrics {
    fun cleanTitleForSearch(title: String): String {
        return title.replace(Regex("\\s*[(\\[].*?[)\\]]"), "").trim()
    }

    fun filterLyricsCreditLines(lyrics: String): String {
        return lyrics.lines().filter { line ->
            // Strip leading bracketed/braced content, version tags, and timestamps
            // Handles [00:00.00], {agent:v1}, {bg}, [bg: ...], v1: etc.
            var textContent = line.trim()
            
            // Repeatedly strip prefixes while they match common patterns
            var stripping = true
            while (stripping) {
                val prevLength = textContent.length
                textContent = textContent
                    .replaceFirst(Regex("^\\[\\d\\d:\\d\\d\\.\\d{2,3}\\]"), "")
                    .replaceFirst(Regex("^\\{agent:[^}]+\\}"), "")
                    .replaceFirst(Regex("^\\{bg\\}"), "")
                    .replaceFirst(Regex("^\\[bg:.*\\]"), "")
                    .replaceFirst(Regex("^v\\d+:"), "")
                    .trim()
                stripping = textContent.length < prevLength
            }

            val lowerText = textContent.lowercase(Locale.getDefault())
            
            val isCredit = lowerText.startsWith("synced by") ||
                    lowerText.startsWith("lyrics by") ||
                    lowerText.startsWith("music by") ||
                    lowerText.startsWith("arranged by") ||
                    (lowerText.startsWith("[") && lowerText.endsWith("]") && lowerText.length < 40 && lowerText.contains("synced by"))
            
            !isCredit
        }.joinToString("\n")
    }

    private val HEX_ENTITY_REGEX = "&#x([0-9a-fA-F]+);".toRegex()
    private val DEC_ENTITY_REGEX = "&#(\\d+);".toRegex()

    private fun decodeHtmlEntities(text: String): String {
        if (!text.contains('&')) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '&') {
                val end = text.indexOf(';', i + 1)
                if (end != -1 && end - i < 12) {
                    val entity = text.substring(i, end + 1)
                    val decoded = when {
                        entity == "&apos;" -> "'"
                        entity == "&quot;" -> "\""
                        entity == "&lt;" -> "<"
                        entity == "&gt;" -> ">"
                        entity == "&nbsp;" -> " "
                        entity == "&amp;" -> "&"
                        entity.startsWith("&#x") -> {
                            entity.substring(3, entity.length - 1).toIntOrNull(16)?.let { codePoint ->
                                if (Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else "\uFFFD"
                            }
                        }
                        entity.startsWith("&#") -> {
                            entity.substring(2, entity.length - 1).toIntOrNull()?.let { codePoint ->
                                if (Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else "\uFFFD"
                            }
                        }
                        else -> null
                    }
                    if (decoded != null) {
                        sb.append(decoded)
                        i = end + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    fun parseLyrics(lyrics: String): List<LyricsEntry> {
        if (lyrics.isBlank()) return emptyList()

        // Fast unescape
        val unescapedLyrics = if (lyrics.contains('\\') || lyrics.startsWith("\"")) {
            val s = lyrics.trim().removePrefix("\"").removeSuffix("\"")
            val sb = StringBuilder(s.length)
            var j = 0
            while (j < s.length) {
                val c = s[j]
                if (c == '\\' && j + 1 < s.length) {
                    when (val next = s[j + 1]) {
                        '\\' -> sb.append('\\')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        else -> sb.append(c).append(next)
                    }
                    j += 2
                } else {
                    sb.append(c)
                    j++
                }
            }
            sb.toString()
        } else lyrics

        val decodedLyrics = decodeHtmlEntities(unescapedLyrics)

        val lines = decodedLyrics.lines()
            .filter { 
                it.isNotBlank() || it.trim().startsWith("[") || it.trim().startsWith("<")
            }
            .filter { !it.trim().startsWith("[offset:") }

        // Check if this is rich sync format (contains <MM:SS.mm> patterns)
        val isRichSync = lines.any { line ->
            RICH_SYNC_LINE_REGEX.matches(line.trim()) &&
            RICH_SYNC_WORD_REGEX.containsMatchIn(line)
        }

        return if (isRichSync) {
            parseRichSyncLyrics(lines)
        } else {
            parseStandardLyrics(lines)
        }
    }

    /**
     * Parse rich sync lyrics format: [MM:SS.mm]<MM:SS.mm> word <MM:SS.mm> word ...
     * This format provides word-by-word timing for karaoke-style highlighting
     */
    private fun parseRichSyncLyrics(lines: List<String>): List<LyricsEntry> {
        val result = mutableListOf<LyricsEntry>()
        var lastNonBgAgent: String? = null

        lines.forEachIndexed { index, line ->
            val trimmedLine = line.trim()
            
            // Try Paxsenix bg format first: [bg: <02:18.078>Yeah<02:19.341>]
            val bgMatch = PAXSENIX_BG_LINE_REGEX.find(trimmedLine)
            if (bgMatch != null) {
                val content = bgMatch.groupValues[1]
                
                // Parse word-level timestamps from content
                val wordTimings = parseRichSyncWords(content, index, lines)
                    ?: run {
                        val nextLine = lines.getOrNull(index + 1)?.trim() ?: ""
                        if (nextLine.startsWith("<") && nextLine.endsWith(">")) {
                            parseWordTimestamps(nextLine.removeSurrounding("<", ">"))
                        } else null
                    }
                
                // Extract plain text (remove all <MM:SS.mm> tags)
                val plainText = content.replace(Regex("<\\d{1,2}:\\d{2}\\.\\d{2,3}>\\s*"), "").trim()
                
                val lineTimeMs = wordTimings?.firstOrNull()?.startTime?.let { (it * 1000).toLong() } ?: 0L
                result.add(LyricsEntry(lineTimeMs, plainText, wordTimings, agent = lastNonBgAgent ?: "bg", isBackground = true))
                return@forEachIndexed
            }
            
            // Try Paxsenix agent format: [00:00.000]v1: <00:00.000>I <00:00.154>promise...
            val agentMatch = PAXSENIX_AGENT_LINE_REGEX.find(trimmedLine)
            if (agentMatch != null) {
                val minutes = agentMatch.groupValues[1].toLongOrNull() ?: 0L
                val seconds = agentMatch.groupValues[2].toLongOrNull() ?: 0L
                val centiseconds = agentMatch.groupValues[3].toLongOrNull() ?: 0L
                val agent = agentMatch.groupValues[4] // v1, v2, etc.
                val content = agentMatch.groupValues[5]
                
                val millisPart = if (agentMatch.groupValues[3].length == 3) centiseconds else centiseconds * 10
                val lineTimeMs = minutes * MINUTE_IN_MILLIS + seconds * SECOND_IN_MILLIS + millisPart
                
                // Parse word-level timestamps from content
                val wordTimings = parseRichSyncWords(content, index, lines)
                    ?: run {
                        val nextLine = lines.getOrNull(index + 1)?.trim() ?: ""
                        if (nextLine.startsWith("<") && nextLine.endsWith(">")) {
                            parseWordTimestamps(nextLine.removeSurrounding("<", ">"))
                        } else null
                    }
                
                // Extract plain text (remove all <MM:SS.mm> tags)
                val plainText = content.replace(Regex("<\\d{1,2}:\\d{2}\\.\\d{2,3}>\\s*"), "").trim()
                
                if (!agent.isNullOrBlank()) {
                    lastNonBgAgent = agent
                }
                result.add(LyricsEntry(lineTimeMs, plainText, wordTimings, agent = agent, isBackground = false))
                return@forEachIndexed
            }
            
            // Try existing format: [MM:SS.mm]{agent:v1}... or [MM:SS.mm]{bg}...
            val matchResult = RICH_SYNC_LINE_REGEX.matchEntire(trimmedLine)
            if (matchResult != null) {
                val minutes = matchResult.groupValues[1].toLongOrNull() ?: 0L
                val seconds = matchResult.groupValues[2].toLongOrNull() ?: 0L
                val centiseconds = matchResult.groupValues[3].toLongOrNull() ?: 0L

                // Convert to milliseconds
                val millisPart = if (matchResult.groupValues[3].length == 3) centiseconds else centiseconds * 10
                val lineTimeMs = minutes * MINUTE_IN_MILLIS + seconds * SECOND_IN_MILLIS + millisPart

                var content = matchResult.groupValues[4].trimStart()

                // Parse agent marker {agent:v1}
                val oldAgentMatch = AGENT_REGEX.find(content)
                val agent = oldAgentMatch?.groupValues?.get(1)
                if (oldAgentMatch != null) {
                    content = content.replaceFirst(AGENT_REGEX, "")
                }

                // Parse background marker {bg}
                val isBackground = BACKGROUND_REGEX.containsMatchIn(content)
                if (isBackground) {
                    content = content.replaceFirst(BACKGROUND_REGEX, "")
                }

                // Parse word-level timestamps from content
                val wordTimings = parseRichSyncWords(content, index, lines)
                    ?: run {
                        val nextLine = lines.getOrNull(index + 1)?.trim() ?: ""
                        if (nextLine.startsWith("<") && nextLine.endsWith(">")) {
                            parseWordTimestamps(nextLine.removeSurrounding("<", ">"))
                        } else null
                    }

                // Extract plain text (remove all <MM:SS.mm> tags)
                val plainText = content.replace(Regex("<\\d{1,2}:\\d{2}\\.\\d{2,3}>\\s*"), "").trim()

                if (!isBackground && !agent.isNullOrBlank()) {
                    lastNonBgAgent = agent
                }
                result.add(LyricsEntry(lineTimeMs, plainText, wordTimings, agent = if (isBackground) lastNonBgAgent ?: "bg" else agent, isBackground = isBackground))
            }
        }

        return result.sorted()
    }

    /**
     * Parse word timestamps from rich sync content
     * Format: <MM:SS.mm> word <MM:SS.mm> word ...
     */
    private fun parseRichSyncWords(content: String, currentIndex: Int, allLines: List<String>): List<WordTimestamp>? {
        val wordMatches = RICH_SYNC_WORD_REGEX.findAll(content).toList()

        if (wordMatches.isEmpty()) return null

        // Check for a trailing end timestamp after the last word.
        // The provider uses two formats:
        //   - Angle brackets: <MM:SS.mmm> (used in v1:/v2: prefixed lines)
        //   - Square brackets: [MM:SS.xx] (used in non-prefixed lines)
        val lastMatchEnd = wordMatches.last().range.last
        val trailingContent = content.substring(lastMatchEnd + 1).trim()
        val angleTrailingMatch = "<(\\d{1,2}):(\\d{2})\\.(\\d{2,3})>".toRegex().find(trailingContent)
        val squareTrailingMatch = "\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\]".toRegex().find(trailingContent)
        val trailingTimeMatch = angleTrailingMatch ?: squareTrailingMatch
        val trailingEndTime: Double? = if (trailingTimeMatch != null && trailingContent.substring(trailingTimeMatch.range.last + 1).removeSuffix("]").isBlank()) {
            val tMin = trailingTimeMatch.groupValues[1].toLongOrNull() ?: 0L
            val tSec = trailingTimeMatch.groupValues[2].toLongOrNull() ?: 0L
            val tFrac = trailingTimeMatch.groupValues[3].toLongOrNull() ?: 0L
            val tFracPart = if (trailingTimeMatch.groupValues[3].length == 3) tFrac / 1000.0 else tFrac / 100.0
            tMin * 60.0 + tSec + tFracPart
        } else null

        val wordTimings = mutableListOf<WordTimestamp>()

        wordMatches.forEachIndexed { index, match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fraction = match.groupValues[3].toLongOrNull() ?: 0L

            val fractionPart = if (match.groupValues[3].length == 3) fraction / 1000.0 else fraction / 100.0
            val startTimeSeconds = minutes * 60.0 + seconds + fractionPart

            val rawText = match.groupValues[4]
            val hasTrailingSpace = rawText.endsWith(" ")
            val words = rawText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

            // Get the next timestamp for end time calculation
            val nextTimestamp: Double
            val nextLineTime: Double?

            if (index < wordMatches.size - 1) {
                val nextMatch = wordMatches[index + 1]
                val nextMin = nextMatch.groupValues[1].toLongOrNull() ?: 0L
                val nextSec = nextMatch.groupValues[2].toLongOrNull() ?: 0L
                val nextFrac = nextMatch.groupValues[3].toLongOrNull() ?: 0L
                val nextFracPart = if (nextMatch.groupValues[3].length == 3) nextFrac / 1000.0 else nextFrac / 100.0
                nextTimestamp = nextMin * 60.0 + nextSec + nextFracPart
                nextLineTime = null
            } else {
                nextLineTime = getNextLineStartTime(currentIndex, allLines)
                nextTimestamp = trailingEndTime ?: nextLineTime ?: (startTimeSeconds + 0.5)
            }

            words.forEachIndexed { wordIndex, word ->
                val isLastWordInGroup = wordIndex == words.lastIndex
                val isLastWordOverall = index == wordMatches.lastIndex && isLastWordInGroup

                val wordStartTime = startTimeSeconds + (nextTimestamp - startTimeSeconds) * wordIndex / words.size
                val wordEndTime = if (!isLastWordInGroup) {
                    startTimeSeconds + (nextTimestamp - startTimeSeconds) * (wordIndex + 1) / words.size
                } else if (!isLastWordOverall) {
                    nextTimestamp
                } else {
                    trailingEndTime ?: nextLineTime ?: (startTimeSeconds + 0.5)
                }

                val wordHasTrailingSpace = if (!isLastWordInGroup) {
                    true
                } else if (!isLastWordOverall) {
                    hasTrailingSpace
                } else {
                    // Last word of last match - check if there's text after it (excluding our optional trailing timestamp)
                    val textAfterMatch = if (trailingTimeMatch != null) {
                        trailingContent.substring(0, trailingTimeMatch.range.first)
                    } else {
                        trailingContent
                    }
                    textAfterMatch.isNotBlank()
                }

                if (word.isNotBlank()) {
                    wordTimings.add(WordTimestamp(word, wordStartTime, wordEndTime, wordHasTrailingSpace))
                }
            }
        }

        return if (wordTimings.isNotEmpty()) wordTimings else null
    }

    /**
     * Get the start time of the next line for calculating the last word's end time
     */
    private fun getNextLineStartTime(currentIndex: Int, allLines: List<String>): Double? {
        // Background vocals overlap the line they accompany rather than following it,
        // so a background line does not mark where the previous line ends. Using one
        // cut the last word short, and when the background started before that word
        // did, the word was handed an end time earlier than its own start.
        var nextIndex = currentIndex + 1
        while (nextIndex < allLines.size && isBackgroundLine(allLines[nextIndex].trim())) {
            nextIndex++
            // A background line without inline timestamps carries its word timings on
            // the following standalone <word:start:end> line. That continuation belongs
            // to the background, so it has to be stepped over as well.
            if (nextIndex < allLines.size && isTimingContinuationLine(allLines[nextIndex].trim())) {
                nextIndex++
            }
        }
        if (nextIndex >= allLines.size) return null

        val nextLine = allLines[nextIndex].trim()

        val matchResult = RICH_SYNC_LINE_REGEX.matchEntire(nextLine) ?: return null
        val minutes = matchResult.groupValues[1].toLongOrNull() ?: return null
        val seconds = matchResult.groupValues[2].toLongOrNull() ?: return null
        val fraction = matchResult.groupValues[3].toLongOrNull() ?: 0L

        val fractionPart = if (matchResult.groupValues[3].length == 3) fraction / 1000.0 else fraction / 100.0
        return minutes * 60.0 + seconds + fractionPart
    }

    /**
     * Both background spellings the parser accepts: a whole `[bg: ...]` line, and a
     * timestamped line whose content begins with `{bg}`.
     */
    private fun isBackgroundLine(line: String): Boolean {
        if (PAXSENIX_BG_LINE_REGEX.matches(line)) return true
        val content = RICH_SYNC_LINE_REGEX.matchEntire(line)?.groupValues?.get(4) ?: return false
        return BACKGROUND_REGEX.containsMatchIn(content.trim())
    }

    /**
     * A standalone `<word:start:end|...>` line holding the previous line's word
     * timings, matching the continuation form [parseRichSyncLyrics] falls back to.
     */
    private fun isTimingContinuationLine(line: String): Boolean =
        line.startsWith("<") && line.endsWith(">")

    /**
     * Parse standard synced lyrics format: [MM:SS.mm] text
     */
    private fun parseStandardLyrics(lines: List<String>): List<LyricsEntry> {
        val result = mutableListOf<LyricsEntry>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!line.trim().startsWith("<") || !line.trim().endsWith(">")) {
                val entries = parseLine(line, null)
                if (entries != null) {
                    val wordTimestamps = if (i + 1 < lines.size) {
                        val nextLine = lines[i + 1]
                        if (nextLine.trim().startsWith("<") && nextLine.trim().endsWith(">")) {
                            parseWordTimestamps(nextLine.trim().removeSurrounding("<", ">"))
                        } else null
                    } else null

                    if (wordTimestamps != null) {
                        result.addAll(entries.map { entry ->
                            LyricsEntry(entry.time, entry.text, wordTimestamps, agent = entry.agent, isBackground = entry.isBackground)
                        })
                    } else {
                        result.addAll(entries)
                    }
                }
            }
            i++
        }
        return result.sorted()
    }

    private fun parseWordTimestamps(data: String): List<WordTimestamp>? {
        if (data.isBlank()) return null
        return try {
            data.split("|").mapNotNull { wordData ->
                val parts = wordData.split(":")
                if (parts.size >= 3) {
                    val text = parts.dropLast(2).joinToString(":")
                    val startTime = parts[parts.size - 2].toDoubleOrNull() ?: 0.0
                    val endTime = parts[parts.size - 1].toDoubleOrNull() ?: 0.0
                    val isLast = wordData == data.split("|").last()
                    WordTimestamp(
                        text = text,
                        startTime = startTime,
                        endTime = endTime,
                        hasTrailingSpace = !isLast
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseLine(line: String, words: List<WordTimestamp>? = null): List<LyricsEntry>? {
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = matchResult.groupValues[1]
        var text = matchResult.groupValues[3]
        val timeMatchResults = TIME_REGEX.findAll(times)

        // Parse agent marker {agent:v1}
        val agentMatch = AGENT_REGEX.find(text)
        val agent = agentMatch?.groupValues?.get(1)
        if (agentMatch != null) {
            text = text.replaceFirst(AGENT_REGEX, "")
        }

        // Parse background marker {bg}
        val isBackground = BACKGROUND_REGEX.containsMatchIn(text)
        if (isBackground) {
            text = text.replaceFirst(BACKGROUND_REGEX, "")
        }

        return timeMatchResults
            .map { timeMatchResult ->
                val min = timeMatchResult.groupValues[1].toLong()
                val sec = timeMatchResult.groupValues[2].toLong()
                val milString = timeMatchResult.groupValues[3]
                var mil = milString.toLong()
                if (milString.length == 2) {
                    mil *= 10
                }
                val time = min * MINUTE_IN_MILLIS + sec * SECOND_IN_MILLIS + mil
                LyricsEntry(time, text, words, agent = agent, isBackground = isBackground)
            }.toList()
    }

}
