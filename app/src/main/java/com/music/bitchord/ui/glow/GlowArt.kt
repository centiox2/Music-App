package com.music.bitchord.ui.glow

import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.graphics.Bitmap
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import com.music.bitchord.data.model.artworkAt

/**
 * Every size a cover can be fetched at, best first.
 *
 * YouTube video art comes in two shapes: the HD frames (`maxresdefault` 1280×720,
 * `hq720`) are the video's own 16:9 picture, while `sddefault` (640×480) and
 * `hqdefault` (480×360) are 4:3 with black bars above and below it. Not every video
 * has the HD ones, so they're tried in order and the next is used when one 404s.
 * Every size starts from the HD frames: the small 320×180 `mqdefault` that rows used
 * before is what made the same video look sharp in one place and soft in another.
 * [VideoSquareCrop] then takes the square out of whichever one arrived.
 */
fun artCandidates(url: String?, px: Int): List<String> {
    if (url == null) return emptyList()
    val id = youTubeVideoId(url) ?: return listOfNotNull(url.artworkAt(px))
    val base = "https://i.ytimg.com/vi/$id/"
    val hd = com.music.bitchord.data.PerformanceProfile.hdThumbnails.value || px > 200
    return if (hd) {
        listOf(base + "maxresdefault.jpg", base + "hq720.jpg", base + "sddefault.jpg", base + "hqdefault.jpg")
    } else {
        // "HD video thumbnails" off (Settings → Performance), for slow TVs: one smaller frame.
        listOf(base + "hq720.jpg", base + "hqdefault.jpg")
    }
}

private val YT_THUMB = Regex("""i\d?\.ytimg\.com/vi(?:_webp)?/([A-Za-z0-9_-]{6,})/""")

/** The video id when [url] is a YouTube video thumbnail (not square album art). */
fun youTubeVideoId(url: String?): String? = url?.let { YT_THUMB.find(it)?.groupValues?.get(1) }

/**
 * An image request for [data] (a URL), cropped to a clean square when it is a YouTube
 * video frame: see [VideoSquareCrop]. Anything else is left as it is.
 */
fun glowImageRequest(
    context: coil3.PlatformContext,
    data: String?,
    square: Boolean = true,
    configure: ImageRequest.Builder.() -> Unit = {},
): ImageRequest = ImageRequest.Builder(context)
    .data(data)
    .apply {
        if (square && data != null && youTubeVideoId(data) != null) transformations(VideoSquareCrop)
        // Settings → Performance → Image fade-in.
        if (!com.music.bitchord.data.PerformanceProfile.imageFade.value) crossfade(false)
        configure()
    }
    .build()

/** A cover image that steps down through [artCandidates] until one loads. */
@Composable
fun ArtImage(
    url: String?,
    px: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    /** Square the picture (video frames lose their bars and sides). False for wide cards. */
    square: Boolean = true,
) {
    val hdSetting by com.music.bitchord.data.PerformanceProfile.hdThumbnails.collectAsStateWithLifecycle()
    val fade by com.music.bitchord.data.PerformanceProfile.imageFade.collectAsStateWithLifecycle()
    val candidates = remember(url, px, hdSetting) { artCandidates(url, px) }
    var index by remember(candidates) { mutableIntStateOf(0) }
    val context = LocalPlatformContext.current
    val model = candidates.getOrNull(index)
    val request = remember(model, square, fade) { glowImageRequest(context, model, square) }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onError = { if (index < candidates.lastIndex) index++ },
    )
}

/**
 * Takes a square out of a YouTube video frame without the black bars.
 *
 * The 4:3 frames (`sddefault`, `hqdefault`) put a 16:9 video between black bars at the
 * top and bottom; a plain centre crop kept those bars in the square. Videos made from a
 * still cover often come with black bars at the sides of the 16:9 picture as well. So the
 * near-black rows and columns at the edges are measured first (only when both opposite
 * edges have them, so a dark sky isn't mistaken for a bar), and the square is the largest
 * one that fits inside the picture that's left: the full height of a 16:9 frame with its
 * sides cropped, never zoomed in past it.
 */
object VideoSquareCrop : Transformation() {
    override val cacheKey: String = "sonder.videoSquare.v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        if (w < 8 || h < 8) return input
        val bounds = pictureBounds(input)
        val side = minOf(bounds.width(), bounds.height())
        if (side < 8) return input
        val left = bounds.left + (bounds.width() - side) / 2
        val top = bounds.top + (bounds.height() - side) / 2
        if (left == 0 && top == 0 && side == w && side == h) return input
        return Bitmap.createBitmap(input, left, top, side, side)
    }

    /** The part of [bitmap] inside any black bars. */
    internal fun pictureBounds(bitmap: Bitmap): android.graphics.Rect {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        // Bars are at most this much of each edge: a 16:9 picture in a 4:3 frame leaves
        // 12.5% top and bottom, a square cover in a 16:9 frame 21.9% left and right.
        val maxRows = (h * 0.3f).toInt()
        val maxCols = (w * 0.3f).toInt()
        fun rowIsBar(y: Int): Boolean = lineIsBar(pixels, y * w, 1, w)
        fun colIsBar(x: Int): Boolean = lineIsBar(pixels, x, w, h)
        var top = 0
        while (top < maxRows && rowIsBar(top)) top++
        var bottom = 0
        while (bottom < maxRows && rowIsBar(h - 1 - bottom)) bottom++
        var left = 0
        while (left < maxCols && colIsBar(left)) left++
        var right = 0
        while (right < maxCols && colIsBar(w - 1 - right)) right++
        // Bars come in pairs. One dark edge alone is part of the picture.
        val minRows = maxOf(2, h / 50)
        val minCols = maxOf(2, w / 50)
        val trimV = if (top >= minRows && bottom >= minRows) minOf(top, bottom) else 0
        val trimH = if (left >= minCols && right >= minCols) minOf(left, right) else 0
        // A JPEG's bar edge is a soft ramp; one extra pixel keeps the dark seam out.
        val padV = if (trimV > 0) 1 else 0
        val padH = if (trimH > 0) 1 else 0
        return android.graphics.Rect(trimH + padH, trimV + padV, w - trimH - padH, h - trimV - padV)
    }

    /** Whether [count] pixels from [start], [step] apart, are all near black. */
    private fun lineIsBar(pixels: IntArray, start: Int, step: Int, count: Int): Boolean {
        // Every few pixels is plenty, and keeps this cheap on a slow TV.
        val stride = maxOf(1, count / 64)
        var dark = 0
        var samples = 0
        var i = 0
        while (i < count) {
            val p = pixels[start + i * step]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (maxOf(r, g, b) <= 28) dark++
            samples++
            i += stride
        }
        // A logo or a speck of compression noise doesn't make a row picture.
        return dark >= samples * 0.97f
    }
}

/**
 * A soft drop shadow under a cover — ViVi Music's `customSoftShadow`: a blurred
 * shadow layer drawn behind the shape, softer than an elevation shadow.
 * [weight] scales it for smaller covers; the strength comes from Settings.
 */
fun Modifier.coverShadow(cornerRadius: Dp, weight: Float = 1f): Modifier = composed {
    val strength by GlowPrefs.coverShadow.collectAsStateWithLifecycle()
    val shadows by com.music.bitchord.data.PerformanceProfile.coverShadows.collectAsStateWithLifecycle()
    if (!shadows) return@composed this
    val elevation = 22.dp * strength * weight
    if (elevation <= 0.5.dp) return@composed this
    val dark = Glow.colors.isDark
    // Paint and outline are built once per size and reused on every frame: covers in a
    // scrolling list or a player animation redraw their shadow each frame.
    drawWithCache {
        val shadow = Color.Black.copy(alpha = if (dark) 0.6f else 0.32f).toArgb()
        val paint = Paint().apply {
            isAntiAlias = true
            color = shadow
            setShadowLayer(elevation.toPx() * 2.2f, 0f, elevation.toPx() * 0.4f, shadow)
        }
        val r = cornerRadius.toPx()
        // Only the shadow, never the shape casting it: a filled shape under the cover
        // showed through as a dark square whenever the cover faded (switching to lyrics
        // or the queue) or hadn't loaded yet.
        val outline = android.graphics.Path().apply {
            addRoundRect(0f, 0f, size.width, size.height, r, r, android.graphics.Path.Direction.CW)
        }
        onDrawBehind {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.clipOutPath(outline)
                native.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
                native.restore()
            }
        }
    }
}
