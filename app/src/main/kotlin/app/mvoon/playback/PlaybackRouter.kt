package app.mvoon.playback

import android.view.SurfaceView
import androidx.media3.common.Player
import app.mvoon.core.media.ExternalSubtitle
import app.mvoon.core.media.PlayableItem
import app.mvoon.core.media.TrackMemory
import app.mvoon.core.player.CompatibilityPolicy
import app.mvoon.core.player.PlaybackState
import app.mvoon.core.player.PlaybackStatus
import app.mvoon.core.player.PlayerEvent
import app.mvoon.core.player.RepeatMode
import app.mvoon.core.player.SleepTimerOption
import app.mvoon.core.player.SleepTimerState
import app.mvoon.core.player.SubtitleFrame
import app.mvoon.core.player.TrackSet
import app.mvoon.core.settings.CompatibilityMode
import app.mvoon.engine.AndroidPlayerEngine
import app.mvoon.engine.EngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * The engine the app talks to. Files play on the [system] engine (Media3 + FFmpeg audio) and move to
 * the [compatibility] engine (libVLC) only when the system one can't play them: on request
 * ([switchToCompatibility]), automatically in [CompatibilityMode.AUTOMATIC], or straight away for a
 * file remembered as needing it. libVLC is created on first use, never at app start.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackRouter(
    private val system: AndroidPlayerEngine,
    compatibilityFactory: () -> AndroidPlayerEngine,
    private val scope: CoroutineScope,
) : AndroidPlayerEngine {

    private var config = EngineConfig()
    private var surface: SurfaceView? = null
    private var videoEnabled = true
    private var volumeBoost = 100

    private val compatibility: AndroidPlayerEngine by lazy {
        compatibilityFactory().also { vlc ->
            vlc.updateConfig(config)
            vlc.onSessionPlayerChanged = { if (active.value === vlc) onSessionPlayerChanged?.invoke(it) }
            scope.launch { vlc.events.collect(_events::emit) }
        }
    }
    private var compatibilityCreated = false

    private val active = MutableStateFlow(system)
    private val current: AndroidPlayerEngine get() = active.value

    /** True while libVLC is the engine playing. */
    val isCompatibilityActive: Boolean get() = current !== system

    /** While engines hand over, observers keep seeing the last state instead of an empty queue. */
    private val frozen = MutableStateFlow<PlaybackState?>(null)

    override val state: StateFlow<PlaybackState> = combine(active.flatMapLatest { it.state }, frozen) { live, held -> held ?: live }
        .stateIn(scope, SharingStarted.Eagerly, system.state.value)
    override val tracks: StateFlow<TrackSet> = active.flatMapLatest { it.tracks }
        .stateIn(scope, SharingStarted.Eagerly, TrackSet())
    override val subtitles: StateFlow<SubtitleFrame> = active.flatMapLatest { it.subtitles }
        .stateIn(scope, SharingStarted.Eagerly, SubtitleFrame.EMPTY)
    override val sleepTimer: StateFlow<SleepTimerState> = active.flatMapLatest { it.sleepTimer }
        .stateIn(scope, SharingStarted.Eagerly, SleepTimerState())
    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    override var onSessionPlayerChanged: ((Player) -> Unit)? = null

    init {
        system.onSessionPlayerChanged = { if (current === system) onSessionPlayerChanged?.invoke(it) }
        scope.launch { system.events.collect(_events::emit) }
        // AUTOMATIC mode: take over as soon as the system engine reports it can't play the file.
        scope.launch {
            system.state.collect { s ->
                if (current !== system) return@collect
                if (CompatibilityPolicy.decide(s.compatibilityReason, config.compatibilityMode, false) == CompatibilityPolicy.Decision.SWITCH) {
                    switchToCompatibility()
                }
            }
        }
    }

    /**
     * Moves the current queue to the compatibility engine, keeping the item, position, speed,
     * repeat/shuffle, sync offsets and sleep timer.
     */
    fun switchToCompatibility(audioTrackIndex: Int? = null) {
        if (current !== system) return
        val s = system.state.value
        val item = s.currentItem ?: return
        // A file that already ended starts over; otherwise continue from where the system engine was.
        val position = if (s.status == PlaybackStatus.ENDED) 0 else system.positionMs.takeIf { it > 0 } ?: item.startPositionMs
        val timer = system.sleepTimer.value
        val memory = (item.trackMemory ?: TrackMemory()).copy(
            audioDelayMs = s.audioDelayMs,
            subtitleDelayMs = s.subtitleDelayMs,
            audioTrackIndex = audioTrackIndex ?: item.trackMemory?.audioTrackIndex,
        )
        val queue = s.queue.toMutableList().also {
            it[s.currentIndex] = item.copy(startPositionMs = position, trackMemory = memory, preferCompatibilityEngine = true)
        }
        handOver(compatibility) { vlc ->
            vlc.load(queue, s.currentIndex, playWhenReady = true)
            vlc.setSpeed(s.speed)
            vlc.setRepeatMode(s.repeatMode)
            vlc.setShuffle(s.shuffle)
            if (timer.active) {
                val remainingMin = timer.endsAtEpochMs?.let { ceil((it - System.currentTimeMillis()) / 60_000.0).toInt().coerceAtLeast(1) }
                vlc.setSleepTimer(if (timer.endOfItem || remainingMin == null) SleepTimerOption.EndOfItem else SleepTimerOption.After(remainingMin))
            }
        }
    }

    /**
     * Stops the previous engine first (hardware decoders are a limited resource and only one
     * producer may draw into the surface), then gives [target] the surface before it starts.
     * The published state is held meanwhile so the player screen never sees an empty queue.
     */
    private fun handOver(target: AndroidPlayerEngine, start: (AndroidPlayerEngine) -> Unit) {
        val previous = current
        if (previous === target) {
            start(target)
            return
        }
        frozen.value = state.value
        try {
            previous.stop()
            moveSurface(previous, target)
            start(target)
            makeActive(target)
        } finally {
            frozen.value = null
        }
    }

    private fun activate(target: AndroidPlayerEngine) {
        if (current === target) return
        moveSurface(current, target)
        makeActive(target)
    }

    private fun moveSurface(from: AndroidPlayerEngine, to: AndroidPlayerEngine) {
        if (to !== system) compatibilityCreated = true
        surface?.let {
            from.detachSurface(it)
            to.attachSurface(it)
        }
        to.setVideoEnabled(videoEnabled)
        to.setVolumeBoost(volumeBoost)
    }

    private fun makeActive(target: AndroidPlayerEngine) {
        active.value = target
        onSessionPlayerChanged?.invoke(target.sessionPlayer())
    }

    // ---------------------------------------------------------------- AndroidPlayerEngine

    override val positionMs: Long get() = current.positionMs
    override val bufferedPositionMs: Long get() = current.bufferedPositionMs

    override fun load(queue: List<PlayableItem>, startIndex: Int, playWhenReady: Boolean) {
        if (queue.isEmpty()) return
        val start = queue[startIndex.coerceIn(0, queue.lastIndex)]
        val target = if (start.preferCompatibilityEngine && config.compatibilityMode != CompatibilityMode.OFF) compatibility else system
        handOver(target) { it.load(queue, startIndex, playWhenReady) }
    }

    override fun stop() {
        current.stop()
        // Back to the battery-friendly engine for whatever plays next.
        activate(system)
    }

    override fun attachSurface(view: SurfaceView) {
        surface = view
        current.attachSurface(view)
    }

    override fun detachSurface(view: SurfaceView) {
        if (surface === view) surface = null
        current.detachSurface(view)
    }

    override fun updateConfig(newConfig: EngineConfig) {
        config = newConfig
        system.updateConfig(newConfig)
        if (compatibilityCreated) compatibility.updateConfig(newConfig)
    }

    override fun sessionPlayer(): Player = current.sessionPlayer()

    override fun release() {
        system.release()
        if (compatibilityCreated) compatibility.release()
        active.value = system
    }

    override fun setVideoEnabled(enabled: Boolean) {
        videoEnabled = enabled
        current.setVideoEnabled(enabled)
    }

    override fun updateQueueItem(index: Int, item: PlayableItem) = current.updateQueueItem(index, item)
    override fun play() = current.play()
    override fun pause() = current.pause()
    override fun togglePlayPause() = current.togglePlayPause()
    override fun seekTo(positionMs: Long) = current.seekTo(positionMs)
    override fun seekBy(deltaMs: Long) = current.seekBy(deltaMs)
    override fun setScrubbing(active: Boolean) = current.setScrubbing(active)
    override fun next() = current.next()
    override fun previous() = current.previous()
    override fun skipTo(index: Int) = current.skipTo(index)
    override fun retry() = current.retry()
    override fun setSpeed(speed: Float) = current.setSpeed(speed)
    override fun setRepeatMode(mode: RepeatMode) = current.setRepeatMode(mode)
    override fun setShuffle(enabled: Boolean) = current.setShuffle(enabled)
    override fun selectAudioTrack(trackId: String?) = current.selectAudioTrack(trackId)
    override fun selectSubtitleTrack(trackId: String?) = current.selectSubtitleTrack(trackId)
    override fun addExternalSubtitle(subtitle: ExternalSubtitle) = current.addExternalSubtitle(subtitle)
    override fun setAudioDelayMs(delayMs: Long) = current.setAudioDelayMs(delayMs)
    override fun setSubtitleDelayMs(delayMs: Long) = current.setSubtitleDelayMs(delayMs)
    override fun setSleepTimer(option: SleepTimerOption?) = current.setSleepTimer(option)

    override fun setVolumeBoost(percent: Int) {
        volumeBoost = percent.coerceIn(100, 200)
        current.setVolumeBoost(volumeBoost)
    }
}
