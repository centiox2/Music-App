package com.music.bitchord.data

/**
 * The title and artist to look lyrics up by.
 *
 * Bracketed parts of the title go, as Metrolist does (`(Official Video)`, `[Lyrics]`, `(feat. …)`).
 * A music video's "artist" is usually the channel that uploaded it ("DrakeVEVO", "Drake - Topic",
 * "Some Label Records"); its title usually names the real artist ("Drake - Hotline Bling"). For
 * those, the artist is taken from the title when it has an "Artist - Title" shape, and channel
 * suffixes are dropped otherwise.
 */
data class LyricsQuery(val title: String, val artist: String) {
    companion object {
        private val BRACKETS = Regex("\\s*[(\\[【].*?[)\\]】]")
        private val DASH = Regex("\\s+[-–—|]\\s+")
        private val CHANNEL_SUFFIX = Regex(
            "(?i)\\s*(-\\s*topic|vevo|official( music| channel)?|music|records|tv)\\s*$",
        )
        private val VIDEO_WORDS = Regex(
            "(?i)\\b(official (music |lyric |audio )?video|official audio|lyric video|lyrics?|visuali[sz]er|audio|hd|4k|mv)\\b",
        )

        fun of(title: String, artist: String, isVideo: Boolean): LyricsQuery {
            val cleanTitle = title.replace(BRACKETS, "").replace(VIDEO_WORDS, "").trim().trimEnd('-', '|', ' ')
            if (!isVideo && !looksLikeChannel(artist)) return LyricsQuery(cleanTitle.ifBlank { title }, artist.trim())
            val parts = cleanTitle.split(DASH, limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                return LyricsQuery(parts[1].trim(), parts[0].trim())
            }
            return LyricsQuery(cleanTitle.ifBlank { title }, channelName(artist))
        }

        /** "DrakeVEVO" → "Drake", "Drake - Topic" → "Drake". */
        fun channelName(artist: String): String {
            var name = artist.trim()
            repeat(2) { name = name.replace(CHANNEL_SUFFIX, "").trim() }
            return name.ifBlank { artist.trim() }
        }

        private val SURE_CHANNEL = Regex("(?i)(vevo|-\\s*topic)\\s*$")

        // Only the unmistakable markers; "… Music" can be a real artist's name.
        private fun looksLikeChannel(artist: String): Boolean = SURE_CHANNEL.containsMatchIn(artist.trim())
    }
}
