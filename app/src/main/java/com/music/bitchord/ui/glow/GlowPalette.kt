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
import androidx.compose.ui.graphics.toArgb
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.music.bitchord.ui.graphics.forPixelAccess
import com.music.bitchord.ui.graphics.toImageBitmap
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
    private val cache = object : LinkedHashMap<String, CoverPalette>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CoverPalette>) = size > 200
    }

    @Synchronized fun get(url: String?): CoverPalette? = url?.let { cache[key(it)] }

    @Synchronized fun put(url: String, palette: CoverPalette) {
        cache[key(url)] = palette
        last = palette
    }

    /** The most recently decoded cover's colours: a better first frame than stock ones. */
    @Volatile var last: CoverPalette? = null
        private set

    // The same cover is asked for at many sizes (=w120, =w544, =w1080…); one entry serves them all.
    private fun key(url: String) = url.substringBefore("=w").substringBefore("=s").substringBefore("?")
}

/**
 * A cover's real colours and how much of it each covers, largest first.
 * Nothing is boosted or invented: a black cover with a red stripe is mostly
 * black here, and the liquid background draws it that way.
 */
@androidx.compose.runtime.Immutable
data class CoverPalette(val colors: List<Color>, val weights: List<Float>) {
    val dominant: Color get() = colors.first()
}

/** Cached colours immediately when there are any, otherwise null until the cover is decoded. */
@Composable
fun rememberGlowPalette(url: String?): CoverPalette? {
    val context = LocalPlatformContext.current
    var palette by remember(url) { mutableStateOf(ArtworkPalettes.get(url)) }
    LaunchedEffect(url) {
        if (url == null || palette != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(url).size(64).forPixelAccess().build()
        val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toImageBitmap()
            ?: return@LaunchedEffect
        val found = withContext(Dispatchers.Default) { coverPalette(bitmap) } ?: return@LaunchedEffect
        ArtworkPalettes.put(url, found)
        palette = found
    }
    return palette
}

/**
 * The cover's colours by area: k-means over its pixels in a perceptual space
 * (OKLab), with each colour weighted by the share of pixels nearest to it.
 * Near-identical clusters are merged and slivers under 1.5% dropped.
 */
fun coverPalette(bitmap: androidx.compose.ui.graphics.ImageBitmap, k: Int = 6): CoverPalette? {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return null
    val pixels = IntArray(w * h)
    bitmap.readPixels(pixels)
    val n = pixels.size
    val lab = Array(n) { toOklab(pixels[it]) }
    // Deterministic k-means++ seeding: the same cover always gives the same palette.
    val rnd = java.util.Random(n.toLong() * 31 + pixels[n / 2])
    val centers = mutableListOf(lab[rnd.nextInt(n)].copyOf())
    val dist = DoubleArray(n) { Double.MAX_VALUE }
    while (centers.size < k) {
        var total = 0.0
        for (i in 0 until n) {
            dist[i] = minOf(dist[i], sq(lab[i], centers.last()))
            total += dist[i]
        }
        if (total <= 1e-9) break
        var r = rnd.nextDouble() * total
        var pick = n - 1
        for (i in 0 until n) { r -= dist[i]; if (r <= 0) { pick = i; break } }
        centers += lab[pick].copyOf()
    }
    val assign = IntArray(n)
    repeat(10) {
        for (i in 0 until n) {
            var best = 0
            var bd = Double.MAX_VALUE
            for (c in centers.indices) { val d = sq(lab[i], centers[c]); if (d < bd) { bd = d; best = c } }
            assign[i] = best
        }
        val sums = Array(centers.size) { DoubleArray(3) }
        val counts = IntArray(centers.size)
        for (i in 0 until n) { val c = assign[i]; counts[c]++; for (j in 0..2) sums[c][j] += lab[i][j] }
        for (c in centers.indices) if (counts[c] > 0) for (j in 0..2) centers[c][j] = sums[c][j] / counts[c]
    }
    val counts = IntArray(centers.size)
    for (i in 0 until n) counts[assign[i]]++
    // Merge clusters that read as the same colour.
    val merged = mutableListOf<Pair<DoubleArray, Int>>()
    centers.indices.sortedByDescending { counts[it] }.forEach { c ->
        if (counts[c] == 0) return@forEach
        val near = merged.indexOfFirst { sq(it.first, centers[c]) < 0.0025 }
        if (near >= 0) {
            val (m, mc) = merged[near]
            val total = mc + counts[c]
            merged[near] = DoubleArray(3) { j -> (m[j] * mc + centers[c][j] * counts[c]) / total } to total
        } else {
            merged += centers[c] to counts[c]
        }
    }
    val kept = merged.filter { it.second.toFloat() / n >= 0.015f }.sortedByDescending { it.second }
    if (kept.isEmpty()) return null
    val sum = kept.sumOf { it.second }.toFloat()
    return CoverPalette(kept.map { fromOklab(it.first) }, kept.map { it.second / sum })
}

private fun sq(a: DoubleArray, b: DoubleArray): Double {
    val x = a[0] - b[0]; val y = a[1] - b[1]; val z = a[2] - b[2]
    return x * x + y * y + z * z
}

private fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }
private fun gam(v: Double): Float { val c = if (v <= 0.0031308) 12.92 * v else 1.055 * Math.pow(v, 1 / 2.4) - 0.055; return c.coerceIn(0.0, 1.0).toFloat() }

private fun toOklab(argb: Int): DoubleArray {
    val r = lin((argb shr 16) and 0xFF); val g = lin((argb shr 8) and 0xFF); val b = lin(argb and 0xFF)
    val l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    return doubleArrayOf(
        0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
        1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
        0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
    )
}

private fun fromOklab(lab: DoubleArray): Color {
    val l = Math.pow(lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2], 3.0)
    val m = Math.pow(lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2], 3.0)
    val s = Math.pow(lab[0] - 0.0894841775 * lab[1] - 1.2914855480 * lab[2], 3.0)
    return Color(
        gam(4.0767416621 * l - 3.3077085627 * m + 0.2309699292 * s),
        gam(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
        gam(-0.0041960863 * l - 0.7034134229 * m + 1.7076926660 * s),
    )
}

/**
 * The colour a person would call "the cover's colour": the most common one, unless that is a
 * near-black, near-white or grey backdrop, in which case the most vivid colour that covers a
 * real share of the cover (a red sleeve on black reads as red, not black).
 */
fun coverAccent(palette: CoverPalette): Color {
    val hsl = FloatArray(3)
    fun hslOf(c: Color) = FloatArray(3).also { androidx.core.graphics.ColorUtils.colorToHSL(c.toArgb(), it) }
    val dominant = palette.dominant
    androidx.core.graphics.ColorUtils.colorToHSL(dominant.toArgb(), hsl)
    val backdrop = hsl[1] < 0.18f || hsl[2] < 0.1f || hsl[2] > 0.92f
    if (!backdrop) return dominant
    return palette.colors.zip(palette.weights)
        .filter { (c, w) -> w >= 0.08f && hslOf(c).let { it[1] >= 0.25f && it[2] in 0.12f..0.9f } }
        .maxByOrNull { (c, w) -> hslOf(c)[1] * kotlin.math.sqrt(w) }
        ?.first ?: dominant
}

/**
 * A surface colour for the mini player and the menus raised from the player: the cover's
 * colour ([coverAccent]) with its hue and saturation kept, only its lightness moved into the
 * range where the text on it stays readable.
 */
fun miniPlayerTint(palette: CoverPalette?, base: Color, isDark: Boolean): Color {
    val seed = palette?.let(::coverAccent) ?: return base
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(seed.toArgb(), hsl)
    hsl[2] = if (isDark) hsl[2].coerceIn(0.16f, 0.32f) else hsl[2].coerceIn(0.8f, 0.9f)
    // A grey cover stays grey; a vivid one is kept vivid, just not neon.
    hsl[1] = hsl[1].coerceAtMost(0.85f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}
