package app.mvoon.core.player

import app.mvoon.core.media.PlayableItem

enum class PlaybackStatus { IDLE, BUFFERING, READY, ENDED }

enum class RepeatMode { OFF, ONE, ALL }

data class VideoSize(val width: Int, val height: Int, val pixelWidthHeightRatio: Float = 1f) {
    /** Display aspect ratio (accounts for anamorphic pixels). */
    val aspectRatio: Float
        get() = if (height == 0) 0f else width * pixelWidthHeightRatio / height
    val isPortrait: Boolean get() = aspectRatio in 0.01f..0.99f
}

/** What is decoding right now — surfaced in the "Playback info" panel so codec claims are verifiable. */
data class DecoderInfo(
    val videoDecoder: String? = null,
    val audioDecoder: String? = null,
    val videoFormat: String? = null,
    val audioFormat: String? = null,
    val droppedFrames: Int = 0,
) {
    val isAudioSoftwareFfmpeg: Boolean get() = audioDecoder?.contains("ffmpeg", ignoreCase = true) == true
}

data class PlaybackState(
    val queue: List<PlayableItem> = emptyList(),
    val currentIndex: Int = 0,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val videoSize: VideoSize? = null,
    val audioDelayMs: Long = 0,
    val subtitleDelayMs: Long = 0,
    val isSeekable: Boolean = true,
    val isLive: Boolean = false,
    val videoEnabled: Boolean = true,
    val error: PlaybackError? = null,
    val decoders: DecoderInfo = DecoderInfo(),
    /** Which engine is playing ("System" or "VLC compatibility"). */
    val engineName: String = "System",
    val isCompatibilityEngine: Boolean = false,
    /** 100 = normal; up to 200 amplifies past the system volume. */
    val volumeBoostPercent: Int = 100,
    /** Set when the system player can't fully play this item; the compatibility engine may help. */
    val compatibilityReason: String? = null,
) {
    val currentItem: PlayableItem? get() = queue.getOrNull(currentIndex)
    val hasNext: Boolean get() = currentIndex < queue.lastIndex || (repeatMode == RepeatMode.ALL && queue.size > 1)
    val hasPrevious: Boolean get() = currentIndex > 0 || (repeatMode == RepeatMode.ALL && queue.size > 1)
}

enum class ErrorKind {
    FILE_NOT_FOUND,
    NO_PERMISSION,
    UNSUPPORTED_FORMAT,
    UNSUPPORTED_CODEC,
    DECODER_FAILED,
    NETWORK,
    CORRUPT_FILE,
    AUDIO_OUTPUT,
    DRM,
    UNKNOWN,
}

/** A playback failure explained in plain language, plus technical detail for the curious. */
data class PlaybackError(
    val kind: ErrorKind,
    val title: String,
    val message: String,
    val technical: String? = null,
    val canRetry: Boolean = true,
)

/** One-off notifications from the engine (snackbars / banners in the UI). */
sealed interface PlayerEvent {
    data class Warning(val message: String) : PlayerEvent
    data class Info(val message: String) : PlayerEvent
    data class ItemCompleted(val itemId: String) : PlayerEvent
    data object SleepTimerFired : PlayerEvent
}

sealed interface SleepTimerOption {
    data class After(val minutes: Int) : SleepTimerOption
    data object EndOfItem : SleepTimerOption
}

data class SleepTimerState(
    val active: Boolean = false,
    /** Wall-clock deadline (epoch millis) for [SleepTimerOption.After]. */
    val endsAtEpochMs: Long? = null,
    val endOfItem: Boolean = false,
)
