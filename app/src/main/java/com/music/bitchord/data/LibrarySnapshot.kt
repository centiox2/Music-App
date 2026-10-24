package com.music.bitchord.data

import android.content.Context
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The last Library read, kept on disk per account so the tab opens on it
 * straight away — reading every page of saved songs and playlists again
 * took long enough to matter when Library is the start screen — while a
 * fresh read replaces it behind the scenes.
 */
object LibrarySnapshot {
    private fun file(context: Context, account: String?) =
        File(context.filesDir, "library_snapshot_${(account ?: "default").hashCode().toUInt()}.json")

    fun load(context: Context, account: String?): LibraryPage? = runCatching {
        val f = file(context, account).takeIf { it.exists() } ?: return null
        val o = JSONObject(f.readText())
        LibraryPage(
            likedSongs = o.getJSONArray("liked").songs(),
            librarySongs = o.getJSONArray("songs").songs(),
            shelves = o.getJSONArray("shelves").let { a ->
                (0 until a.length()).map { i ->
                    val s = a.getJSONObject(i)
                    HomeShelf(s.getString("title"), s.getJSONArray("items").let { items -> (0 until items.length()).map { items.getJSONObject(it).item() } })
                }
            },
        )
    }.getOrNull()

    fun save(context: Context, account: String?, page: LibraryPage) {
        runCatching {
            val o = JSONObject()
                .put("liked", JSONArray(page.likedSongs.map { it.json() }))
                .put("songs", JSONArray(page.librarySongs.map { it.json() }))
                .put(
                    "shelves",
                    JSONArray(page.shelves.map { s -> JSONObject().put("title", s.title).put("items", JSONArray(s.items.map { it.json() })) }),
                )
            file(context, account).writeText(o.toString())
        }
    }

    fun clear(context: Context) {
        context.filesDir.listFiles { f -> f.name.startsWith("library_snapshot_") }?.forEach { it.delete() }
    }

    private fun Song.json() = JSONObject()
        .put("v", videoId).put("t", title).put("a", artist).putOpt("th", thumbnailUrl).putOpt("d", durationText)
        .putOpt("ai", artistId).putOpt("al", albumId).putOpt("an", albumName).put("vid", isVideo).putOpt("sv", setVideoId)
        .put("ar", JSONArray(artists.map { JSONObject().put("n", it.name).putOpt("b", it.browseId) }))

    private fun JSONArray.songs() = (0 until length()).map { i ->
        val o = getJSONObject(i)
        Song(
            videoId = o.getString("v"),
            title = o.getString("t"),
            artist = o.getString("a"),
            thumbnailUrl = o.optString("th").ifEmpty { null },
            durationText = o.optString("d").ifEmpty { null },
            artistId = o.optString("ai").ifEmpty { null },
            artists = o.optJSONArray("ar")?.let { a -> (0 until a.length()).map { ArtistRef(a.getJSONObject(it).getString("n"), a.getJSONObject(it).optString("b").ifEmpty { null }) } }.orEmpty(),
            albumId = o.optString("al").ifEmpty { null },
            albumName = o.optString("an").ifEmpty { null },
            isVideo = o.optBoolean("vid"),
            setVideoId = o.optString("sv").ifEmpty { null },
        )
    }

    private fun ShelfItem.json() = JSONObject()
        .put("t", title).put("s", subtitle).putOpt("th", thumbnailUrl).putOpt("v", videoId).putOpt("b", browseId)

    private fun JSONObject.item() = ShelfItem(
        title = getString("t"),
        subtitle = optString("s"),
        thumbnailUrl = optString("th").ifEmpty { null },
        videoId = optString("v").ifEmpty { null },
        browseId = optString("b").ifEmpty { null },
    )
}
