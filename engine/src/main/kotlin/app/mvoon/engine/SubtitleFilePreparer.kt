package app.mvoon.engine

import android.content.Context
import android.net.Uri
import app.mvoon.core.media.ExternalSubtitle
import app.mvoon.subtitles.SubtitleCharsets
import app.mvoon.subtitles.SubtitleFormats
import java.io.File
import java.security.MessageDigest

/**
 * Re-encodes non-UTF-8 sidecar subtitles (common for SRT/ASS) into a UTF-8 copy in the app cache,
 * because Media3's parsers assume UTF-8. UTF-8 files are passed through untouched. Call off the main thread.
 */
class SubtitleFilePreparer(private val context: Context) {

    private val dir = File(context.cacheDir, "subtitles").apply { mkdirs() }

    fun prepare(subtitle: ExternalSubtitle, fallbackCharset: String?): ExternalSubtitle {
        if (!SubtitleFormats.isPlainTextFormat(subtitle.mimeType)) return subtitle
        return runCatching {
            val bytes = context.contentResolver.openInputStream(Uri.parse(subtitle.uri))?.use { input ->
                input.readBytes()
            } ?: return subtitle
            if (bytes.size > MAX_BYTES) return subtitle
            val decoded = SubtitleCharsets.decode(bytes, fallbackCharset, subtitle.language)
            if (!decoded.wasConverted) return subtitle
            val name = sha1(subtitle.uri + decoded.charset) + extensionFor(subtitle.mimeType)
            val out = File(dir, name)
            if (!out.exists()) out.writeText(decoded.text, Charsets.UTF_8)
            subtitle.copy(uri = Uri.fromFile(out).toString())
        }.getOrDefault(subtitle)
    }

    /** Drop converted copies older than a week so the cache can't grow without bound. */
    fun trimCache() {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private fun extensionFor(mime: String) = when (mime) {
        SubtitleFormats.MIME_SSA -> ".ass"
        SubtitleFormats.MIME_VTT -> ".vtt"
        else -> ".srt"
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
    }
}
