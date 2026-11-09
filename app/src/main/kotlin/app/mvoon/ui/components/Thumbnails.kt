package app.mvoon.ui.components

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mvoon.core.media.MediaKind
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

/** Coil model for a media thumbnail (video frame or embedded cover art). */
data class MediaThumb(val uri: String, val kind: MediaKind)

class MediaThumbKeyer : Keyer<MediaThumb> {
    override fun key(data: MediaThumb, options: Options): String = "thumb2:${data.kind}:${data.uri}"
}

class MediaThumbFetcher(
    private val data: MediaThumb,
    private val options: Options,
    private val context: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val width = options.size.width.pxOrElse { 480 }.coerceIn(96, 1024)
        val height = options.size.height.pxOrElse { 270 }.coerceIn(96, 1024)
        val uri = Uri.parse(data.uri)
        if (noArtMarker.exists()) error("No artwork for ${data.uri}")
        val bitmap = (if (data.kind == MediaKind.VIDEO) systemThumbnail(uri, width, height) else null)
            ?: diskCached()
            ?: limiter.withPermit { extract(uri, width, height) }?.also(::storeOnDisk)
        if (bitmap == null) {
            runCatching { noArtMarker.createNewFile() }
            error("No artwork for ${data.uri}")
        }
        ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    /**
     * The system keeps its own thumbnail cache for indexed videos — by far the cheapest path.
     * Not used for audio: for songs without embedded art, Android substitutes an image file from
     * the same folder (often an unrelated photo), so songs only ever show their own cover.
     */
    private fun systemThumbnail(uri: Uri, width: Int, height: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority != MediaStore.AUTHORITY) return null
        return runCatching { context.contentResolver.loadThumbnail(uri, Size(width, height), null) }.getOrNull()
    }

    private fun extract(uri: Uri, width: Int, height: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            if (data.kind == MediaKind.AUDIO) {
                retriever.embeddedPicture?.let { bytes -> decodeSampled(bytes, width, height) }
            } else {
                val durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) * 1000
                // A frame ~10% in is far more representative than the (often black) first frame.
                val atUs = if (durationUs > 0) (durationUs / 10).coerceAtMost(60_000_000) else 2_000_000
                // Fall back to the first frame when the duration is unknown or the seek fails.
                listOf(atUs, 0L).firstNotNullOfOrNull { time ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, width, height)
                    } else {
                        retriever.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeSampled(bytes: ByteArray, width: Int, height: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private val baseName: String
        get() = MessageDigest.getInstance("SHA-1").digest("${data.kind}:${data.uri}".toByteArray()).joinToString("") { "%02x".format(it) }

    private val cacheDir: File get() = File(context.cacheDir, "thumbs").apply { mkdirs() }
    private val cacheFile: File get() = File(cacheDir, "$baseName.jpg")

    /** Remembers files without artwork so they aren't opened again on every scroll. */
    private val noArtMarker: File get() = File(cacheDir, "$baseName.none")

    private fun diskCached(): Bitmap? = cacheFile.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    private fun storeOnDisk(bitmap: Bitmap) {
        runCatching { cacheFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
    }

    class Factory(private val context: Context) : Fetcher.Factory<MediaThumb> {
        override fun create(data: MediaThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbFetcher(data, options, context.applicationContext)
    }

    private companion object {
        /** Frame extraction is CPU/IO heavy: cap concurrency so scrolling stays smooth. */
        val limiter = Semaphore(3)
    }
}

private enum class ThumbPhase { LOADING, READY, MISSING }

/**
 * Thumbnail or cover art. Shimmers while loading; when a file has no picture of its own it shows
 * generated artwork (a cerulean gradient seeded by [seed], so an album's tracks share colours).
 */
@Composable
fun MediaThumbnail(
    uri: String?,
    kind: MediaKind,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    seed: String? = null,
) {
    var phase by remember(uri) { mutableStateOf(if (uri == null) ThumbPhase.MISSING else ThumbPhase.LOADING) }
    Box(modifier, contentAlignment = Alignment.Center) {
        when (phase) {
            ThumbPhase.LOADING -> Box(Modifier.fillMaxSize().shimmer())
            ThumbPhase.MISSING -> GeneratedArt(seed ?: uri.orEmpty(), kind, iconSize = iconSize)
            ThumbPhase.READY -> Unit
        }
        if (uri != null && phase != ThumbPhase.MISSING) {
            AsyncImage(
                model = MediaThumb(uri, kind),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { phase = ThumbPhase.READY },
                onError = { phase = ThumbPhase.MISSING },
            )
        }
    }
}

/** Generated-cover gradients, all from the cerulean palette. */
private val ArtPalettes = listOf(
    Color(0xFF5B8CFF) to Color(0xFF2F3FA8),
    Color(0xFF1F2E5E) to Color(0xFF0A1020),
    Color(0xFF8FB8FF) to Color(0xFF4A63D9),
    Color(0xFF8B7CFF) to Color(0xFF3A2F8F),
    Color(0xFF5FD4F5) to Color(0xFF2563C9),
    Color(0xFF6C7FA6) to Color(0xFF2B3654),
    Color(0xFFB4A8FF) to Color(0xFF5B8CFF),
    Color(0xFF5ED3B3) to Color(0xFF1F6F8B),
)

/**
 * Designed artwork for media without a picture: a cerulean gradient chosen by [seed], a soft vinyl
 * (audio) or light-sweep (video) motif, and a centred glyph.
 */
@Composable
fun GeneratedArt(seed: String, kind: MediaKind, modifier: Modifier = Modifier, iconSize: Dp = 28.dp, showIcon: Boolean = true) {
    val (from, to) = ArtPalettes[abs(seed.hashCode() % ArtPalettes.size)]
    val onArt = if (from.luminance() > 0.45f) Color(0xFF231F17) else Color.White
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(from, to)))
            .drawBehind {
                if (kind == MediaKind.AUDIO) {
                    // Vinyl grooves, off-centre towards the bottom-right corner.
                    val center = Offset(size.width * 0.78f, size.height * 0.82f)
                    val step = size.minDimension * 0.11f
                    for (i in 1..6) {
                        drawCircle(onArt.copy(alpha = 0.07f), radius = step * i, center = center, style = Stroke(width = size.minDimension * 0.012f))
                    }
                } else {
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                            start = Offset.Zero,
                            end = Offset(size.width * 0.7f, size.height),
                        ),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (showIcon) {
            Icon(
                imageVector = if (kind == MediaKind.AUDIO) Lucide.Music else Lucide.Film,
                contentDescription = null,
                tint = onArt.copy(alpha = 0.8f),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
