package app.mvoon.vlc

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import app.mvoon.core.media.ExternalSubtitle
import app.mvoon.core.media.PlayableItem
import app.mvoon.core.media.TrackMemory
import app.mvoon.core.player.DecoderInfo
import app.mvoon.core.player.DecoderSupport
import app.mvoon.core.player.ErrorKind
import app.mvoon.core.player.PlaybackError
import app.mvoon.core.player.PlaybackState
import app.mvoon.core.player.PlaybackStatus
import app.mvoon.core.player.PlayerEvent
import app.mvoon.core.player.RepeatMode
import app.mvoon.core.player.SleepTimerOption
import app.mvoon.core.player.SleepTimerState
import app.mvoon.core.player.SubtitleFrame
import app.mvoon.core.player.TrackInfo
import app.mvoon.core.player.TrackSet
import app.mvoon.core.player.TrackType
import app.mvoon.core.player.VideoSize
import app.mvoon.core.player.channelLayoutName
import app.mvoon.core.settings.SubtitleEdge
import app.mvoon.core.settings.SubtitleStyle
import app.mvoon.engine.AndroidPlayerEngine
import app.mvoon.engine.EngineConfig
import app.mvoon.engine.SleepTimerController
import app.mvoon.subtitles.LanguageTags
import app.mvoon.subtitles.SubtitleTrackChooser
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.io.File
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Compatibility engine backed by libVLC: plays containers and codecs the phone's own decoders
 * can't (WMV/ASF, VC-1, RealMedia, MPEG-2, DivX…). Hardware decoding is used when available,
 * with libVLC's FFmpeg software decoders as fallback. VLC draws subtitles itself, so
 * [subtitles] stays empty and subtitles can't be dragged in this mode.
 */
class VlcPlayerEngine(private val context: Context) : AndroidPlayerEngine {

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private var config = EngineConfig()
    private var libVlc: LibVLC? = null
    private var builtStyle: SubtitleStyle? = null
    private var player: MediaPlayer? = null
    private var surfaceView: SurfaceView? = null
    private var openFd: ParcelFileDescriptor? = null

    private var queue: List<PlayableItem> = emptyList()
    private var index = 0
    private var playWhenReady = false
    private var isPlaying = false
    private var status = PlaybackStatus.IDLE
    private var startPaused = false
    private var durationMs = 0L
    private var lastTimeMs = 0L
    private var speed = 1f
    private var repeatMode = RepeatMode.OFF
    private var shuffle = false
    private var audioDelayMs = 0L
    private var subtitleDelayMs = 0L
    private var videoEnabled = true
    private var scrubbing = false
    private var error: PlaybackError? = null
    private var videoSize: VideoSize? = null
    private var delaysAppliedFor = -1

    // Track selection and sidecar subtitles: VLC only accepts slaves once the input is running, and
    // ES ids that appear after that point are the external subtitles, in the order they were added.
    private var started = false
    private var selectionsAppliedFor = -1
    private var embeddedSpuIds: Set<Int>? = null
    private var sidecarsPending = false
    private val pendingSlaves = mutableListOf<PendingSlave>()
    private val attachedSidecars = mutableListOf<ExternalSubtitle>()
    private val applySelections = Runnable { applyInitialSelections() }

    private var clockStarted = false

    // The very first start after installation indexes the system fonts for subtitles (~10 s on
    // slow devices); say so instead of looking stuck.
    private val slowStartNotice = Runnable {
        if (!clockStarted && player != null && error == null) {
            _events.tryEmit(PlayerEvent.Info("Starting the compatibility engine… the first time takes a few seconds while it prepares fonts."))
        }
    }

    private class PendingSlave(val file: File, val subtitle: ExternalSubtitle, val select: Boolean)

    private val _state = MutableStateFlow(PlaybackState(engineName = ENGINE_NAME, isCompatibilityEngine = true))
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private val _tracks = MutableStateFlow(TrackSet())
    override val tracks: StateFlow<TrackSet> = _tracks.asStateFlow()
    override val subtitles: StateFlow<SubtitleFrame> = MutableStateFlow(SubtitleFrame.EMPTY).asStateFlow()
    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()
    private val sleep = SleepTimerController(
        handler = main,
        isPlaying = { player?.isPlaying == true },
        setVolume = { fadeFactor = it; applyVolume() },
        pause = { pause() },
        onFired = { _events.tryEmit(PlayerEvent.SleepTimerFired) },
    )
    override val sleepTimer: StateFlow<SleepTimerState> = sleep.state

    private val sessionPlayer by lazy { VlcSessionPlayer(this) }
    override var onSessionPlayerChanged: ((Player) -> Unit)? = null
    override fun sessionPlayer(): Player = sessionPlayer

    override val positionMs: Long
        get() {
            // Until its clock runs (e.g. a file opened paused at its resume point) libVLC reports 0;
            // the start / seek target is the real position then.
            val t = if (clockStarted) player?.time?.takeIf { it >= 0 } ?: lastTimeMs else lastTimeMs
            return if (durationMs > 0) t.coerceIn(0, durationMs) else t.coerceAtLeast(0)
        }
    override val bufferedPositionMs: Long get() = positionMs

    // ---------------------------------------------------------------- setup

    override fun updateConfig(newConfig: EngineConfig) {
        config = newConfig
        // libVLC reads subtitle style options only when the instance is created. Apply a change by
        // reloading the file at the same spot, debounced so dragging a slider reloads once.
        main.removeCallbacks(restyle)
        if (player != null && builtStyle != null && builtStyle != newConfig.subtitleStyle) main.postDelayed(restyle, RESTYLE_DEBOUNCE_MS)
    }

    private val restyle = Runnable {
        val item = queue.getOrNull(index) ?: return@Runnable
        if (player == null || builtStyle == config.subtitleStyle) return@Runnable
        val set = _tracks.value
        val memory = (item.trackMemory ?: TrackMemory()).copy(
            audioTrackIndex = set.audio.indexOfFirst { it.isSelected }.takeIf { it >= 0 },
            subtitleTrackIndex = set.subtitles.indexOfFirst { it.isSelected }.let { if (it >= 0) it else TrackMemory.SUBTITLES_OFF },
            audioDelayMs = audioDelayMs,
            subtitleDelayMs = subtitleDelayMs,
        )
        queue = queue.toMutableList().also { it[index] = item.copy(trackMemory = memory) }
        val position = positionMs
        val play = playWhenReady
        releaseMediaPlayer()
        open(index, position, play)
    }

    /** libVLC instance; rebuilt when the subtitle style changed and nothing is playing. */
    private fun ensureLibVlc(): LibVLC {
        val existing = libVlc
        if (existing != null && (builtStyle == config.subtitleStyle || player != null)) return existing
        player?.release()
        player = null
        existing?.release()
        val base = arrayListOf(
            "--no-video-title-show",
            "--audio-time-stretch",
            "--no-sub-autodetect-file",
            "--network-caching=1500",
            "--http-reconnect",
        )
        val created = runCatching { LibVLC(context, ArrayList(base + styleOptions(config.subtitleStyle))) }
            .onFailure { Log.w(TAG, "libVLC rejected subtitle style options; using defaults", it) }
            .getOrElse { LibVLC(context, base) }
        libVlc = created
        builtStyle = config.subtitleStyle
        return created
    }

    /** Maps Mvoon's subtitle style onto VLC's text renderer (freetype) options. */
    private fun styleOptions(style: SubtitleStyle): List<String> {
        val outline = when (style.edge) {
            SubtitleEdge.NONE, SubtitleEdge.DROP_SHADOW -> 0
            else -> when {
                style.outlineWidth < 0.08f -> 2
                style.outlineWidth < 0.16f -> 4
                else -> 6
            }
        }
        val shadow = style.edge == SubtitleEdge.DROP_SHADOW || style.edge == SubtitleEdge.OUTLINE_AND_SHADOW
        return listOfNotNull(
            "--sub-text-scale=${(style.textSizeSp / 20f * 100).roundToInt().coerceIn(30, 400)}",
            "--freetype-color=${style.textColor and 0xFFFFFF}",
            "--freetype-outline-color=${style.outlineColor and 0xFFFFFF}",
            "--freetype-outline-thickness=$outline",
            "--freetype-shadow-opacity=${if (shadow) 160 else 0}",
            "--freetype-background-color=${style.backgroundColor and 0xFFFFFF}",
            "--freetype-background-opacity=${(style.backgroundOpacity * 255).roundToInt()}",
            if (style.bold) "--freetype-bold" else null,
        )
    }

    private fun ensurePlayer(): MediaPlayer {
        player?.let { return it }
        val p = MediaPlayer(ensureLibVlc())
        p.setEventListener(eventListener)
        player = p
        attachVout()
        return p
    }

    // ---------------------------------------------------------------- video output

    private val layoutListener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> updateWindowSize(v as SurfaceView) }

    override fun attachSurface(view: SurfaceView) {
        surfaceView?.let {
            it.removeOnLayoutChangeListener(layoutListener)
            it.holder.removeCallback(surfaceCallback)
        }
        surfaceView = view
        surfaceLost = false
        view.addOnLayoutChangeListener(layoutListener)
        view.holder.addCallback(surfaceCallback)
        attachVout()
    }

    override fun detachSurface(view: SurfaceView) {
        view.removeOnLayoutChangeListener(layoutListener)
        view.holder.removeCallback(surfaceCallback)
        if (surfaceView !== view) return
        surfaceView = null
        player?.vlcVout?.let { if (it.areViewsAttached()) it.detachViews() }
    }

    // libVLC 3 doesn't rebuild a video output whose surface went away (PiP and multi-window
    // transitions, coming back from the background): once the new surface exists, restart the
    // video track so a fresh output is created on it.
    private var surfaceLost = false
    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            if (!surfaceLost) return
            surfaceLost = false
            main.post(::restartVideoOutput) // after libVLC's own callback has taken the surface
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            surfaceLost = true
        }
    }

    private fun restartVideoOutput() {
        val p = player ?: return
        if (!started || !videoEnabled || surfaceView == null) return
        p.setVideoTrackEnabled(false)
        p.setVideoTrackEnabled(true)
    }

    private fun attachVout() {
        val p = player ?: return
        val view = surfaceView ?: return
        val vout = p.vlcVout
        if (vout.areViewsAttached()) vout.detachViews()
        vout.setVideoView(view)
        vout.attachViews()
        updateWindowSize(view)
    }

    /**
     * Mvoon sizes the SurfaceView itself (aspect modes, zoom), so VLC is told to fill exactly
     * that area: window size = view size and forced aspect = view aspect.
     */
    private fun updateWindowSize(view: SurfaceView) {
        val p = player ?: return
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return
        p.vlcVout.setWindowSize(w, h)
        p.aspectRatio = "$w:$h"
        p.scale = 0f
    }

    // ---------------------------------------------------------------- PlayerEngine

    override fun load(queue: List<PlayableItem>, startIndex: Int, playWhenReady: Boolean) {
        if (queue.isEmpty()) return
        this.queue = queue
        open(startIndex.coerceIn(0, queue.lastIndex), queue[startIndex.coerceIn(0, queue.lastIndex)].startPositionMs, playWhenReady)
    }

    private fun open(target: Int, startMs: Long, play: Boolean) {
        val item = queue.getOrNull(target) ?: return
        val lib = ensureLibVlc()
        val p = ensurePlayer()
        index = target
        error = null
        videoSize = null
        durationMs = item.durationMs
        lastTimeMs = startMs.coerceAtLeast(0)
        delaysAppliedFor = -1
        started = false
        clockStarted = false
        main.removeCallbacks(slowStartNotice)
        main.postDelayed(slowStartNotice, SLOW_START_MS)
        selectionsAppliedFor = -1
        embeddedSpuIds = null
        sidecarsPending = false
        pendingSlaves.clear()
        attachedSidecars.clear()
        main.removeCallbacks(applySelections)
        _tracks.value = TrackSet()
        audioDelayMs = item.trackMemory?.audioDelayMs ?: 0
        subtitleDelayMs = item.trackMemory?.subtitleDelayMs ?: 0

        // libVLC dup()s the descriptor only once the input opens, so the previous one is closed
        // after the new media replaced it (set_media joins the old input thread).
        val previousFd = openFd
        openFd = null
        val media = try {
            createMedia(lib, item)
        } catch (e: SecurityException) {
            previousFd?.close()
            fail(ErrorKind.NO_PERMISSION, "Can't access this file", "Mvoon no longer has permission to read it. Open it again from your file manager.", e)
            return
        } catch (e: Exception) {
            previousFd?.close()
            fail(ErrorKind.FILE_NOT_FOUND, "File not found", "It may have been moved, renamed or deleted.", e)
            return
        }
        media.setHWDecoderEnabled(true, false)
        if (startMs > 0) media.addOption(":start-time=${startMs / 1000.0}")
        p.media = media
        media.release()
        previousFd?.close()

        playWhenReady = play
        startPaused = !play
        status = PlaybackStatus.BUFFERING
        p.rate = speed
        if (startPaused) p.setVolume(0) else applyVolume()
        p.play()
        if (play) gainAudioFocus()
        loadSidecarSubtitles(item.externalSubtitles, selectLast = item.externalSubtitles.any { it.isUserAdded })
        publish()
    }

    private fun createMedia(lib: LibVLC, item: PlayableItem): Media {
        val uri = Uri.parse(item.uri)
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            // libVLC can't open content:// itself; hand it a file descriptor instead.
            val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Cannot open $uri")
            openFd = fd
            Media(lib, fd.fileDescriptor)
        } else {
            Media(lib, uri)
        }
    }

    /** VLC can't read content:// subtitles either: copy them to the cache, then attach. */
    private fun loadSidecarSubtitles(subs: List<ExternalSubtitle>, selectLast: Boolean) {
        if (subs.isEmpty()) return
        val forIndex = index
        sidecarsPending = true
        io.execute {
            val files = subs.mapNotNull { sub -> materialize(sub)?.let { it to sub } }
            main.post {
                if (index != forIndex || player == null) return@post
                files.forEachIndexed { i, (file, sub) -> pendingSlaves += PendingSlave(file, sub, selectLast && i == files.lastIndex) }
                if (pendingSlaves.isEmpty()) {
                    sidecarsPending = false
                    applyInitialSelections()
                } else {
                    attachPendingSlaves()
                }
            }
        }
    }

    private fun attachPendingSlaves() {
        val p = player ?: return
        if (!started || pendingSlaves.isEmpty()) return
        if (embeddedSpuIds == null) embeddedSpuIds = p.spuTracks.orEmpty().map { it.id }.toSet()
        for (slave in pendingSlaves) {
            if (p.addSlave(IMedia.Slave.Type.Subtitle, Uri.fromFile(slave.file), slave.select)) attachedSidecars += slave.subtitle
        }
        pendingSlaves.clear()
        sidecarsPending = false
        // Slave ES show up asynchronously; choose tracks once they are listed.
        main.removeCallbacks(applySelections)
        main.postDelayed(applySelections, 600)
    }

    /** Same rules as the system engine: remembered choice, then language preferences. */
    private fun applyInitialSelections() {
        val item = queue.getOrNull(index) ?: return
        if (selectionsAppliedFor == index || !started || sidecarsPending) return
        refreshTracks()
        val set = _tracks.value
        if (set.audio.isEmpty() && set.subtitles.isEmpty()) return
        selectionsAppliedFor = index
        val memory = item.trackMemory
        val audio = memory?.audioTrackIndex?.let(set.audio::getOrNull)
            ?: config.audioLanguages.firstNotNullOfOrNull { lang -> set.audio.firstOrNull { LanguageTags.sameLanguage(lang, it.language) } }
        if (audio != null && !audio.isSelected) selectAudioTrack(audio.id)
        val audioLanguage = (audio ?: set.selectedAudio)?.language
        val subtitle = when (val remembered = memory?.subtitleTrackIndex) {
            TrackMemory.SUBTITLES_OFF -> null
            null -> if (item.externalSubtitles.any { it.isUserAdded }) {
                set.subtitles.lastOrNull { it.isExternal }?.id
            } else {
                SubtitleTrackChooser.choose(set.subtitles, config.subtitleMode, subtitleLanguages(), audioLanguage)
            }
            else -> set.subtitles.getOrNull(remembered)?.id
        }
        if (subtitle != _tracks.value.selectedSubtitle?.id) selectSubtitleTrack(subtitle)
    }

    private fun subtitleLanguages(): List<String> = config.subtitleLanguages.ifEmpty { listOf(Locale.getDefault().language) }

    private fun materialize(sub: ExternalSubtitle): File? = runCatching {
        val uri = Uri.parse(sub.uri)
        if (uri.scheme == ContentResolver.SCHEME_FILE) return@runCatching File(uri.path!!)
        val ext = sub.mimeType.let { if (it.contains("ssa")) "ass" else if (it.contains("vtt")) "vtt" else "srt" }
        val dir = File(context.cacheDir, "vlc-subtitles").apply { mkdirs() }
        val name = sub.label.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(60).ifBlank { "subtitle" }
        val out = File(dir, "${name}_${sub.uri.hashCode().toUInt()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use(input::copyTo) }
        out
    }.getOrNull()

    override fun play() {
        val p = player ?: return
        if (status == PlaybackStatus.ENDED || p.playerState == STATE_ENDED || p.playerState == STATE_STOPPED) {
            open(index, if (status == PlaybackStatus.ENDED) 0 else lastTimeMs, true)
            return
        }
        playWhenReady = true
        startPaused = false
        gainAudioFocus()
        p.play()
        publish()
    }

    override fun pause() {
        playWhenReady = false
        player?.pause()
        publish()
    }

    override fun togglePlayPause() = if (playWhenReady) pause() else play()

    override fun seekTo(positionMs: Long) {
        val p = player ?: return
        val target = if (durationMs > 0) positionMs.coerceIn(0, durationMs) else positionMs.coerceAtLeast(0)
        lastTimeMs = target
        if (status == PlaybackStatus.ENDED) {
            open(index, target, playWhenReady)
            return
        }
        p.setTime(target, scrubbing)
        publish()
    }

    override fun seekBy(deltaMs: Long) = seekTo(positionMs + deltaMs)

    override fun setScrubbing(active: Boolean) {
        scrubbing = active
    }

    private fun nextIndex(): Int? = when {
        index < queue.lastIndex -> index + 1
        repeatMode == RepeatMode.ALL && queue.isNotEmpty() -> 0
        else -> null
    }

    override fun next() {
        val n = if (shuffle && queue.size > 1) (queue.indices - index).random() else nextIndex()
        n?.let { open(it, queue[it].startPositionMs, true) }
    }

    override fun previous() {
        if (positionMs > 3_000 || index == 0) seekTo(0) else open(index - 1, 0, true)
    }

    override fun skipTo(index: Int) = skipTo(index, 0)

    internal fun skipTo(index: Int, positionMs: Long) {
        if (index in queue.indices) open(index, positionMs, true)
    }

    internal val seekIncrementMs: Long get() = config.seekIncrementMs

    override fun retry() = open(index, lastTimeMs, true)

    override fun stop() {
        // The MediaPlayer is released (not just stopped) so a changed subtitle style can rebuild
        // libVLC before the next file, and no decoder memory is held while idle.
        main.removeCallbacks(applySelections)
        main.removeCallbacks(restyle)
        main.removeCallbacks(slowStartNotice)
        releaseMediaPlayer()
        queue = emptyList()
        isPlaying = false
        playWhenReady = false
        status = PlaybackStatus.IDLE
        sleep.cancel()
        releaseAudioFocus()
        openFd?.close()
        openFd = null
        _tracks.value = TrackSet()
        publish()
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.25f, 4f)
        player?.rate = this.speed
        publish()
    }

    override fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
        publish()
    }

    override fun setShuffle(enabled: Boolean) {
        shuffle = enabled
        publish()
    }

    override fun selectAudioTrack(trackId: String?) {
        val p = player ?: return
        val id = trackId?.removePrefix("a:")?.toIntOrNull() ?: _tracks.value.audio.firstOrNull()?.id?.removePrefix("a:")?.toIntOrNull() ?: return
        p.setAudioTrack(id)
        refreshTracks()
    }

    override fun selectSubtitleTrack(trackId: String?) {
        val p = player ?: return
        p.setSpuTrack(trackId?.removePrefix("s:")?.toIntOrNull() ?: -1)
        refreshTracks()
    }

    override fun addExternalSubtitle(subtitle: ExternalSubtitle) {
        if (index in queue.indices) {
            queue = queue.toMutableList().also { it[index] = it[index].copy(externalSubtitles = it[index].externalSubtitles + subtitle) }
        }
        loadSidecarSubtitles(listOf(subtitle), selectLast = true)
    }

    override fun setAudioDelayMs(delayMs: Long) {
        audioDelayMs = delayMs.coerceIn(-MAX_SYNC_MS, MAX_SYNC_MS)
        player?.setAudioDelay(audioDelayMs * 1000)
        publish()
    }

    override fun setSubtitleDelayMs(delayMs: Long) {
        subtitleDelayMs = delayMs.coerceIn(-MAX_SYNC_MS, MAX_SYNC_MS)
        player?.setSpuDelay(subtitleDelayMs * 1000)
        publish()
    }

    override fun setVideoEnabled(enabled: Boolean) {
        if (videoEnabled == enabled) return
        videoEnabled = enabled
        player?.setVideoTrackEnabled(enabled)
        publish()
    }

    override fun setSleepTimer(option: SleepTimerOption?) = sleep.set(option)

    // libVLC amplifies natively: its volume goes up to 200 %.
    private var boostPercent = 100
    private var duckFactor = 1f
    private var fadeFactor = 1f

    override fun setVolumeBoost(percent: Int) {
        boostPercent = percent.coerceIn(100, 200)
        applyVolume()
        publish()
    }

    private fun applyVolume() {
        player?.setVolume((boostPercent * duckFactor * fadeFactor).roundToInt())
    }

    override fun updateQueueItem(index: Int, item: PlayableItem) {
        if (index in queue.indices && index != this.index && queue[index].id == item.id) {
            queue = queue.toMutableList().also { it[index] = item }
        }
    }

    private fun releaseMediaPlayer() {
        val p = player ?: return
        player = null
        p.setEventListener(null)
        p.stop()
        if (p.vlcVout.areViewsAttached()) p.vlcVout.detachViews()
        p.release()
        isPlaying = false
    }

    override fun release() {
        stop()
        surfaceView?.removeOnLayoutChangeListener(layoutListener)
        libVlc?.release()
        libVlc = null
        builtStyle = null
    }

    // ---------------------------------------------------------------- events

    private val eventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.Opening -> status = PlaybackStatus.BUFFERING
            MediaPlayer.Event.Buffering -> if (event.buffering >= 100f) {
                if (status == PlaybackStatus.BUFFERING && clockStarted) status = PlaybackStatus.READY
            } else if (!isPlaying) {
                status = PlaybackStatus.BUFFERING
            }
            MediaPlayer.Event.Playing -> {
                // "Playing" arrives before the first frame; stay in buffering until the clock moves.
                if (clockStarted) status = PlaybackStatus.READY
                isPlaying = true
                if (startPaused) {
                    startPaused = false
                    player?.pause()
                    applyVolume()
                }
                applyDelaysOnce()
                registerNoisyReceiver()
                updateVideoSize()
                refreshTracks()
                if (!started) {
                    started = true
                    attachPendingSlaves()
                    applyInitialSelections()
                }
            }
            MediaPlayer.Event.Paused -> {
                isPlaying = false
                if (!clockStarted) status = PlaybackStatus.READY
            }
            MediaPlayer.Event.Stopped -> isPlaying = false
            MediaPlayer.Event.TimeChanged -> {
                lastTimeMs = event.timeChanged
                if (!clockStarted) {
                    clockStarted = true
                    main.removeCallbacks(slowStartNotice)
                    if (status == PlaybackStatus.BUFFERING) status = PlaybackStatus.READY
                    publish()
                }
            }
            MediaPlayer.Event.LengthChanged -> durationMs = event.lengthChanged
            MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected -> refreshTracks()
            MediaPlayer.Event.Vout -> {
                updateVideoSize()
                surfaceView?.let(::updateWindowSize)
            }
            MediaPlayer.Event.EndReached -> main.post(::onItemEnded)
            MediaPlayer.Event.EncounteredError -> {
                isPlaying = false
                status = PlaybackStatus.IDLE
                error = PlaybackError(
                    ErrorKind.UNSUPPORTED_FORMAT,
                    "Even the compatibility engine can't play this",
                    "VLC couldn't decode this file either. It may be damaged, DRM-protected or in a very rare format.",
                    "libVLC EncounteredError",
                    canRetry = false,
                )
            }
        }
        if (event.type != MediaPlayer.Event.TimeChanged && event.type != MediaPlayer.Event.PositionChanged) publish()
    }

    private fun onItemEnded() {
        isPlaying = false
        queue.getOrNull(index)?.let { _events.tryEmit(PlayerEvent.ItemCompleted(it.id)) }
        lastTimeMs = durationMs
        val stopHere = sleep.onItemEnded()
        val next = when {
            stopHere -> null
            repeatMode == RepeatMode.ONE -> index
            !config.autoPlayNext -> null
            shuffle && queue.size > 1 -> (queue.indices - index).random()
            else -> nextIndex()
        }
        if (next != null) {
            open(next, if (next == index) 0 else queue[next].startPositionMs, true)
        } else {
            status = PlaybackStatus.ENDED
            playWhenReady = false
            publish()
        }
    }

    private fun applyDelaysOnce() {
        val p = player ?: return
        if (delaysAppliedFor == index) return
        delaysAppliedFor = index
        if (audioDelayMs != 0L) p.setAudioDelay(audioDelayMs * 1000)
        if (subtitleDelayMs != 0L) p.setSpuDelay(subtitleDelayMs * 1000)
        if (!videoEnabled) p.setVideoTrackEnabled(false)
    }

    private fun updateVideoSize() {
        val track = player?.currentVideoTrack ?: return
        if (track.width <= 0 || track.height <= 0) return
        val ratio = if (track.sarNum > 0 && track.sarDen > 0) track.sarNum.toFloat() / track.sarDen else 1f
        videoSize = VideoSize(track.width, track.height, ratio)
    }

    private fun refreshTracks() {
        val p = player ?: return
        val media = p.media
        val all = if (media != null) freshTracks(media).also { media.release() } else emptyList()
        val details = all.associateBy { it.id }
        // Some demuxers (ASF) give the media's track list different ids than the player's track
        // descriptions; fall back to the n-th track of the same type.
        val audioDetails = all.filterIsInstance<IMedia.AudioTrack>()
        val videoDetails = all.filterIsInstance<IMedia.VideoTrack>()
        val textDetails = all.filterIsInstance<IMedia.SubtitleTrack>()
        fun label(name: String?, track: IMedia.Track?, ordinal: Int): String =
            LanguageTags.displayName(track?.language) ?: name?.substringAfter(" - ", name)?.trim('[', ']', ' ')?.ifBlank { null }
                ?: "Track ${ordinal + 1}"

        val audio = p.audioTracks.orEmpty().filter { it.id >= 0 }.mapIndexed { i, d ->
            val t = details[d.id] as? IMedia.AudioTrack ?: audioDetails.getOrNull(i)
            TrackInfo(
                id = "a:${d.id}", type = TrackType.AUDIO, label = label(d.name, t, i),
                language = LanguageTags.normalize(t?.language), codec = VlcCodecNames.name(t?.fourcc ?: 0, t?.codec),
                channels = t?.channels ?: 0, sampleRate = t?.rate ?: 0, bitrate = t?.bitrate ?: 0,
                isSelected = d.id == p.audioTrack, decoderSupport = DecoderSupport.UNKNOWN,
            )
        }
        val embedded = embeddedSpuIds
        var externalOrdinal = 0
        val subtitles = p.spuTracks.orEmpty().filter { it.id >= 0 }.mapIndexed { i, d ->
            val t = details[d.id] ?: textDetails.getOrNull(i)
            val external = embedded != null && d.id !in embedded
            val sidecar = if (external) attachedSidecars.getOrNull(externalOrdinal++) else null
            TrackInfo(
                id = "s:${d.id}", type = TrackType.SUBTITLE, label = sidecar?.label ?: label(d.name, t, i),
                language = LanguageTags.normalize(sidecar?.language ?: t?.language), codec = VlcCodecNames.name(t?.fourcc ?: 0, t?.codec),
                isSelected = d.id == p.spuTrack, isExternal = external, isForced = sidecar?.isForced ?: false,
                isBitmapSubtitle = VlcCodecNames.isImageSubtitle(t?.fourcc ?: 0),
            )
        }
        val video = p.videoTracks.orEmpty().filter { it.id >= 0 }.mapIndexed { i, d ->
            val t = details[d.id] as? IMedia.VideoTrack ?: videoDetails.getOrNull(i)
            TrackInfo(
                id = "v:${d.id}", type = TrackType.VIDEO, label = d.name ?: "Video ${i + 1}",
                codec = VlcCodecNames.name(t?.fourcc ?: 0, t?.codec), width = t?.width ?: 0, height = t?.height ?: 0,
                frameRate = if (t != null && t.frameRateDen > 0) t.frameRateNum.toFloat() / t.frameRateDen else 0f,
                isSelected = d.id == p.videoTrack,
            )
        }
        _tracks.value = TrackSet(video, audio, subtitles)
        publish()
    }

    /**
     * libVLC's Java [Media] caches its track list on first read and only clears it after a parse,
     * so ES added later (second audio track, subtitle slaves) would never show. Read it natively.
     */
    private fun freshTracks(media: IMedia): List<IMedia.Track> {
        nativeGetTracks?.let { method ->
            runCatching { (method.invoke(media) as Array<*>?)?.filterIsInstance<IMedia.Track>() }.getOrNull()?.let { return it }
        }
        return (0 until media.trackCount).mapNotNull(media::getTrack)
    }

    /** libVLC's decoder counters for the current file. */
    data class Stats(
        val decodedVideo: Int,
        val displayedPictures: Int,
        val lostPictures: Int,
        val decodedAudio: Int,
        val playedAudioBuffers: Int,
        val lostAudioBuffers: Int,
    )

    fun stats(): Stats? {
        val media = player?.media ?: return null
        return try {
            media.stats?.let { Stats(it.decodedVideo, it.displayedPictures, it.lostPictures, it.decodedAudio, it.playedAbuffers, it.lostAbuffers) }
        } finally {
            media.release()
        }
    }

    private fun fail(kind: ErrorKind, title: String, message: String, cause: Throwable) {
        Log.w(TAG, "Cannot open media", cause)
        error = PlaybackError(kind, title, message, cause.toString(), canRetry = false)
        status = PlaybackStatus.IDLE
        isPlaying = false
        publish()
    }

    private fun publish() {
        val tracks = _tracks.value
        val video = tracks.selectedVideo
        val audio = tracks.selectedAudio
        _state.value = PlaybackState(
            queue = queue,
            currentIndex = index.coerceAtLeast(0),
            status = if (queue.isEmpty()) PlaybackStatus.IDLE else status,
            isPlaying = isPlaying && playWhenReady,
            playWhenReady = playWhenReady,
            durationMs = durationMs,
            speed = speed,
            repeatMode = repeatMode,
            shuffle = shuffle,
            videoSize = videoSize,
            audioDelayMs = audioDelayMs,
            subtitleDelayMs = subtitleDelayMs,
            isSeekable = player?.isSeekable ?: true,
            videoEnabled = videoEnabled,
            error = error,
            decoders = DecoderInfo(
                videoDecoder = video?.let { "VLC (hardware when available, FFmpeg otherwise)" },
                audioDecoder = audio?.let { "VLC" },
                videoFormat = video?.let { listOfNotNull(it.codec, if (it.width > 0) "${it.width}×${it.height}" else null).joinToString(" · ") },
                audioFormat = audio?.let { listOfNotNull(it.codec, channelLayoutName(it.channels), if (it.sampleRate > 0) "${it.sampleRate / 1000.0} kHz" else null).joinToString(" · ") },
                droppedFrames = stats()?.lostPictures ?: 0,
            ),
            engineName = ENGINE_NAME,
            isCompatibilityEngine = true,
            volumeBoostPercent = boostPercent,
        )
        sessionPlayer.refresh()
    }

    // ---------------------------------------------------------------- audio focus & noisy

    private var resumeOnFocusGain = false
    private var hasFocus = false
    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build(),
            )
            .setOnAudioFocusChangeListener({ change -> onFocusChange(change) }, main)
            .build()
    }

    private fun gainAudioFocus() {
        if (hasFocus) return
        hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun releaseAudioFocus() {
        if (hasFocus) audioManager.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
        unregisterNoisyReceiver()
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                hasFocus = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGain = playWhenReady
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                duckFactor = 0.3f
                applyVolume()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                duckFactor = 1f
                applyVolume()
                if (resumeOnFocusGain) play()
                resumeOnFocusGain = false
            }
        }
    }

    private var noisyRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    private fun registerNoisyReceiver() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(
            context, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyRegistered) return
        runCatching { context.unregisterReceiver(noisyReceiver) }
        noisyRegistered = false
    }

    companion object {
        const val ENGINE_NAME = "VLC compatibility"
        private val nativeGetTracks: Method? =
            runCatching { Media::class.java.getDeclaredMethod("nativeGetTracks").apply { isAccessible = true } }.getOrNull()
        private const val TAG = "MvoonVlc"
        private const val MAX_SYNC_MS = 10_000L
        private const val RESTYLE_DEBOUNCE_MS = 900L
        private const val SLOW_START_MS = 3_000L
        // libvlc_state_t values
        private const val STATE_STOPPED = 5
        private const val STATE_ENDED = 6
    }
}
