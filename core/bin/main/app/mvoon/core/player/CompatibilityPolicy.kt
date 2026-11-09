package app.mvoon.core.player

import app.mvoon.core.settings.CompatibilityMode

/**
 * When to bring in the compatibility engine (libVLC on Android) for a file the system player can't
 * handle. Pure logic so the rules are unit-tested and shared by every platform.
 */
object CompatibilityPolicy {

    enum class Decision { NONE, OFFER, SWITCH }

    /**
     * A short, user-facing explanation when the system player can't fully play the current item,
     * or null when it plays fine. [videoMatters] is false for audio items (cover art doesn't count).
     */
    fun reasonFor(errorKind: ErrorKind?, tracks: TrackSet, videoMatters: Boolean = true): String? {
        when (errorKind) {
            ErrorKind.UNSUPPORTED_FORMAT -> return "This file's format isn't supported by the system player."
            ErrorKind.UNSUPPORTED_CODEC -> return "This phone has no decoder for this file."
            ErrorKind.DECODER_FAILED -> return "This phone's decoder failed on this file."
            ErrorKind.CORRUPT_FILE -> return "The system player couldn't read this file."
            else -> Unit
        }
        val videoUnsupported = videoMatters && tracks.video.isNotEmpty() && tracks.video.none { it.isSupported }
        val audioUnsupported = tracks.audio.isNotEmpty() && tracks.audio.none { it.isSupported }
        return when {
            videoUnsupported -> "This phone can't decode the video (${tracks.video.first().codec ?: "unknown codec"})."
            audioUnsupported -> "This phone can't decode the audio (${tracks.audio.first().codec ?: "unknown codec"})."
            else -> null
        }
    }

    fun decide(reason: String?, mode: CompatibilityMode, alreadyUsingCompatibility: Boolean): Decision = when {
        reason == null || alreadyUsingCompatibility || mode == CompatibilityMode.OFF -> Decision.NONE
        mode == CompatibilityMode.AUTOMATIC -> Decision.SWITCH
        else -> Decision.OFFER
    }
}
