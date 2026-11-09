package app.mvoon.library.scan

import app.mvoon.core.media.MediaKind

/** Extension-based media detection for providers that report `application/octet-stream`. */
object MediaFiles {
    private val video = setOf(
        "mkv", "mk3d", "mp4", "m4v", "mov", "qt", "avi", "webm", "ts", "m2ts", "mts", "3gp", "3g2",
        "flv", "ogv", "mpg", "mpeg", "vob", "wmv", "asf", "divx", "f4v",
    )
    private val audio = setOf(
        "mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "mka", "ac3", "eac3", "ec3", "dts", "dtshd",
        "thd", "mlp", "alac", "amr", "awb", "wma", "aiff", "aif", "mp2", "weba",
    )

    /** A real audio/video MIME type wins (".mp4" can be audio-only); the extension covers generic types. */
    fun kindFor(fileName: String, mimeType: String?): MediaKind? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            mimeType?.startsWith("audio/") == true -> MediaKind.AUDIO
            mimeType?.startsWith("video/") == true -> MediaKind.VIDEO
            ext in video -> MediaKind.VIDEO
            ext in audio -> MediaKind.AUDIO
            else -> null
        }
    }

    fun titleFromFileName(fileName: String): String = fileName.substringBeforeLast('.').ifBlank { fileName }

    /** "external_primary" → "Internal storage"; SD card volumes are named by their id ("1a2b-3c4d"). */
    fun volumeLabel(volume: String?): String = when {
        volume == null || volume == "external" || volume == "external_primary" || volume == "primary" -> "Internal storage"
        else -> "SD card"
    }
}
