package app.mvoon.core.media

/** Broad category of a playable item. Drives UI (video surface vs. audio layout) and battery decisions. */
enum class MediaKind { VIDEO, AUDIO }

/** How a network URL should be interpreted. AUTO lets the engine infer from the URL / server. */
enum class StreamType { AUTO, PROGRESSIVE, HLS, DASH, SMOOTH_STREAMING, RTSP }

/**
 * Something the user can play. Platform-neutral: every location is a URI string
 * (content://, file://, http(s)://, rtsp://, …) so this model can move to Kotlin Multiplatform unchanged.
 */
data class PlayableItem(
    /** Stable key used for resume positions, favourites and playlists. Usually the URI string. */
    val id: String,
    val uri: String,
    val title: String,
    /** Secondary line: artist, album, folder or host. */
    val subtitle: String? = null,
    val kind: MediaKind,
    val mimeType: String? = null,
    val artworkUri: String? = null,
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val externalSubtitles: List<ExternalSubtitle> = emptyList(),
    val startPositionMs: Long = 0,
    val isStream: Boolean = false,
    val streamType: StreamType = StreamType.AUTO,
    /** Choices the user made last time this item played; restored when its tracks become known. */
    val trackMemory: TrackMemory? = null,
    /** Play with the compatibility engine (it was needed for this file before). */
    val preferCompatibilityEngine: Boolean = false,
)

/** Per-file playback choices remembered between sessions. Track indexes are positions within their type. */
data class TrackMemory(
    val audioTrackIndex: Int? = null,
    /** -1 = the user turned subtitles off; null = no explicit choice (automatic). */
    val subtitleTrackIndex: Int? = null,
    val audioDelayMs: Long = 0,
    val subtitleDelayMs: Long = 0,
) {
    companion object {
        const val SUBTITLES_OFF = -1
    }
}

/** A subtitle file that lives next to (or is manually attached to) a media item. */
data class ExternalSubtitle(
    val uri: String,
    val label: String,
    /** BCP-47 language tag, or null when it could not be inferred. */
    val language: String?,
    val mimeType: String,
    val isForced: Boolean = false,
    val isSdh: Boolean = false,
    /** True when the user picked the file explicitly (it should then be selected immediately). */
    val isUserAdded: Boolean = false,
)
