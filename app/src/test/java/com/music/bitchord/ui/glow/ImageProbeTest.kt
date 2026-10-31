package com.music.bitchord.ui.glow

import coil3.SingletonImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [35])
class ImageProbeTest {
    @Test
    fun probe() {
        assumeTrue(System.getenv("REVIEW_SHOTS") != null)
        val app = RuntimeEnvironment.getApplication()
        // Robolectric has no native ImageDecoder; BitmapFactory decodes covers there.
        coil3.SingletonImageLoader.setUnsafe(coil3.ImageLoader.Builder(RuntimeEnvironment.getApplication()).components { add(coil3.decode.BitmapFactoryDecoder.Factory()) }.build())
        val out = StringBuilder()
        out.append("proxy=${System.getProperty("https.proxyHost")}:${System.getProperty("https.proxyPort")}\n")
        for (url in listOf("https://i.ytimg.com/vi/fHI8X4OXluQ/hqdefault.jpg", "https://lh3.googleusercontent.com/r_SysR5EYXyn93P7mp_zjDd3AUPEwWi3T4XpgKsh4_fDNHQY50wOylATtpdwBdbTQ9CaxZ-Xpu4kG6k=w544-h544-p-l90-rj")) {
            val r = runBlocking { SingletonImageLoader.get(app).execute(ImageRequest.Builder(app).data(url).build()) }
            out.append(url).append(" -> ").append(
                when (r) {
                    is SuccessResult -> "OK ${r.image.width}x${r.image.height} from ${r.dataSource}"
                    is ErrorResult -> "ERROR ${r.throwable}\n" + r.throwable.stackTraceToString().take(1500)
                },
            ).append("\n")
        }
        File(System.getenv("REVIEW_SHOTS")!!).apply { mkdirs() }.resolve("image-probe.txt").writeText(out.toString())
    }
}
