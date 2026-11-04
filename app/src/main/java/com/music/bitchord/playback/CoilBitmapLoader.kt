package com.music.bitchord.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.music.bitchord.ui.glow.artCandidates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Artwork for the notification, lock screen and car, through the app's own image cache.
 *
 * Media3's default loader fetched every cover from the internet, so offline the notification
 * had none, even for a song whose cover the app was showing (the owner's log: ~200 failed
 * loads). This asks Coil, and when the network can't answer, looks in Coil's cache for the
 * same cover at the sizes the app itself shows it at.
 */
@UnstableApi
class CoilBitmapLoader(context: Context) : BitmapLoader {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bitmap != null) future.set(bitmap) else future.setException(IllegalArgumentException("Couldn't decode artwork"))
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            val loader = SingletonImageLoader.get(app)
            suspend fun load(data: String, network: Boolean): Bitmap? {
                val request = ImageRequest.Builder(app)
                    .data(data)
                    .allowHardware(false)
                    .networkCachePolicy(if (network) CachePolicy.ENABLED else CachePolicy.DISABLED)
                    .build()
                return (loader.execute(request) as? SuccessResult)?.image?.toBitmap()
            }
            val url = uri.toString()
            val bitmap = load(url, network = true)
                ?: (artCandidates(url, 1080) + artCandidates(url, 544) + artCandidates(url, 180))
                    .distinct()
                    .firstNotNullOfOrNull { load(it, network = false) }
            if (bitmap != null) future.set(bitmap) else future.setException(IllegalStateException("No artwork for $uri"))
        }
        return future
    }
}
