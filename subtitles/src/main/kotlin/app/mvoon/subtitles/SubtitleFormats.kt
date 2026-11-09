package app.mvoon.subtitles

/**
 * Sidecar subtitle formats the Android engine (Media3) can parse.
 *
 * Deliberately NOT listed: MicroDVD `.sub`, VobSub `.idx/.sub` pairs, SAMI `.smi` and `.lrc` —
 * Media3 has no sidecar parser for them, so offering them would only produce a silent failure.
 * (VobSub, PGS and DVB *embedded* in MKV/MP4/TS are supported.)
 */
object SubtitleFormats {
    const val MIME_SUBRIP = "application/x-subrip"
    const val MIME_SSA = "text/x-ssa"
    const val MIME_VTT = "text/vtt"
    const val MIME_TTML = "application/ttml+xml"

    private val byExtension = mapOf(
        "srt" to MIME_SUBRIP,
        "ass" to MIME_SSA,
        "ssa" to MIME_SSA,
        "vtt" to MIME_VTT,
        "webvtt" to MIME_VTT,
        "ttml" to MIME_TTML,
        "dfxp" to MIME_TTML,
    )

    val supportedExtensions: Set<String> get() = byExtension.keys

    fun extensionOf(fileName: String): String = fileName.substringAfterLast('.', "").lowercase()

    fun mimeTypeFor(fileName: String): String? = byExtension[extensionOf(fileName)]

    fun isSupported(fileName: String): Boolean = mimeTypeFor(fileName) != null

    /** Text formats whose bytes we may need to re-encode to UTF-8 (TTML declares its own encoding). */
    fun isPlainTextFormat(mimeType: String): Boolean =
        mimeType == MIME_SUBRIP || mimeType == MIME_SSA || mimeType == MIME_VTT

    fun displayName(mimeType: String?): String = when (mimeType) {
        MIME_SUBRIP -> "SubRip (SRT)"
        MIME_SSA -> "SubStation Alpha (ASS/SSA)"
        MIME_VTT -> "WebVTT"
        MIME_TTML -> "TTML"
        else -> mimeType ?: "Unknown"
    }
}
