package com.music.bitchord.ui.glow

import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.artworkAt

/**
 * Every size a cover can be fetched at, best first.
 *
 * YouTube video art comes as `hqdefault.jpg`: 480×360 with black bars above
 * and below the 16:9 frame, which a square crop keeps. The HD frames
 * (`maxresdefault`, `hq720`) have no bars but don't exist for every video, so
 * they're tried in order and the next one is used when one 404s.
 */
fun artCandidates(url: String?, px: Int): List<String> {
    if (url == null) return emptyList()
    val match = YT_THUMB.find(url) ?: return listOfNotNull(url.artworkAt(px))
    val base = "https://i.ytimg.com/vi/${match.groupValues[1]}/"
    return if (px <= 200) {
        // Small rows: mqdefault is 320×180 and already 16:9, so it has no bars either.
        listOf(base + "mqdefault.jpg", base + "hqdefault.jpg")
    } else {
        listOf(base + "maxresdefault.jpg", base + "hq720.jpg", base + "sddefault.jpg", base + "hqdefault.jpg")
    }
}

private val YT_THUMB = Regex("""i\d?\.ytimg\.com/vi(?:_webp)?/([A-Za-z0-9_-]{6,})/""")

/** A cover image that steps down through [artCandidates] until one loads. */
@Composable
fun ArtImage(
    url: String?,
    px: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val candidates = remember(url, px) { artCandidates(url, px) }
    var index by remember(candidates) { mutableIntStateOf(0) }
    AsyncImage(
        model = candidates.getOrNull(index),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onError = { if (index < candidates.lastIndex) index++ },
    )
}

/**
 * A soft drop shadow under a cover — ViVi Music's `customSoftShadow`: a blurred
 * shadow layer drawn behind the shape, softer than an elevation shadow.
 * [weight] scales it for smaller covers; the strength comes from Settings.
 */
fun Modifier.coverShadow(cornerRadius: Dp, weight: Float = 1f): Modifier = composed {
    val strength by GlowPrefs.coverShadow.collectAsStateWithLifecycle()
    val elevation = 22.dp * strength * weight
    if (elevation <= 0.5.dp) return@composed this
    val dark = Glow.colors.isDark
    drawBehind {
        val shadow = Color.Black.copy(alpha = if (dark) 0.6f else 0.32f).toArgb()
        val paint = Paint().apply {
            isAntiAlias = true
            color = shadow
            setShadowLayer(elevation.toPx() * 2.2f, 0f, elevation.toPx() * 0.4f, shadow)
        }
        drawIntoCanvas { canvas ->
            val r = cornerRadius.toPx()
            val native = canvas.nativeCanvas
            // Only the shadow, never the shape casting it: a filled shape under the cover
            // showed through as a dark square whenever the cover faded (switching to lyrics
            // or the queue) or hadn't loaded yet.
            val outline = android.graphics.Path().apply {
                addRoundRect(0f, 0f, size.width, size.height, r, r, android.graphics.Path.Direction.CW)
            }
            native.save()
            native.clipOutPath(outline)
            native.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
            native.restore()
        }
    }
}
