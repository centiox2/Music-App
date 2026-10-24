package com.music.bitchord.ui.glow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.music.bitchord.ui.graphics.forPixelAccess
import com.music.bitchord.ui.graphics.toImageBitmap
import com.music.bitchord.ui.player.artworkMeshColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Artwork colours, remembered across the whole app.
 *
 * BitChord's `rememberArtworkColors` starts every new screen on stock colours
 * and swaps in the real ones once the cover is decoded, so opening the player
 * or changing page flashed the wrong glow for a moment. Kept here by URL, a
 * screen that has seen a cover before starts on its colours straight away.
 */
object ArtworkPalettes {
    private val cache = object : LinkedHashMap<String, List<Color>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Color>>) = size > 200
    }

    @Synchronized fun get(url: String?): List<Color>? = url?.let { cache[key(it)] }

    @Synchronized fun put(url: String, colors: List<Color>) {
        cache[key(url)] = colors
        last = colors
    }

    /** The most recently decoded cover's colours: a better first frame than stock ones. */
    @Volatile var last: List<Color>? = null
        private set

    // The same cover is asked for at many sizes (=w120, =w544, =w1080…); one entry serves them all.
    private fun key(url: String) = url.substringBefore("=w").substringBefore("=s").substringBefore("?")
}

/**
 * The cover's four mesh colours: cached ones immediately when there are any,
 * otherwise null until the cover is decoded — never a stand-in palette.
 */
@Composable
fun rememberGlowPalette(url: String?): List<Color>? {
    val context = LocalPlatformContext.current
    var colors by remember(url) { mutableStateOf(ArtworkPalettes.get(url)) }
    LaunchedEffect(url) {
        if (url == null || colors != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(url).size(128).forPixelAccess().build()
        val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toImageBitmap()
            ?: return@LaunchedEffect
        val found = withContext(Dispatchers.Default) { artworkMeshColors(bitmap) }
        ArtworkPalettes.put(url, found)
        colors = found
    }
    return colors
}

/**
 * A surface colour for the mini player from the cover: its most populous
 * colour, pushed dark enough that white text stays readable on it.
 */
fun miniPlayerTint(colors: List<Color>?, base: Color, isDark: Boolean): Color {
    val seed = colors?.firstOrNull() ?: return base
    return if (isDark) {
        val dark = lerp(seed, Color.Black, 0.62f)
        if (dark.luminance() > 0.12f) lerp(dark, Color.Black, 0.35f) else dark
    } else {
        lerp(seed, Color.White, 0.78f)
    }
}
