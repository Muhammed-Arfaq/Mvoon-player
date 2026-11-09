package app.mvoon.core.player

/** A decoded platform bitmap (Android `Bitmap`, iOS `CGImage`, …) behind a neutral handle. */
interface PlatformImage {
    val width: Int
    val height: Int
}

enum class CueTextAlignment { START, CENTER, END }

/** A styled run inside subtitle text (bold/italic/colour from SRT tags, ASS overrides or WebVTT). */
data class TextSpanStyle(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    /** ARGB colour from the subtitle file, if any. */
    val color: Int? = null,
    val relativeSize: Float? = null,
)

data class StyledText(val text: String, val spans: List<TextSpanStyle> = emptyList())

sealed interface SubtitleCue {
    /** Text subtitle — fully restylable by the user. */
    data class Text(
        val text: StyledText,
        val alignment: CueTextAlignment = CueTextAlignment.CENTER,
        /** True when the source placed this cue in the top part of the frame (e.g. ASS \an8 signs). */
        val anchoredTop: Boolean = false,
    ) : SubtitleCue

    /**
     * Image subtitle (PGS / VobSub / DVB). Geometry is expressed as fractions of the video frame, as
     * authored. Colours, font and outline are baked into the pixels and cannot be changed.
     */
    data class Image(
        val image: PlatformImage,
        val left: Float,
        val top: Float,
        val width: Float,
        /** Fraction of the frame height, or NaN to keep the bitmap's own aspect ratio. */
        val height: Float,
    ) : SubtitleCue
}

data class SubtitleFrame(val cues: List<SubtitleCue> = emptyList()) {
    val isEmpty: Boolean get() = cues.isEmpty()

    companion object {
        val EMPTY = SubtitleFrame()
    }
}
