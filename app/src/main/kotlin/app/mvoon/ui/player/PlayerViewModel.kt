package app.mvoon.ui.player

import androidx.compose.runtime.mutableIntStateOf
import app.mvoon.core.settings.CompatibilityMode
import android.app.Application
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.mvoon.appContainer
import app.mvoon.core.media.MediaKind
import app.mvoon.core.media.PlayableItem
import app.mvoon.core.media.StreamType
import app.mvoon.core.player.PlaybackStatus
import app.mvoon.core.player.RepeatMode
import app.mvoon.core.player.SleepTimerOption
import app.mvoon.core.player.TrackInfo
import app.mvoon.core.settings.OrientationMode
import app.mvoon.core.settings.PlaybackPreferences
import app.mvoon.core.settings.SubtitlePlacement
import app.mvoon.core.settings.SubtitleStyle
import app.mvoon.playback.RestorePoint
import kotlinx.coroutines.launch

enum class AspectMode(val label: String) {
    FIT("Fit"),
    FILL("Crop to fill"),
    STRETCH("Stretch"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
    RATIO_21_9("21:9"),
    ORIGINAL("Original size"),
    ;

    val forcedAspect: Float?
        get() = when (this) {
            RATIO_16_9 -> 16f / 9f
            RATIO_4_3 -> 4f / 3f
            RATIO_21_9 -> 21f / 9f
            else -> null
        }

    fun next(): AspectMode = entries[(ordinal + 1) % entries.size]
}

enum class RotationChoice(val label: String) { AUTO("Auto-rotate"), LANDSCAPE("Landscape"), PORTRAIT("Portrait") }

enum class Panel { AUDIO, SUBTITLES, SUBTITLE_STYLE, SYNC, SPEED, SLEEP, MORE, INFO, QUEUE, ASPECT }

class PlayerViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {

    private val container = app.appContainer
    val engine = container.engine
    private val coordinator = container.playback
    private val settings = container.settings

    val state = engine.state
    val tracks = engine.tracks
    val subtitles = engine.subtitles
    val sleepTimer = engine.sleepTimer
    val events = engine.events
    val preferences = coordinator.preferences
    val resumeHint = coordinator.resumeHint
    val isPreparing = coordinator.isPreparing

    // View state that should survive configuration changes.
    var aspectMode by mutableStateOf(AspectMode.FIT)
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    var locked by mutableStateOf(false)
    var rotation by mutableStateOf<RotationChoice?>(null)
    var panel by mutableStateOf<Panel?>(null)

    /** "Adjust position" mode: shows a sample subtitle the user can drag even when none is on screen. */
    var subtitlePreview by mutableStateOf(false)

    private val currentItemId: String? get() = state.value.currentItem?.id

    // What the screen was showing, so it can come back after the engine was released. The whole queue
    // is kept in memory; the current item also goes into saved state for when Android ends the process.
    private var restoreQueue: List<PlayableItem> = emptyList()
    private var restoreIndex = 0

    init {
        viewModelScope.launch {
            state.collect { s ->
                val item = s.currentItem ?: return@collect
                restoreQueue = s.queue
                restoreIndex = s.currentIndex
                saved[KEY_SPEED] = s.speed
                if (!s.isPlaying) notePosition()
                if (saved.get<String>(KEY_ID) != item.id) {
                    saved[KEY_ID] = item.id
                    saved[KEY_URI] = item.uri
                    saved[KEY_TITLE] = item.title
                    saved[KEY_SUBTITLE] = item.subtitle
                    saved[KEY_KIND] = item.kind.name
                    saved[KEY_MIME] = item.mimeType
                    saved[KEY_STREAM] = if (item.isStream) item.streamType.name else null
                }
            }
        }
    }

    /**
     * Called whenever the player screen becomes visible. If the engine was released meanwhile (Android
     * stopped the playback service during a long pause, or restarted the app), the file comes back
     * paused where it was, instead of an empty player whose Play button does nothing.
     */
    fun restoreIfReleased() {
        if (state.value.queue.isNotEmpty() || isPreparing.value) return
        val queue = restoreQueue.ifEmpty { listOfNotNull(savedItem()) }
        if (queue.isEmpty()) return
        val lastSeen = saved.get<String>(KEY_POSITION_ID)?.let { id ->
            RestorePoint(id, saved.get<Long>(KEY_POSITION) ?: 0, saved.get<Long>(KEY_POSITION_AT) ?: 0)
        }
        coordinator.restore(queue, if (restoreQueue.isEmpty()) 0 else restoreIndex, saved.get<Float>(KEY_SPEED) ?: 1f, lastSeen)
    }

    /** Remembers where the current item is (on pause and when the screen goes away). */
    private fun notePosition() {
        val s = state.value
        val item = s.currentItem ?: return
        // While a file is still loading its position isn't known yet.
        if (s.isLive || s.status != PlaybackStatus.READY) return
        saved[KEY_POSITION_ID] = item.id
        saved[KEY_POSITION] = engine.positionMs
        saved[KEY_POSITION_AT] = System.currentTimeMillis()
    }

    /** Something new was opened: the previous file must not come back. */
    fun forgetRestorePoint() {
        restoreQueue = emptyList()
        saved.remove<String>(KEY_ID)
    }

    private fun savedItem(): PlayableItem? {
        val id = saved.get<String>(KEY_ID) ?: return null
        val stream = saved.get<String>(KEY_STREAM)
        return PlayableItem(
            id = id,
            uri = saved.get<String>(KEY_URI) ?: id,
            title = saved.get<String>(KEY_TITLE).orEmpty(),
            subtitle = saved.get<String>(KEY_SUBTITLE),
            kind = runCatching { MediaKind.valueOf(saved.get<String>(KEY_KIND).orEmpty()) }.getOrDefault(MediaKind.VIDEO),
            mimeType = saved.get<String>(KEY_MIME),
            isStream = stream != null,
            streamType = stream?.let { runCatching { StreamType.valueOf(it) }.getOrNull() } ?: StreamType.AUTO,
        )
    }

    /**
     * Every audio track can be chosen. One the phone can't decode is played by the compatibility
     * engine, switched in at the same position with that track selected.
     */
    fun selectAudio(track: TrackInfo?) {
        val index = track?.let { tracks.value.audio.indexOf(it) }?.takeIf { it >= 0 }
        val needsCompatibility = track != null && !track.isSupported && !state.value.isCompatibilityEngine
        if (needsCompatibility && canUseCompatibility) {
            engine.switchToCompatibility(audioTrackIndex = index)
        } else {
            engine.selectAudioTrack(track?.id)
        }
        currentItemId?.let { id -> coordinator.rememberAudioChoice(id, index) }
    }

    val canUseCompatibility: Boolean get() = preferences.value.playback.compatibilityEngine != CompatibilityMode.OFF

    /** The file whose "several audio tracks" hint was already shown. */
    var audioHintShownFor: String? = null

    /** Bumped when a volume key changes the boost, so the screen can show the level. */
    var volumeFlash by mutableIntStateOf(0)

    /**
     * Volume keys past the system maximum raise the boost in 10 % steps up to 200 %, and lower it
     * again before the system volume goes down. Returns true when the key was used.
     */
    fun handleVolumeKey(up: Boolean, audioManager: android.media.AudioManager): Boolean {
        if (!preferences.value.playback.volumeBoost) return false
        val stream = android.media.AudioManager.STREAM_MUSIC
        val atMax = audioManager.getStreamVolume(stream) >= audioManager.getStreamMaxVolume(stream)
        val boost = state.value.volumeBoostPercent
        val next = when {
            up && atMax && boost < 200 -> boost + 10
            !up && boost > 100 -> boost - 10
            else -> return false
        }
        engine.setVolumeBoost(next)
        volumeFlash++
        return true
    }

    fun selectSubtitle(track: TrackInfo?) {
        engine.selectSubtitleTrack(track?.id)
        currentItemId?.let { id -> coordinator.rememberSubtitleChoice(id, track?.let { tracks.value.subtitles.indexOf(it) }?.takeIf { it >= 0 }) }
    }

    fun attachSubtitle(uri: Uri) = coordinator.attachSubtitle(uri)

    fun setSpeed(speed: Float) {
        engine.setSpeed(speed)
        coordinator.rememberSpeed(speed)
    }

    fun cycleRepeat() {
        engine.setRepeatMode(
            when (state.value.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            },
        )
    }

    fun toggleShuffle() = engine.setShuffle(!state.value.shuffle)

    fun setSleepTimer(option: SleepTimerOption?) = engine.setSleepTimer(option)

    /** Re-opens the current file with libVLC at the same position. */
    fun useCompatibilityEngine() = engine.switchToCompatibility()

    fun saveSubtitlePlacement(landscape: Boolean, placement: SubtitlePlacement) {
        viewModelScope.launch {
            settings.update { prefs ->
                val subs = prefs.subtitles
                prefs.copy(subtitles = if (landscape) subs.copy(placementLandscape = placement) else subs.copy(placementPortrait = placement))
            }
        }
    }

    fun resetSubtitlePlacement() {
        viewModelScope.launch {
            settings.update { prefs ->
                val defaults = app.mvoon.core.settings.SubtitlePreferences()
                prefs.copy(subtitles = prefs.subtitles.copy(placementLandscape = defaults.placementLandscape, placementPortrait = defaults.placementPortrait))
            }
        }
    }

    fun updateSubtitleStyle(transform: (SubtitleStyle) -> SubtitleStyle) {
        viewModelScope.launch { settings.update { it.copy(subtitles = it.subtitles.copy(style = transform(it.subtitles.style))) } }
    }

    fun updatePlayback(transform: (PlaybackPreferences) -> PlaybackPreferences) {
        viewModelScope.launch { settings.update { it.copy(playback = transform(it.playback)) } }
    }

    fun startOver() {
        engine.seekTo(0)
        coordinator.clearResumeHint()
    }

    fun resumeAt(positionMs: Long) {
        engine.seekTo(positionMs)
        coordinator.clearResumeHint()
    }

    fun dismissResumeHint() = coordinator.clearResumeHint()

    fun resetZoom() {
        zoom = 1f
        pan = Offset.Zero
    }

    fun cycleRotation(): RotationChoice {
        val next = when (rotation ?: RotationChoice.AUTO) {
            RotationChoice.AUTO -> RotationChoice.LANDSCAPE
            RotationChoice.LANDSCAPE -> RotationChoice.PORTRAIT
            RotationChoice.PORTRAIT -> RotationChoice.AUTO
        }
        rotation = next
        return next
    }

    /**
     * Orientation the activity should request, combining the user's choice and the video shape.
     * Null means "keep the current one" (e.g. while the video's shape is still unknown).
     */
    fun requestedOrientation(): Int? {
        val s = state.value
        if (s.currentItem?.kind == MediaKind.AUDIO) return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        rotation?.let {
            return when (it) {
                RotationChoice.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                RotationChoice.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                RotationChoice.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }
        return when (preferences.value.playback.orientation) {
            OrientationMode.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientationMode.FOLLOW_VIDEO -> when {
                s.videoSize == null -> null
                s.videoSize!!.isPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    fun persist() {
        notePosition()
        coordinator.persistNow()
    }

    /** Press-and-hold fast playback starts at the speed chosen last time. */
    fun rememberHoldSpeed(speed: Float) {
        if (speed != preferences.value.playback.holdSpeed) updatePlayback { it.copy(holdSpeed = speed) }
    }

    private companion object {
        const val KEY_ID = "restore_id"
        const val KEY_URI = "restore_uri"
        const val KEY_TITLE = "restore_title"
        const val KEY_SUBTITLE = "restore_subtitle"
        const val KEY_KIND = "restore_kind"
        const val KEY_MIME = "restore_mime"
        const val KEY_STREAM = "restore_stream"
        const val KEY_SPEED = "restore_speed"
        const val KEY_POSITION_ID = "restore_position_id"
        const val KEY_POSITION = "restore_position"
        const val KEY_POSITION_AT = "restore_position_at"
    }
}
