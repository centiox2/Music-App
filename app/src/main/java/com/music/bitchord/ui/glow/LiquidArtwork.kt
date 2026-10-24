package com.music.bitchord.ui.glow

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.music.bitchord.ui.graphics.forPixelAccess
import com.music.bitchord.ui.graphics.toImageBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Apple Music's liquid background, rebuilt from the reverse-engineered web
 * player: four copies of the cover at 125%, 80%, 50% and 25% size, each
 * turning at its own rate and the two small ones circling, then the whole
 * scene twisted around its centre, blurred heavily and saturated. What is left
 * is the cover's own colours flowing, with nothing of its picture.
 *
 * The scene is drawn at a quarter of the screen's resolution and scaled up:
 * the blur hides that completely and it is sixteen times less work per frame.
 * It ticks at 30 fps, the pace the original is written for. The twist needs
 * Android 13; Android 12 gets the rest; older versions use the colour mesh.
 */
@Composable
fun LiquidArtwork(
    url: String?,
    fallback: CoverPalette,
    modifier: Modifier = Modifier,
    moving: Boolean = true,
    dark: Boolean = true,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        GlowMesh(fallback, modifier, moving = moving, dark = dark)
        return
    }
    val context = LocalPlatformContext.current
    // The cover now showing, and the one it is fading in over.
    var current by remember { mutableStateOf<ImageBitmap?>(LiquidCovers.get(url)) }
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(url) {
        // A song change during the last cross-fade cancelled it half way; finish it first,
        // or the cover before stayed drawn under the new one for good.
        if (previous != null || fade.value < 1f) {
            fade.snapTo(1f)
            previous = null
        }
        if (url == null) return@LaunchedEffect
        val image = LiquidCovers.get(url) ?: run {
            val request = ImageRequest.Builder(context).data(url).size(200).forPixelAccess().build()
            (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toImageBitmap()
                ?.also { LiquidCovers.put(url, it) }
        } ?: return@LaunchedEffect
        if (image === current) return@LaunchedEffect
        // The old cover stays until the new one has loaded, then cross-fades out.
        previous = current
        current = image
        try {
            fade.snapTo(if (previous == null) 1f else 0f)
            fade.animateTo(1f, tween(1400))
        } finally {
            previous = null
        }
    }

    // Time in 30 fps ticks, which is the unit the original's speeds are in.
    var ticks by remember { mutableFloatStateOf(LiquidCovers.ticks) }
    LaunchedEffect(moving) {
        if (!moving) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (isActive) {
            // Sleep between ticks rather than wake on every frame: on a 120Hz screen that
            // was 120 wake-ups a second to draw 30.
            delay(30)
            withFrameMillis { now ->
                ticks += (now - last).coerceAtMost(100) / 33.333f
                LiquidCovers.ticks = ticks
                last = now
            }
        }
    }

    val base = remember(fallback, dark) { liquidBase(fallback.dominant, dark) }
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val density = LocalDensity.current
        val fullW = constraints.maxWidth.coerceAtLeast(1)
        val fullH = constraints.maxHeight.coerceAtLeast(1)
        val w = (fullW / SCALE).roundToInt().coerceAtLeast(1)
        val h = (fullH / SCALE).roundToInt().coerceAtLeast(1)
        val effect = remember(w, h, dark) { liquidEffect(w, h, dark) }
        Box(
            Modifier
                .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = fullW.toFloat() / w
                    scaleY = fullH.toFloat() / h
                    renderEffect = effect.asComposeRenderEffect()
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(base)
                val t = ticks
                previous?.let { drawScene(it, t, 1f) }
                current?.let { drawScene(it, t, fade.value) }
            }
        }
    }
}

private const val SCALE = 4f

/** The four copies, placed and turned as the original places them. */
private fun DrawScope.drawScene(image: ImageBitmap, t: Float, alpha: Float) {
    if (alpha <= 0f) return
    // The original sizes its copies by the width of a landscape window; on a
    // phone held upright the height is the long side, so that is the measure.
    val basis = size.maxDimension
    val cx = size.width / 2
    val cy = size.height / 2
    val r0 = 0.003f * t
    val r1 = -0.008f * t
    val r2 = -0.006f * t
    val r3 = 0.004f * t
    val orbit = size.minDimension / 2
    sprite(image, Offset(cx, cy), basis * 1.25f, r0, alpha)
    sprite(image, Offset(size.width / 2.5f, size.height / 2.5f), basis * 0.8f, r1, alpha)
    sprite(image, Offset(cx + orbit * cos(r2 * 0.75f), cy + orbit * sin(r2 * 0.75f)), basis * 0.5f, r2, alpha)
    val nudge = orbit * 0.2f
    sprite(image, Offset(cx + nudge + orbit * cos(r3 * 0.75f), cy + nudge + orbit * sin(r3 * 0.75f)), basis * 0.25f, r3, alpha)
}

private fun DrawScope.sprite(image: ImageBitmap, center: Offset, side: Float, radians: Float, alpha: Float) {
    val half = side / 2
    withTransform({ rotateRad(radians, center) }) {
        drawImage(
            image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt()),
            dstSize = IntSize(side.roundToInt(), side.roundToInt()),
            alpha = alpha,
            filterQuality = FilterQuality.Low,
        )
    }
}

/** Twist (Android 13+), then blur, then saturation — the original's filter order. */
@RequiresApi(Build.VERSION_CODES.S)
private fun liquidEffect(w: Int, h: Int, dark: Boolean): RenderEffect {
    // The original blurs by about a twelfth of its window's width.
    val sigma = (maxOf(w, h) / 11f).coerceAtLeast(4f)
    val blur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RenderEffect.createBlurEffect(sigma, sigma, twistEffect(w, h), Shader.TileMode.CLAMP)
    } else {
        RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.CLAMP)
    }
    return RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(tone(dark)), blur)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun twistEffect(w: Int, h: Int): RenderEffect {
    val shader = RuntimeShader(TWIST)
    shader.setFloatUniform("center", w / 2f, h / 2f)
    // 900px of a ~1280px window in the original: most of the screen turns.
    shader.setFloatUniform("radius", maxOf(w, h) * 0.7f)
    shader.setFloatUniform("angle", -3.25f)
    shader.setFloatUniform("bounds", w - 1f, h - 1f)
    return RenderEffect.createRuntimeShaderEffect(shader, "content")
}

/**
 * The original's saturation is 2.75 on a light page; here white text sits on
 * it, so a little less, and in dark mode slightly dimmed.
 */
private fun tone(dark: Boolean): ColorMatrix {
    val m = ColorMatrix().apply { setSaturation(1.9f) }
    if (dark) {
        m.postConcat(ColorMatrix().apply { setScale(0.82f, 0.82f, 0.82f, 1f) })
    } else {
        // Light theme: lifted toward white, as the colour mesh does, so dark text stays readable.
        m.postConcat(ColorMatrix(floatArrayOf(0.62f, 0f, 0f, 0f, 92f, 0f, 0.62f, 0f, 0f, 92f, 0f, 0f, 0.62f, 0f, 92f, 0f, 0f, 0f, 1f, 0f)))
    }
    return m
}

// The colour under the copies; it then goes through the same tone as they do.
private fun liquidBase(color: Color, dark: Boolean): Color =
    if (dark) androidx.compose.ui.graphics.lerp(color, Color.Black, 0.35f) else color

/** PixiJS's TwistFilter: each point turns about the centre, the most at the middle. */
private const val TWIST = """
uniform shader content;
uniform float2 center;
uniform float radius;
uniform float angle;
uniform float2 bounds;

half4 main(float2 coord) {
    float2 d = coord - center;
    float dist = length(d);
    if (dist < radius) {
        float ratio = (radius - dist) / radius;
        float a = ratio * ratio * angle;
        float s = sin(a);
        float c = cos(a);
        d = float2(d.x * c - d.y * s, d.x * s + d.y * c);
    }
    return content.eval(clamp(center + d, float2(0.0), bounds));
}
"""

/** Decoded covers for the background, and its clock, so reopening the player picks up where it was. */
internal object LiquidCovers {
    private val cache = object : LinkedHashMap<String, ImageBitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) = size > 6
    }

    @Volatile var ticks = 0f

    @Synchronized fun get(url: String?): ImageBitmap? = url?.let { cache[key(it)] }

    @Synchronized fun put(url: String, image: ImageBitmap) {
        cache[key(url)] = image
    }

    private fun key(url: String) = url.substringBefore("=w").substringBefore("=s").substringBefore("?")
}
