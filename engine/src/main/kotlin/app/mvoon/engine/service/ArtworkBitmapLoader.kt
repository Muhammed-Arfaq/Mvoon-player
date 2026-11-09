package app.mvoon.engine.service

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.media3.common.util.BitmapLoader
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Notification / lock-screen artwork. Media3's default loader can only decode image files; local
 * media URIs (videos, songs) are turned into thumbnails or embedded cover art here instead.
 */
class ArtworkBitmapLoader(context: Context) : BitmapLoader {

    private val appContext = context.applicationContext
    private val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    private val fallback = DataSourceBitmapLoader.Builder(appContext).build()

    override fun supportsMimeType(mimeType: String): Boolean = fallback.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = fallback.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val isLocal = uri.scheme == ContentResolver.SCHEME_CONTENT || uri.scheme == ContentResolver.SCHEME_FILE
        if (!isLocal) return fallback.loadBitmap(uri)
        val type = runCatching { appContext.contentResolver.getType(uri) }.getOrNull()
        val looksLikeMedia = type?.startsWith("video/") == true || type?.startsWith("audio/") == true ||
            (type == null && uri.scheme == ContentResolver.SCHEME_FILE && !uri.path.orEmpty().endsWith(".jpg"))
        if (!looksLikeMedia) return fallback.loadBitmap(uri)
        val isAudio = type?.startsWith("audio/") == true
        return executor.submit(Callable { thumbnailFor(uri, isAudio) })
    }

    private fun thumbnailFor(uri: Uri, isAudio: Boolean): Bitmap {
        // The system thumbnail is fine for videos, but for songs without embedded art Android
        // substitutes an image from the same folder (often an unrelated photo): never use it there.
        if (!isAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching { return appContext.contentResolver.loadThumbnail(uri, Size(512, 512), null) }
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
            retriever.embeddedPicture?.let { bytes ->
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it }
            }
            if (isAudio) return generatedCover(uri.toString())
            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(5_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 512, 512)
            } else {
                retriever.getFrameAtTime(5_000_000)
            }
            return frame ?: error("No artwork for $uri")
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Cerulean gradient with a music note, for songs that carry no cover of their own. */
    private fun generatedCover(seed: String): Bitmap {
        val palettes = listOf(
            0xFF5B8CFF.toInt() to 0xFF2F3FA8.toInt(),
            0xFF1F2E5E.toInt() to 0xFF0A1020.toInt(),
            0xFF8B7CFF.toInt() to 0xFF3A2F8F.toInt(),
            0xFF5FD4F5.toInt() to 0xFF2563C9.toInt(),
        )
        val (from, to) = palettes[Math.floorMod(seed.hashCode(), palettes.size)]
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), from, to, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = null
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 6f
        paint.color = 0x14FFFFFF
        for (i in 1..6) canvas.drawCircle(size * 0.78f, size * 0.82f, size * 0.11f * i, paint)
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = 0xCCFFFFFF.toInt()
        paint.textAlign = android.graphics.Paint.Align.CENTER
        paint.textSize = size * 0.42f
        canvas.drawText("♪", size / 2f, size / 2f + paint.textSize * 0.35f, paint)
        return bitmap
    }
}
