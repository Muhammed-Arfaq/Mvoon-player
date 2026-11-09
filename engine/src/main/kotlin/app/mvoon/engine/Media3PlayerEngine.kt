package app.mvoon.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import android.view.SurfaceView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import app.mvoon.core.media.ExternalSubtitle
import app.mvoon.core.media.MediaKind
import app.mvoon.core.media.PlayableItem
import app.mvoon.core.media.TrackMemory
import app.mvoon.core.player.CompatibilityPolicy
import app.mvoon.core.player.DecoderInfo
import app.mvoon.core.player.DecoderSupport
import app.mvoon.core.player.PlaybackState
import app.mvoon.core.player.PlaybackStatus
import app.mvoon.core.player.PlayerEngine
import app.mvoon.core.player.PlayerEvent
import app.mvoon.core.player.RepeatMode
import app.mvoon.core.player.SleepTimerOption
import app.mvoon.core.player.SleepTimerState
import app.mvoon.core.player.SubtitleFrame
import app.mvoon.core.player.TrackInfo
import app.mvoon.core.player.TrackSet
import app.mvoon.core.player.TrackType
import app.mvoon.core.player.VideoSize
import app.mvoon.core.settings.AudioDecoderMode
import app.mvoon.core.settings.CompatibilityMode
import app.mvoon.core.settings.SubtitleAutoMode
import app.mvoon.core.settings.SubtitleStyle
import app.mvoon.core.settings.VideoDecoderMode
import app.mvoon.engine.render.MvoonRenderersFactory
import app.mvoon.subtitles.LanguageTags
import app.mvoon.subtitles.SubtitleTrackChooser
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.log10
import kotlin.math.roundToInt

/** Settings the engine needs; pushed by the app whenever preferences change. */
data class EngineConfig(
    val videoDecoder: VideoDecoderMode = VideoDecoderMode.HARDWARE_FIRST,
    val audioDecoder: AudioDecoderMode = AudioDecoderMode.DEVICE_FIRST,
    val seekIncrementMs: Long = 10_000,
    val audioLanguages: List<String> = emptyList(),
    val subtitleMode: SubtitleAutoMode = SubtitleAutoMode.PREFERRED_LANGUAGES,
    val subtitleLanguages: List<String> = emptyList(),
    val skipSilence: Boolean = false,
    val autoPlayNext: Boolean = true,
    /** Only the compatibility engine uses this; Media3 subtitles are drawn by Mvoon's own overlay. */
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    /** Read by the router that decides when the compatibility engine takes over. */
    val compatibilityMode: CompatibilityMode = CompatibilityMode.ASK,
)

/**
 * [PlayerEngine] backed by Media3 ExoPlayer. One instance lives for the whole process; the underlying
 * [ExoPlayer] is created lazily on first use and released when the playback service goes away.
 */
class Media3PlayerEngine(private val context: Context) : AndroidPlayerEngine {

    private val main = Handler(Looper.getMainLooper())

    private var config = EngineConfig()
    private var player: ExoPlayer? = null
    private var renderers: MvoonRenderersFactory? = null
    private var builtWith: Pair<VideoDecoderMode, AudioDecoderMode>? = null
    private var surfaceView: SurfaceView? = null

    /** Called whenever a new ExoPlayer instance is created (the media session re-binds to it). */
    override var onSessionPlayerChanged: ((Player) -> Unit)? = null

    private var queue: List<PlayableItem> = emptyList()
    private var trackRefs: Map<String, Pair<TrackGroup, Int>> = emptyMap()
    private var selectionsAppliedFor: String? = null
    private var pendingExternalSelection: String? = null
    private var appliedAudioDelayMs = 0L
    private var requestedAudioDelayMs = 0L
    private var subtitleDelayMs = 0L
    private var videoEnabled = true
    private var decoders = DecoderInfo()

    /** Set when the current item has tracks but none we can decode (ExoPlayer itself raises no error). */
    private var trackError: app.mvoon.core.player.PlaybackError? = null

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private val _tracks = MutableStateFlow(TrackSet())
    override val tracks: StateFlow<TrackSet> = _tracks.asStateFlow()
    private val _subtitles = MutableStateFlow(SubtitleFrame.EMPTY)
    override val subtitles: StateFlow<SubtitleFrame> = _subtitles.asStateFlow()
    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()
    private val sleep = SleepTimerController(
        handler = main,
        isPlaying = { player?.isPlaying == true },
        setVolume = { player?.volume = it },
        pause = { player?.pause() },
        onFired = { _events.tryEmit(PlayerEvent.SleepTimerFired) },
    )
    override val sleepTimer: StateFlow<SleepTimerState> = sleep.state

    private val effectiveAudioDelayMs: Long
        get() = if (player?.currentTracks?.isTypeSelected(C.TRACK_TYPE_AUDIO) == true) appliedAudioDelayMs else 0L

    override val positionMs: Long
        get() {
            val p = player ?: return 0
            val raw = p.currentPosition - effectiveAudioDelayMs
            val duration = p.duration
            return if (duration > 0) raw.coerceIn(0, duration) else raw.coerceAtLeast(0)
        }

    override val bufferedPositionMs: Long get() = player?.bufferedPosition ?: 0

    /** The live ExoPlayer, creating it if needed. Exposed for the media session and diagnostics. */
    fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val modes = config.videoDecoder to config.audioDecoder
        val renderersFactory = MvoonRenderersFactory(context, modes.first, modes.second)
        val extractors = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val mediaSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http), extractors)
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
                .setExceedAudioConstraintsIfNecessary(true)
                .build()
        }
        val newPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes(MediaKind.VIDEO), true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(config.seekIncrementMs)
            .setSeekForwardIncrementMs(config.seekIncrementMs)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setUsePlatformDiagnostics(false)
            .build()
        newPlayer.addListener(listener)
        newPlayer.addAnalyticsListener(analytics)
        newPlayer.skipSilenceEnabled = config.skipSilence
        applyTrackParameters(newPlayer)
        surfaceView?.let(newPlayer::setVideoSurfaceView)
        player = newPlayer
        renderers = renderersFactory
        builtWith = modes
        renderersFactory.audioSink?.delayUs = appliedAudioDelayMs * 1000
        renderersFactory.subtitleRenderer?.delayUs = subtitleDelayMs * 1000
        onSessionPlayerChanged?.invoke(newPlayer)
        applyBoost()
        return newPlayer
    }

    override fun sessionPlayer(): Player = ensurePlayer()

    override fun release() = releasePlayer()

    /** Releases the decoder resources. A later [load] recreates the player transparently. */
    fun releasePlayer() {
        main.removeCallbacksAndMessages(null)
        releaseBoost()
        player?.run {
            removeListener(listener)
            removeAnalyticsListener(analytics)
            release()
        }
        player = null
        renderers = null
        queue = emptyList()
        _subtitles.value = SubtitleFrame.EMPTY
        _tracks.value = TrackSet()
        sleep.cancel()
        _state.value = PlaybackState()
    }

    val currentPlayer: ExoPlayer? get() = player

    override fun updateConfig(newConfig: EngineConfig) {
        val old = config
        config = newConfig
        val p = player ?: return
        p.skipSilenceEnabled = newConfig.skipSilence
        if (old.subtitleMode != newConfig.subtitleMode || old.subtitleLanguages != newConfig.subtitleLanguages ||
            old.audioLanguages != newConfig.audioLanguages
        ) {
            applyTrackParameters(p)
        }
    }

    override fun attachSurface(view: SurfaceView) {
        surfaceView = view
        player?.setVideoSurfaceView(view)
    }

    override fun detachSurface(view: SurfaceView) {
        if (surfaceView === view) surfaceView = null
        player?.clearVideoSurfaceView(view)
    }

    // ---------------------------------------------------------------- PlayerEngine

    override fun load(queue: List<PlayableItem>, startIndex: Int, playWhenReady: Boolean) {
        if (queue.isEmpty()) return
        // Decoder preferences are fixed at construction time: rebuild if they changed.
        if (player != null && builtWith != (config.videoDecoder to config.audioDecoder)) releasePlayerKeepingSurface()
        val p = ensurePlayer()
        val index = startIndex.coerceIn(0, queue.lastIndex)
        this.queue = queue
        selectionsAppliedFor = null
        pendingExternalSelection = null
        decoders = DecoderInfo()
        trackError = null
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverrides().build()
        applyTrackParameters(p)
        p.setAudioAttributes(audioAttributes(queue[index].kind), true)
        p.setWakeMode(if (queue.any { it.isStream }) C.WAKE_MODE_NETWORK else C.WAKE_MODE_LOCAL)
        p.pauseAtEndOfMediaItems = sleep.state.value.endOfItem || !config.autoPlayNext
        applyItemSync(queue[index])
        p.setMediaItems(queue.map(MediaItemMapper::toMediaItem), index, queue[index].startPositionMs.coerceAtLeast(0))
        p.prepare()
        p.playWhenReady = playWhenReady
        publishState()
    }

    /** Replaces an upcoming queue entry (e.g. once its sidecar subtitles were found). */
    override fun updateQueueItem(index: Int, item: PlayableItem) {
        val p = player ?: return
        if (index !in queue.indices || queue[index].id != item.id) return
        queue = queue.toMutableList().also { it[index] = item }
        if (index != p.currentMediaItemIndex && index < p.mediaItemCount) {
            p.replaceMediaItem(index, MediaItemMapper.toMediaItem(item))
        }
    }

    override fun play() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_ENDED) p.seekToDefaultPosition()
        if (p.playbackState == Player.STATE_IDLE) p.prepare()
        p.play()
    }

    override fun pause() {
        player?.pause()
    }

    override fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying || (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING)) pause() else play()
    }

    override fun seekTo(positionMs: Long) {
        val p = player ?: return
        val duration = p.duration
        val target = if (duration > 0) positionMs.coerceIn(0, duration) else positionMs.coerceAtLeast(0)
        p.seekTo(target)
    }

    override fun seekBy(deltaMs: Long) = seekTo(positionMs + deltaMs)

    override fun setScrubbing(active: Boolean) {
        player?.setScrubbingModeEnabled(active)
    }

    override fun next() {
        player?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
    }

    override fun previous() {
        val p = player ?: return
        // Like most players: restart the current item unless we're in its first few seconds.
        if (positionMs > 3_000 || !p.hasPreviousMediaItem()) p.seekTo(0) else p.seekToPreviousMediaItem()
    }

    override fun skipTo(index: Int) {
        val p = player ?: return
        if (index in 0 until p.mediaItemCount) p.seekToDefaultPosition(index)
    }

    override fun retry() {
        val p = player ?: return
        p.prepare()
        p.play()
    }

    override fun stop() {
        player?.run {
            stop()
            clearMediaItems()
        }
        setSleepTimer(null)
        queue = emptyList()
        _subtitles.value = SubtitleFrame.EMPTY
        publishState()
    }

    override fun setSpeed(speed: Float) {
        player?.setPlaybackParameters(PlaybackParameters(speed.coerceIn(0.25f, 4f)))
    }

    override fun setRepeatMode(mode: RepeatMode) {
        player?.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    }

    override fun setShuffle(enabled: Boolean) {
        player?.shuffleModeEnabled = enabled
    }

    override fun selectAudioTrack(trackId: String?) {
        val p = player ?: return
        val builder = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        val ref = trackId?.let(trackRefs::get)
        if (ref == null) builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        else builder.setOverrideForType(TrackSelectionOverride(ref.first, ref.second))
        p.trackSelectionParameters = builder.build()
    }

    override fun selectSubtitleTrack(trackId: String?) {
        val p = player ?: return
        val builder = p.trackSelectionParameters.buildUpon()
        val ref = trackId?.let(trackRefs::get)
        if (ref == null) {
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            _subtitles.value = SubtitleFrame.EMPTY
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(ref.first, ref.second))
        }
        p.trackSelectionParameters = builder.build()
    }

    override fun addExternalSubtitle(subtitle: ExternalSubtitle) {
        val p = player ?: return
        val index = p.currentMediaItemIndex
        val item = queue.getOrNull(index) ?: return
        val added = subtitle.copy(isUserAdded = true)
        val updated = item.copy(
            externalSubtitles = item.externalSubtitles.filterNot { it.uri == added.uri } + added,
            startPositionMs = positionMs,
        )
        queue = queue.toMutableList().also { it[index] = updated }
        pendingExternalSelection = added.uri
        selectionsAppliedFor = updated.id // keep the user's current audio choice; we select the new file below
        val wasPlaying = p.playWhenReady
        p.setMediaItems(queue.map(MediaItemMapper::toMediaItem), index, updated.startPositionMs)
        p.prepare()
        p.playWhenReady = wasPlaying
    }

    override fun setAudioDelayMs(delayMs: Long) {
        requestedAudioDelayMs = delayMs.coerceIn(-MAX_SYNC_MS, MAX_SYNC_MS)
        publishState()
        main.removeCallbacks(applyAudioDelay)
        main.postDelayed(applyAudioDelay, SYNC_DEBOUNCE_MS)
    }

    private val applyAudioDelay = Runnable {
        val p = player ?: return@Runnable
        val audioPosition = positionMs
        val decreased = requestedAudioDelayMs < appliedAudioDelayMs
        appliedAudioDelayMs = requestedAudioDelayMs
        renderers?.audioSink?.delayUs = appliedAudioDelayMs * 1000
        // The renderer only lets its clock move forward; a smaller offset needs a resync seek.
        if (decreased && p.playbackState != Player.STATE_IDLE) p.seekTo(audioPosition)
        publishState()
    }

    override fun setSubtitleDelayMs(delayMs: Long) {
        val newDelay = delayMs.coerceIn(-MAX_SYNC_MS, MAX_SYNC_MS)
        val increased = newDelay > subtitleDelayMs
        subtitleDelayMs = newDelay
        renderers?.subtitleRenderer?.delayUs = newDelay * 1000
        publishState()
        // Cues already consumed can only be re-read by seeking; earlier offsets need no seek.
        if (increased) {
            main.removeCallbacks(resyncSubtitles)
            main.postDelayed(resyncSubtitles, SYNC_DEBOUNCE_MS)
        }
    }

    private val resyncSubtitles = Runnable {
        player?.takeIf { it.playbackState != Player.STATE_IDLE }?.seekTo(positionMs)
    }

    override fun setVideoEnabled(enabled: Boolean) {
        val p = player ?: return
        if (videoEnabled == enabled) return
        videoEnabled = enabled
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
            .build()
        publishState()
    }

    override fun setSleepTimer(option: SleepTimerOption?) {
        sleep.set(option)
        // "End of item" uses ExoPlayer's own pause-at-end so it stops exactly at the boundary.
        player?.pauseAtEndOfMediaItems = option == SleepTimerOption.EndOfItem || !config.autoPlayNext
    }

    // ---------------------------------------------------------------- volume boost

    private var boostPercent = 100
    private var loudness: LoudnessEnhancer? = null
    private var loudnessSession = C.AUDIO_SESSION_ID_UNSET

    override fun setVolumeBoost(percent: Int) {
        boostPercent = percent.coerceIn(100, 200)
        applyBoost()
        publishState()
    }

    /**
     * Gain past the system volume through the platform LoudnessEnhancer on the player's audio
     * session, so it works for every decoder (device or FFmpeg). 200 % doubles the amplitude
     * (+6 dB); the effect's limiter keeps peaks from clipping harshly.
     */
    private fun applyBoost() {
        val p = player ?: return
        if (boostPercent <= 100) {
            loudness?.enabled = false
            return
        }
        val session = p.audioSessionId
        if (loudness == null || loudnessSession != session) {
            releaseBoost()
            loudness = runCatching { LoudnessEnhancer(session) }
                .onFailure { Log.w(TAG, "Volume boost unavailable on this device", it) }
                .getOrNull()
            loudnessSession = session
        }
        loudness?.run {
            setTargetGain((2000 * log10(boostPercent / 100.0)).roundToInt())
            enabled = true
        }
    }

    private fun releaseBoost() {
        loudness?.release()
        loudness = null
        loudnessSession = C.AUDIO_SESSION_ID_UNSET
    }

    // ---------------------------------------------------------------- Internals

    private fun releasePlayerKeepingSurface() {
        val view = surfaceView
        releaseBoost()
        player?.run {
            removeListener(listener)
            removeAnalyticsListener(analytics)
            release()
        }
        player = null
        renderers = null
        surfaceView = view
    }

    private fun audioAttributes(kind: MediaKind): AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(if (kind == MediaKind.AUDIO) C.AUDIO_CONTENT_TYPE_MUSIC else C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

    private fun subtitleLanguages(): List<String> =
        config.subtitleLanguages.ifEmpty { listOf(Locale.getDefault().language) }

    private fun applyTrackParameters(p: ExoPlayer) {
        val builder = p.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguages(*config.audioLanguages.toTypedArray())
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !videoEnabled)
        when (config.subtitleMode) {
            SubtitleAutoMode.OFF -> builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            SubtitleAutoMode.FORCED_ONLY -> builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguages()
                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            SubtitleAutoMode.PREFERRED_LANGUAGES, SubtitleAutoMode.ALWAYS -> builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguages(*subtitleLanguages().toTypedArray())
                .setIgnoredTextSelectionFlags(0)
                .setSelectUndeterminedTextLanguage(config.subtitleMode == SubtitleAutoMode.ALWAYS)
        }
        p.trackSelectionParameters = builder.build()
    }

    private fun applyItemSync(item: PlayableItem) {
        val memory = item.trackMemory ?: TrackMemory()
        main.removeCallbacks(applyAudioDelay)
        requestedAudioDelayMs = memory.audioDelayMs
        appliedAudioDelayMs = memory.audioDelayMs
        subtitleDelayMs = memory.subtitleDelayMs
        renderers?.audioSink?.delayUs = appliedAudioDelayMs * 1000
        renderers?.subtitleRenderer?.delayUs = subtitleDelayMs * 1000
    }

    private fun currentItem(): PlayableItem? {
        val p = player ?: return null
        val id = p.currentMediaItem?.mediaId ?: return null
        return queue.getOrNull(p.currentMediaItemIndex)?.takeIf { it.id == id } ?: queue.firstOrNull { it.id == id }
    }

    private fun rebuildTracks(tracks: Tracks) {
        val p = player ?: return
        val refs = HashMap<String, Pair<TrackGroup, Int>>()
        val video = ArrayList<TrackInfo>()
        val audio = ArrayList<TrackInfo>()
        val text = ArrayList<TrackInfo>()
        val audioSupport = audioRendererSupport(p)
        tracks.groups.forEachIndexed { groupIndex, group ->
            val type = when (group.type) {
                C.TRACK_TYPE_VIDEO -> TrackType.VIDEO
                C.TRACK_TYPE_AUDIO -> TrackType.AUDIO
                C.TRACK_TYPE_TEXT -> TrackType.SUBTITLE
                else -> return@forEachIndexed
            }
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val id = "g${groupIndex}t$i"
                refs[id] = group.mediaTrackGroup to i
                val list = when (type) {
                    TrackType.VIDEO -> video
                    TrackType.AUDIO -> audio
                    TrackType.SUBTITLE -> text
                }
                list += trackInfo(
                    id, type, format, list.size,
                    supported = group.isTrackSupported(i),
                    selected = group.isTrackSelected(i),
                    support = if (type == TrackType.AUDIO) audioSupport[format.sampleMimeType] else null,
                )
            }
        }
        trackRefs = refs
        _tracks.value = TrackSet(video, audio, text)
    }

    /** Which audio renderer (platform or FFmpeg) handles each MIME type in the current selection. */
    private fun audioRendererSupport(p: ExoPlayer): Map<String?, DecoderSupport> {
        val mapped = (p.trackSelector as? DefaultTrackSelector)?.currentMappedTrackInfo ?: return emptyMap()
        val result = HashMap<String?, DecoderSupport>()
        for (r in 0 until mapped.rendererCount) {
            if (mapped.getRendererType(r) != C.TRACK_TYPE_AUDIO) continue
            val isFfmpeg = mapped.getRendererName(r).contains("Ffmpeg", ignoreCase = true)
            val groups = mapped.getTrackGroups(r)
            for (g in 0 until groups.length) {
                val group = groups[g]
                for (t in 0 until group.length) {
                    val mime = group.getFormat(t).sampleMimeType
                    val handled = mapped.getTrackSupport(r, g, t) == C.FORMAT_HANDLED
                    if (handled && result[mime] == null) result[mime] = if (isFfmpeg) DecoderSupport.FFMPEG else DecoderSupport.PLATFORM
                }
            }
        }
        return result
    }

    private fun trackInfo(
        id: String,
        type: TrackType,
        format: Format,
        ordinal: Int,
        supported: Boolean,
        selected: Boolean,
        support: DecoderSupport?,
    ): TrackInfo {
        val language = LanguageTags.normalize(format.language)
        val codec = CodecNames.describe(format)
        val isExternal = format.id?.contains("external:") == true
        val label = buildString {
            val name = format.label?.takeIf { it.isNotBlank() && !isExternal }
                ?: LanguageTags.displayName(language)
                ?: format.label?.takeIf { it.isNotBlank() }
                ?: "Track ${ordinal + 1}"
            append(name)
        }
        return TrackInfo(
            id = id,
            type = type,
            label = label,
            language = language,
            codec = codec,
            mimeType = CodecNames.originalMime(format),
            channels = format.channelCount.coerceAtLeast(0),
            sampleRate = format.sampleRate.coerceAtLeast(0),
            bitrate = format.bitrate.coerceAtLeast(0),
            width = format.width.coerceAtLeast(0),
            height = format.height.coerceAtLeast(0),
            frameRate = format.frameRate.takeIf { it > 0 } ?: 0f,
            isSupported = supported,
            isSelected = selected,
            isExternal = isExternal,
            isForced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0,
            isDefault = format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
            isBitmapSubtitle = type == TrackType.SUBTITLE && CodecNames.isBitmapSubtitle(format),
            decoderSupport = when {
                !supported -> DecoderSupport.UNSUPPORTED
                support != null -> support
                else -> DecoderSupport.UNKNOWN
            },
        )
    }

    /** First time an item's tracks are known: restore remembered choices or apply the auto rules. */
    private fun applyInitialSelections() {
        val item = currentItem() ?: return
        val set = _tracks.value
        if (set.audio.isEmpty() && set.subtitles.isEmpty() && set.video.isEmpty()) return

        pendingExternalSelection?.let { uri ->
            pendingExternalSelection = null
            val track = set.subtitles.firstOrNull { it.isExternal && trackRefs[it.id]?.let { ref -> ref.first.getFormat(ref.second).id?.contains(uri) } == true }
            if (track != null && !track.isSelected) selectSubtitleTrack(track.id)
            return
        }
        if (selectionsAppliedFor == item.id) return
        selectionsAppliedFor = item.id

        val memory = item.trackMemory
        memory?.audioTrackIndex?.let { index ->
            set.audio.getOrNull(index)?.takeIf { it.isSupported && !it.isSelected }?.let { selectAudioTrack(it.id) }
        }
        val audioLanguage = (memory?.audioTrackIndex?.let(set.audio::getOrNull) ?: set.selectedAudio)?.language
        when (val remembered = memory?.subtitleTrackIndex) {
            TrackMemory.SUBTITLES_OFF -> if (set.selectedSubtitle != null) selectSubtitleTrack(null)
            null -> {
                val userAdded = item.externalSubtitles.lastOrNull { it.isUserAdded }
                val chosen = if (userAdded != null) {
                    set.subtitles.lastOrNull { it.isExternal }?.id
                } else {
                    SubtitleTrackChooser.choose(set.subtitles, config.subtitleMode, subtitleLanguages(), audioLanguage)
                }
                val current = set.selectedSubtitle?.id
                if (chosen != current) {
                    if (chosen == null) {
                        // Leave the selector's choice alone in OFF mode (already disabled) and FORCED mode.
                        if (config.subtitleMode == SubtitleAutoMode.PREFERRED_LANGUAGES) selectSubtitleTrack(null)
                    } else {
                        selectSubtitleTrack(chosen)
                    }
                }
            }
            else -> set.subtitles.getOrNull(remembered)?.takeIf { !it.isSelected }?.let { selectSubtitleTrack(it.id) }
        }
        warnAboutUnsupportedTracks(set)
    }

    private fun warnAboutUnsupportedTracks(set: TrackSet) {
        val videoUnsupported = set.video.isNotEmpty() && set.video.none { it.isSupported }
        val audioUnsupported = set.audio.isNotEmpty() && set.audio.none { it.isSupported }
        val nothingPlayable = (set.video.isEmpty() || videoUnsupported) && (set.audio.isEmpty() || audioUnsupported)
        when {
            nothingPlayable -> {
                val codecs = (set.video + set.audio).mapNotNull { it.codec }.distinct().joinToString(" and ")
                trackError = app.mvoon.core.player.PlaybackError(
                    app.mvoon.core.player.ErrorKind.UNSUPPORTED_CODEC,
                    "Can't play this file",
                    "This device can't decode its ${codecs.ifEmpty { "tracks" }}, and the bundled FFmpeg decoder doesn't " +
                        "include that format. See Settings → Format support for what this phone can play.",
                    technical = set.audio.plus(set.video).joinToString { "${it.mimeType}" },
                    canRetry = false,
                )
                player?.pause()
                publishState()
            }
            // The player screen turns PlaybackState.compatibilityReason into a persistent notice
            // that offers the compatibility engine, so no transient warning here.
            videoUnsupported || audioUnsupported -> publishState()
            else -> set.audio.filterNot { it.isSupported }.takeIf { it.isNotEmpty() }?.let { bad ->
                _events.tryEmit(PlayerEvent.Info("${bad.size} audio track(s) (${bad.mapNotNull { it.codec }.distinct().joinToString()}) can't be decoded on this device."))
            }
        }
    }

    private fun publishState() {
        val p = player
        if (p == null) {
            _state.value = PlaybackState()
            return
        }
        val video = p.videoSize
        val error = p.playerError?.let { ErrorMapper.map(it, currentItem()) } ?: trackError
        _state.value = PlaybackState(
            queue = queue,
            currentIndex = p.currentMediaItemIndex.coerceAtLeast(0),
            status = when (p.playbackState) {
                Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
                Player.STATE_READY -> PlaybackStatus.READY
                Player.STATE_ENDED -> PlaybackStatus.ENDED
                else -> PlaybackStatus.IDLE
            },
            isPlaying = p.isPlaying,
            playWhenReady = p.playWhenReady,
            durationMs = p.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0,
            speed = p.playbackParameters.speed,
            repeatMode = when (p.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            shuffle = p.shuffleModeEnabled,
            videoSize = if (video.width > 0 && video.height > 0) {
                VideoSize(video.width, video.height, video.pixelWidthHeightRatio)
            } else {
                null
            },
            audioDelayMs = requestedAudioDelayMs,
            subtitleDelayMs = subtitleDelayMs,
            isSeekable = p.isCurrentMediaItemSeekable,
            isLive = p.isCurrentMediaItemLive,
            videoEnabled = videoEnabled,
            error = error,
            decoders = decoders,
            engineName = "System",
            volumeBoostPercent = boostPercent,
            compatibilityReason = CompatibilityPolicy.reasonFor(error?.kind, _tracks.value, videoMatters = currentItem()?.kind != MediaKind.AUDIO),
        )
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishState()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (boostPercent > 100) applyBoost()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                val p = player ?: return
                val previousIndex = p.previousMediaItemIndex
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && previousIndex != C.INDEX_UNSET) {
                    queue.getOrNull(previousIndex)?.let { _events.tryEmit(PlayerEvent.ItemCompleted(it.id)) }
                }
            }
            decoders = DecoderInfo()
            trackError = null
            _subtitles.value = SubtitleFrame.EMPTY
            currentItem()?.let { item ->
                applyItemSync(item)
                player?.setAudioAttributes(audioAttributes(item.kind), true)
                // setMediaItems() only honours the first item's start position; resume later ones here.
                val resumable = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                if (resumable && item.startPositionMs > 0) {
                    val index = player?.currentMediaItemIndex ?: -1
                    if (index in queue.indices) queue = queue.toMutableList().also { it[index] = item.copy(startPositionMs = 0) }
                    player?.seekTo(item.startPositionMs)
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            rebuildTracks(tracks)
            if (!tracks.isEmpty) applyInitialSelections()
        }

        override fun onCues(cueGroup: CueGroup) {
            _subtitles.value = SubtitleFrame(CueMapper.map(cueGroup.cues))
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                currentItem()?.let { _events.tryEmit(PlayerEvent.ItemCompleted(it.id)) }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                currentItem()?.let { _events.tryEmit(PlayerEvent.ItemCompleted(it.id)) }
                if (sleep.onItemEnded()) player?.pauseAtEndOfMediaItems = !config.autoPlayNext
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Playback error ${error.errorCodeName}", error)
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player?.run {
                    seekToDefaultPosition()
                    prepare()
                }
            }
        }
    }

    private val analytics = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            Log.i(TAG, "Video decoder: $decoderName")
            decoders = decoders.copy(videoDecoder = decoderName)
            publishState()
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            Log.i(TAG, "Audio decoder: $decoderName")
            decoders = decoders.copy(audioDecoder = decoderName)
            publishState()
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            decoders = decoders.copy(videoFormat = describeVideo(format))
            publishState()
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            Log.i(TAG, "Audio format: ${format.sampleMimeType} ${format.channelCount}ch ${format.sampleRate}Hz")
            decoders = decoders.copy(audioFormat = describeAudio(format))
            publishState()
        }

        override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
            decoders = decoders.copy(droppedFrames = decoders.droppedFrames + droppedFrames)
        }
    }

    private fun describeVideo(format: Format): String = buildString {
        append(CodecNames.describe(format))
        if (format.width > 0) append(" · ${format.width}×${format.height}")
        if (format.frameRate > 0) append(" · %.3g fps".format(format.frameRate))
        format.colorInfo?.let { if (it.colorTransfer == C.COLOR_TRANSFER_ST2084 || it.colorTransfer == C.COLOR_TRANSFER_HLG) append(" · HDR") }
    }

    private fun describeAudio(format: Format): String = buildString {
        append(CodecNames.describe(format))
        app.mvoon.core.player.channelLayoutName(format.channelCount)?.let { append(" · ").append(it) }
        if (format.sampleRate > 0) append(" · ${format.sampleRate / 1000.0} kHz")
    }

    companion object {
        private const val TAG = "MvoonEngine"
        const val USER_AGENT = "Mvoon/1.0 (Android; Media3)"
        private const val MAX_SYNC_MS = 10_000L
        private const val SYNC_DEBOUNCE_MS = 350L
    }
}
