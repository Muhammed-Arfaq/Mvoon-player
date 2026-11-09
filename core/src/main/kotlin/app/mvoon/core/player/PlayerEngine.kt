package app.mvoon.core.player

import app.mvoon.core.media.ExternalSubtitle
import app.mvoon.core.media.PlayableItem
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The playback contract the UI is written against.
 *
 * Android implements it with Media3/ExoPlayer (+ FFmpeg audio). An iOS port would implement it with
 * AVFoundation (or libmpv for wider codec coverage) and a Windows port with libmpv / Media Foundation —
 * the UI, library and subtitle logic stay the same. All methods must be called from the main thread.
 */
interface PlayerEngine {
    val state: StateFlow<PlaybackState>
    val tracks: StateFlow<TrackSet>
    val subtitles: StateFlow<SubtitleFrame>
    val sleepTimer: StateFlow<SleepTimerState>
    val events: SharedFlow<PlayerEvent>

    /** Current position in ms, compensated for audio delay. Cheap; poll it while the UI is visible. */
    val positionMs: Long
    val bufferedPositionMs: Long

    fun load(queue: List<PlayableItem>, startIndex: Int = 0, playWhenReady: Boolean = true)
    fun play()
    fun pause()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)

    /** Enables fast, approximate seeking while the user drags the timeline. */
    fun setScrubbing(active: Boolean)
    fun next()
    fun previous()
    fun skipTo(index: Int)
    fun retry()
    fun stop()

    fun setSpeed(speed: Float)
    fun setRepeatMode(mode: RepeatMode)
    fun setShuffle(enabled: Boolean)

    /** `null` restores automatic (preference-based) selection. */
    fun selectAudioTrack(trackId: String?)

    /** `null` turns subtitles off. */
    fun selectSubtitleTrack(trackId: String?)
    fun addExternalSubtitle(subtitle: ExternalSubtitle)

    /** Positive = audio is heard later. */
    fun setAudioDelayMs(delayMs: Long)

    /** Positive = subtitles appear later. */
    fun setSubtitleDelayMs(delayMs: Long)

    /** Disable video decoding while playing audio in the background (saves battery). */
    fun setVideoEnabled(enabled: Boolean)

    fun setSleepTimer(option: SleepTimerOption?)

    /** Software gain on top of the system volume: 100 (off) to 200 %. */
    fun setVolumeBoost(percent: Int)
}
