package app.mvoon.playback

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.mvoon.PlayerActivity
import app.mvoon.core.media.ExternalSubtitle
import app.mvoon.core.media.MediaKind
import app.mvoon.core.media.PlayableItem
import app.mvoon.core.media.StreamType
import app.mvoon.core.media.TrackMemory
import app.mvoon.core.player.PlayerEvent
import app.mvoon.core.settings.AppPreferences
import app.mvoon.core.settings.ResumeMode
import app.mvoon.engine.EngineConfig
import app.mvoon.engine.SubtitleFilePreparer
import app.mvoon.library.LibraryRepository
import app.mvoon.library.db.PlaybackStateEntity
import app.mvoon.library.db.LibraryRow
import app.mvoon.library.db.mediaKind
import app.mvoon.library.scan.MediaFiles
import app.mvoon.library.settings.SettingsRepository
import app.mvoon.subtitles.SubtitleFormats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Offered after opening something that was played before. */
data class ResumeHint(val itemId: String, val positionMs: Long, val resumed: Boolean)

/** Where the player screen last saw an item paused, and when (epoch ms). */
data class RestorePoint(val itemId: String, val positionMs: Long, val atMs: Long)

/**
 * Glue between the library and the engine: builds [PlayableItem]s (with resume position, remembered
 * track choices and sidecar subtitles), keeps the engine configured from settings, and records
 * history while anything plays — including in the background, when no screen is open.
 */
class PlaybackCoordinator(
    private val context: Context,
    private val engine: PlaybackRouter,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val subtitlePreparer: SubtitleFilePreparer,
    private val scope: CoroutineScope,
) {
    private val _preferences = MutableStateFlow(AppPreferences())
    val preferences: StateFlow<AppPreferences> = _preferences.asStateFlow()

    private val _preferencesLoaded = MutableStateFlow(false)
    val preferencesLoaded: StateFlow<Boolean> = _preferencesLoaded.asStateFlow()

    private val _resumeHint = MutableStateFlow<ResumeHint?>(null)
    val resumeHint: StateFlow<ResumeHint?> = _resumeHint.asStateFlow()

    private val _isPreparing = MutableStateFlow(false)
    val isPreparing: StateFlow<Boolean> = _isPreparing.asStateFlow()

    /** Explicit track choices made in the player, remembered per file. */
    private val userChoices = HashMap<String, TrackMemory>()
    private var enrichJob: Job? = null

    fun start() {
        scope.launch {
            settings.preferences.collect { prefs ->
                _preferences.value = prefs
                _preferencesLoaded.value = true
                engine.updateConfig(prefs.toEngineConfig())
            }
        }
        scope.launch {
            engine.events.collect { event ->
                if (event is PlayerEvent.ItemCompleted) {
                    engine.state.value.queue.firstOrNull { it.id == event.itemId }?.let { item ->
                        library.savePlayback(
                            item, 0, engine.state.value.durationMs, memoryFor(item.id), completed = true,
                            usedCompatibilityEngine = engine.state.value.isCompatibilityEngine,
                        )
                    }
                }
            }
        }
        // Save on pause / item change, and every few seconds while playing.
        scope.launch {
            engine.state.map { it.isPlaying to it.currentItem?.id }.distinctUntilChanged().collect { persistNow() }
        }
        scope.launch {
            while (isActive) {
                delay(5_000)
                if (engine.state.value.isPlaying) persistNow()
            }
        }
        subtitlePreparer.trimCache()
    }

    private fun AppPreferences.toEngineConfig() = EngineConfig(
        videoDecoder = playback.videoDecoder,
        audioDecoder = playback.audioDecoder,
        seekIncrementMs = playback.seekIncrementMs,
        audioLanguages = playback.audioLanguages,
        subtitleMode = subtitles.mode,
        subtitleLanguages = subtitles.languages,
        skipSilence = playback.skipSilence,
        autoPlayNext = playback.autoPlayNext,
        subtitleStyle = subtitles.style,
        compatibilityMode = playback.compatibilityEngine,
    )

    // ---------------------------------------------------------------- starting playback

    fun playRows(context: Context, rows: List<LibraryRow>, startIndex: Int, shuffle: Boolean = false, resume: Boolean = false) {
        if (rows.isEmpty()) return
        val items = rows.map { row ->
            val m = row.media
            PlayableItem(
                id = m.id, uri = m.uri, title = m.title,
                subtitle = if (m.mediaKind == MediaKind.AUDIO) listOfNotNull(m.artist, m.album).joinToString(" · ").ifBlank { null } else m.folderName,
                kind = m.mediaKind, mimeType = m.mimeType,
                durationMs = maxOf(m.durationMs, row.playedDurationMs ?: 0), sizeBytes = m.sizeBytes,
            )
        }
        play(context, items, startIndex, shuffle, forceResume = resume)
    }

    /** [resume] continues the first item where it was left, whatever the "Resume playback" setting says. */
    fun playItems(context: Context, items: List<PlayableItem>, startIndex: Int, shuffle: Boolean = false, resume: Boolean = false) =
        play(context, items, startIndex, shuffle, forceResume = resume)

    /**
     * Puts a queue back into the engine, paused where it was last saved. The player screen calls this
     * when it comes back to an empty engine: Android stops the playback service a while after playback
     * pauses in the background (Media3 lets it go after about ten minutes), or restarted the app.
     */
    fun restore(items: List<PlayableItem>, startIndex: Int, speed: Float, lastSeen: RestorePoint?) {
        if (items.isEmpty()) return
        enrichJob?.cancel()
        _isPreparing.value = true
        enrichJob = scope.launch {
            val index = startIndex.coerceIn(0, items.lastIndex)
            val item = items[index]
            val saved = withContext(Dispatchers.IO) { library.playbackState(item.id) }
            // The screen's own note is exact (history saves the last seconds of a file as "finished",
            // position 0); history wins only if it's newer, e.g. music that played on in the background.
            val seen = lastSeen?.takeIf { it.itemId == item.id && (saved == null || it.atMs >= saved.lastPlayedAt - 2_000) }
            val position = seen?.positionMs ?: saved?.takeIf { !it.completed }?.positionMs ?: 0
            val queue = items.toMutableList().also { it[index] = enrich(item, isStart = false).copy(startPositionMs = position) }
            engine.load(queue, index, playWhenReady = false)
            engine.setSpeed(speed)
            _isPreparing.value = false
        }
    }

    /** Files handed to us by other apps, the file picker or a share sheet. */
    fun playUris(context: Context, uris: List<Uri>, mimeType: String? = null) {
        if (uris.isEmpty()) return
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    keepAccess(uri)
                    if (uri.scheme == "http" || uri.scheme == "https" || uri.scheme == "rtsp") {
                        streamItem(uri.toString(), StreamType.AUTO, null)
                    } else {
                        val name = library.displayName(uri) ?: uri.lastPathSegment ?: "Media"
                        val type = mimeType ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()
                        val known = library.media(uri.toString())
                        PlayableItem(
                            id = uri.toString(), uri = uri.toString(),
                            title = known?.title ?: MediaFiles.titleFromFileName(name),
                            kind = MediaFiles.kindFor(name, type) ?: known?.mediaKind ?: MediaKind.VIDEO,
                            mimeType = type,
                            durationMs = known?.durationMs ?: 0,
                        )
                    }
                }
            }
            play(context, items, 0, shuffle = false, launchUi = false)
        }
    }

    fun playUrl(context: Context, url: String, type: StreamType, title: String?) =
        play(context, listOf(streamItem(url.trim(), type, title)), 0)

    private fun streamItem(url: String, type: StreamType, title: String?): PlayableItem {
        val uri = Uri.parse(url)
        val name = title?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }?.let(MediaFiles::titleFromFileName)
            ?: uri.host ?: url
        val inferred = when {
            type != StreamType.AUTO -> type
            url.contains(".m3u8", true) -> StreamType.HLS
            url.contains(".mpd", true) -> StreamType.DASH
            url.contains(".ism", true) -> StreamType.SMOOTH_STREAMING
            uri.scheme.equals("rtsp", true) -> StreamType.RTSP
            else -> StreamType.AUTO
        }
        val kind = MediaFiles.kindFor(uri.lastPathSegment ?: "", null) ?: MediaKind.VIDEO
        return PlayableItem(id = url, uri = url, title = name, subtitle = uri.host, kind = kind, isStream = true, streamType = inferred)
    }

    private fun play(
        context: Context,
        items: List<PlayableItem>,
        startIndex: Int,
        shuffle: Boolean = false,
        launchUi: Boolean = true,
        forceResume: Boolean = false,
    ) {
        if (launchUi) openPlayer(context)
        enrichJob?.cancel()
        _isPreparing.value = true
        persistNow()
        enrichJob = scope.launch {
            val started = android.os.SystemClock.elapsedRealtime()
            val index = startIndex.coerceIn(0, items.lastIndex)
            val first = enrich(items[index], isStart = true, forceResume = forceResume)
            android.util.Log.i(
                "MvoonStartup",
                "Prepared '${first.title}' in ${android.os.SystemClock.elapsedRealtime() - started} ms " +
                    "(${first.externalSubtitles.size} sidecar subtitle(s), resume at ${first.startPositionMs} ms)",
            )
            val queue = items.toMutableList().also { it[index] = first }
            engine.load(queue, index, playWhenReady = true)
            engine.setShuffle(shuffle)
            prefs().playback.let { if (it.rememberSpeed) engine.setSpeed(it.lastSpeed) else engine.setSpeed(1f) }
            _isPreparing.value = false
            // Find subtitles / resume data for the rest of the queue in the background.
            for (i in queue.indices) {
                if (i == index) continue
                val enriched = enrich(queue[i], isStart = false)
                if (enriched != queue[i]) engine.updateQueueItem(i, enriched)
            }
        }
    }

    fun openPlayer(context: Context) {
        context.startActivity(
            Intent(context, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    private suspend fun enrich(item: PlayableItem, isStart: Boolean, forceResume: Boolean = false): PlayableItem = withContext(Dispatchers.IO) {
        val saved = library.playbackState(item.id)
        val prefs = prefs()
        val savedPosition = saved?.takeIf { !it.completed && it.positionMs > 5_000 }?.positionMs ?: 0
        val resume = when {
            forceResume -> savedPosition
            prefs.playback.resumeMode == ResumeMode.ALWAYS -> savedPosition
            else -> 0
        }
        if (isStart) {
            _resumeHint.value = if (savedPosition > 0 && (forceResume || prefs.playback.resumeMode != ResumeMode.NEVER)) {
                ResumeHint(item.id, savedPosition, resumed = resume > 0)
            } else {
                null
            }
        }
        val memory = saved?.let {
            TrackMemory(it.audioTrackIndex, it.subtitleTrackIndex, it.audioDelayMs, it.subtitleDelayMs)
        }
        val subtitles = if (!item.isStream && item.kind == MediaKind.VIDEO) {
            withTimeoutOrNull(2_000) { findSubtitles(item.uri) } ?: emptyList()
        } else {
            emptyList()
        }
        item.copy(
            startPositionMs = resume,
            trackMemory = memory,
            externalSubtitles = (item.externalSubtitles + subtitles).distinctBy { it.uri },
            durationMs = maxOf(item.durationMs, saved?.durationMs ?: 0),
            preferCompatibilityEngine = saved?.engine == PlaybackStateEntity.ENGINE_COMPATIBILITY,
        )
    }

    private suspend fun findSubtitles(uri: String): List<ExternalSubtitle> {
        val charset = prefs().subtitles.fallbackCharset
        return library.findSubtitles(uri).map { subtitlePreparer.prepare(it, charset) }
    }

    /** User picked a subtitle file in the player. */
    fun attachSubtitle(uri: Uri) {
        scope.launch {
            val prepared = withContext(Dispatchers.IO) {
                keepAccess(uri)
                val name = library.displayName(uri) ?: "Subtitle"
                val mime = SubtitleFormats.mimeTypeFor(name) ?: SubtitleFormats.MIME_SUBRIP
                val language = app.mvoon.subtitles.ExternalSubtitleMatcher
                    .match(name, listOf(app.mvoon.subtitles.SubtitleCandidate(name, uri.toString(), inSubtitleFolder = true)))
                    .firstOrNull()?.language
                subtitlePreparer.prepare(
                    ExternalSubtitle(uri.toString(), name.substringBeforeLast('.'), language, mime, isUserAdded = true),
                    prefs().subtitles.fallbackCharset,
                )
            }
            engine.addExternalSubtitle(prepared)
        }
    }

    fun clearResumeHint() {
        _resumeHint.value = null
    }

    /**
     * A file was deleted from the device: take it out of what's playing and forget it everywhere.
     * The playing file is followed by the next one in the queue; if nothing comes after it, playback
     * stops. Returns false when nothing is left to play (the player screen then closes).
     */
    fun onFileDeleted(id: String): Boolean {
        val s = engine.state.value
        val index = s.queue.indexOfFirst { it.id == id }
        val keepsPlaying = when {
            index < 0 -> s.queue.isNotEmpty()
            index == s.currentIndex && index == s.queue.lastIndex -> {
                engine.stop()
                false
            }
            else -> {
                engine.removeFromQueue(id)
                true
            }
        }
        // After the queue changed, so a save for the deleted item can't land after it was forgotten.
        scope.launch { library.removeDeleted(id) }
        return keepsPlaying
    }

    // ---------------------------------------------------------------- remembering

    fun rememberAudioChoice(itemId: String, audioIndex: Int?) {
        userChoices[itemId] = (userChoices[itemId] ?: TrackMemory()).copy(audioTrackIndex = audioIndex)
    }

    fun rememberSubtitleChoice(itemId: String, subtitleIndex: Int?) {
        userChoices[itemId] = (userChoices[itemId] ?: TrackMemory()).copy(subtitleTrackIndex = subtitleIndex ?: TrackMemory.SUBTITLES_OFF)
    }

    fun rememberSpeed(speed: Float) {
        if (!prefs().playback.rememberSpeed) return
        scope.launch { settings.update { it.copy(playback = it.playback.copy(lastSpeed = speed)) } }
    }

    private fun memoryFor(itemId: String): TrackMemory? {
        val state = engine.state.value
        val choice = userChoices[itemId]
        val delays = if (state.currentItem?.id == itemId) state.audioDelayMs to state.subtitleDelayMs else null
        if (choice == null && delays == null) return null
        return TrackMemory(
            audioTrackIndex = choice?.audioTrackIndex,
            subtitleTrackIndex = choice?.subtitleTrackIndex,
            audioDelayMs = delays?.first ?: choice?.audioDelayMs ?: 0,
            subtitleDelayMs = delays?.second ?: choice?.subtitleDelayMs ?: 0,
        )
    }

    fun persistNow() {
        val state = engine.state.value
        val item = state.currentItem ?: return
        if (state.isLive) return
        val position = engine.positionMs
        val duration = state.durationMs
        if (duration <= 0 && position <= 0) return
        scope.launch {
            library.savePlayback(
                item, position, duration, memoryFor(item.id), completed = false, countPlay = false,
                usedCompatibilityEngine = state.isCompatibilityEngine,
            )
        }
    }

    private fun prefs() = _preferences.value

    /** Keep read access to picked/opened documents so "recently played" and resume keep working. */
    private fun keepAccess(uri: Uri) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}
