package app.mvoon.core.player

enum class TrackType { VIDEO, AUDIO, SUBTITLE }

/** Which decoder family handles a track on this device. */
enum class DecoderSupport {
    /** Android's own MediaCodec decoders (hardware or the platform's software codecs). */
    PLATFORM,

    /** The bundled FFmpeg audio decoders (AC-3, E-AC-3, DTS, TrueHD, …). */
    FFMPEG,
    UNSUPPORTED,
    UNKNOWN,
}

data class TrackInfo(
    /** Opaque id understood by the engine that produced it. */
    val id: String,
    val type: TrackType,
    val label: String,
    val language: String? = null,
    /** Human-readable codec name, e.g. "E-AC-3", "H.265 / HEVC", "PGS (image)". */
    val codec: String? = null,
    val mimeType: String? = null,
    val channels: Int = 0,
    val sampleRate: Int = 0,
    val bitrate: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val isSupported: Boolean = true,
    val isSelected: Boolean = false,
    val isExternal: Boolean = false,
    val isForced: Boolean = false,
    val isDefault: Boolean = false,
    /** Image-based subtitle (PGS, VobSub, DVB): can be moved/resized but not restyled. */
    val isBitmapSubtitle: Boolean = false,
    val decoderSupport: DecoderSupport = DecoderSupport.UNKNOWN,
)

data class TrackSet(
    val video: List<TrackInfo> = emptyList(),
    val audio: List<TrackInfo> = emptyList(),
    val subtitles: List<TrackInfo> = emptyList(),
) {
    val selectedAudio: TrackInfo? get() = audio.firstOrNull { it.isSelected }
    val selectedSubtitle: TrackInfo? get() = subtitles.firstOrNull { it.isSelected }
    val selectedVideo: TrackInfo? get() = video.firstOrNull { it.isSelected }
    val unsupported: List<TrackInfo> get() = (video + audio + subtitles).filterNot { it.isSupported }
}

/** Formats a channel count the way people talk about it ("Stereo", "5.1", "7.1"). */
fun channelLayoutName(channels: Int): String? = when (channels) {
    0 -> null
    1 -> "Mono"
    2 -> "Stereo"
    3 -> "2.1"
    6 -> "5.1"
    8 -> "7.1"
    else -> "$channels ch"
}
