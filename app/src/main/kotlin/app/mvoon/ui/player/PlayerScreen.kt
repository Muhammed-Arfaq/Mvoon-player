package app.mvoon.ui.player

import androidx.compose.runtime.saveable.rememberSaveable
import app.mvoon.core.settings.CompatibilityMode
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mvoon.core.media.MediaKind
import app.mvoon.core.player.HoldSpeed
import app.mvoon.core.player.PlaybackStatus
import app.mvoon.core.player.PlayerEvent
import app.mvoon.core.player.SubtitleFrame
import app.mvoon.core.player.channelLayoutName
import app.mvoon.core.util.resolutionLabel
import app.mvoon.subtitles.SubtitlePlacementMath
import app.mvoon.ui.library.AddToPlaylistDialog
import app.mvoon.ui.library.rememberDeleteMedia
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.LockOpen
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.SunDim
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.VolumeX

private const val PREVIEW_TEXT = "Drag me anywhere\nPinch to resize"

@Composable
fun PlayerScreen(
    vm: PlayerViewModel,
    isInPip: Boolean,
    onBack: () -> Unit,
    onEnterPip: () -> Unit,
    onVideoRect: (Rect) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as Activity
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current

    val state by vm.state.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val liveFrame by vm.subtitles.collectAsStateWithLifecycle()
    val sleep by vm.sleepTimer.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val resumeHint by vm.resumeHint.collectAsStateWithLifecycle()
    val preparing by vm.isPreparing.collectAsStateWithLifecycle()
    val isFavorite by vm.isFavorite.collectAsStateWithLifecycle()
    val libraryEntry by vm.libraryEntry.collectAsStateWithLifecycle()

    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val item = state.currentItem
    val isAudio = item?.kind == MediaKind.AUDIO

    // ------------------------------------------------------------ position polling
    var position by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            if (!scrubbing) position = vm.engine.positionMs
            buffered = vm.engine.bufferedPositionMs
            delay(if (controlsVisible || isAudio) 200 else 1_000)
        }
    }
    LaunchedEffect(state.isPlaying) { view.keepScreenOn = state.isPlaying && !isAudio }

    // ------------------------------------------------------------ transient UI
    var interactionNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(controlsVisible, state.isPlaying, vm.panel, interactionNonce, scrubbing) {
        if (controlsVisible && state.isPlaying && vm.panel == null && !scrubbing && !isAudio) {
            delay(3_800)
            controlsVisible = false
        }
    }
    var toast by remember { mutableStateOf<String?>(null) }
    var compatibilityDismissedFor by rememberSaveable { mutableStateOf<String?>(null) }
    var toastNonce by remember { mutableIntStateOf(0) }
    var toastMillis by remember { mutableLongStateOf(1_800L) }
    fun showToast(text: String, millis: Long = 1_800L) {
        toast = text
        toastMillis = millis
        toastNonce++
    }
    LaunchedEffect(toastNonce) {
        if (toast != null) {
            delay(toastMillis)
            toast = null
        }
    }

    // ------------------------------------------------------------ the playing file: favourite, playlist, delete
    var addingToPlaylist by remember { mutableStateOf(false) }
    // Playback pauses while Android asks, and carries on afterwards (with the next file if it was deleted).
    var resumeAfterDelete by remember { mutableStateOf(false) }
    val deleteMedia = rememberDeleteMedia(
        onDeleted = { file ->
            when {
                !vm.onDeleted(file) -> onBack() // nothing left to play
                resumeAfterDelete -> vm.engine.play()
            }
        },
        onCancelled = { if (resumeAfterDelete) vm.engine.play() },
    )
    val fileActions = FileActions(
        favorite = isFavorite.takeIf { libraryEntry != null && libraryEntry?.id == item?.id },
        toggleFavorite = {
            when (vm.toggleFavorite()) {
                true -> showToast("Added to favourites")
                false -> showToast("Removed from favourites")
                null -> Unit
            }
        },
        addToPlaylist = {
            vm.panel = null
            addingToPlaylist = true
        },
        delete = vm.deletable()?.let { file ->
            {
                vm.panel = null
                resumeAfterDelete = vm.state.value.playWhenReady
                vm.engine.pause()
                deleteMedia(file)
            }
        },
    )
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is PlayerEvent.Warning -> showToast(event.message, 4_500)
                is PlayerEvent.Info -> showToast(event.message, 3_500)
                PlayerEvent.SleepTimerFired -> showToast("Sleep timer — playback paused")
                is PlayerEvent.ItemCompleted -> Unit
            }
        }
    }
    LaunchedEffect(resumeHint) {
        if (resumeHint != null) {
            delay(9_000)
            vm.dismissResumeHint()
        }
    }

    // ------------------------------------------------------------ gesture state
    var showUnlock by remember { mutableStateOf(false) }
    LaunchedEffect(showUnlock) {
        if (showUnlock) {
            delay(2_500)
            showUnlock = false
        }
    }
    var seekPreview by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var doubleTap by remember { mutableStateOf<Pair<TapSide, Int>?>(null) }
    var doubleTapNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(doubleTapNonce) {
        if (doubleTap != null) {
            delay(700)
            doubleTap = null
        }
    }
    // Press-and-hold fast playback (null while not holding) and the double-tap play/pause flash.
    var holdSpeed by remember { mutableStateOf<Float?>(null) }
    var holdShown by remember { mutableFloatStateOf(HoldSpeed.DEFAULT) }
    var playFlash by remember { mutableStateOf<Boolean?>(null) }
    var playFlashNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(playFlashNonce) {
        if (playFlash != null) {
            delay(700)
            playFlash = null
        }
    }
    var levelIndicator by remember { mutableStateOf<Pair<Boolean, Float>?>(null) } // brightness? to level
    var levelNonce by remember { mutableIntStateOf(0) }
    // Volume keys changed the boost (see PlayerActivity): show the combined level.
    LaunchedEffect(vm.volumeFlash) {
        if (vm.volumeFlash > 0) {
            levelIndicator = false to (1f + (vm.state.value.volumeBoostPercent - 100) / 100f)
            levelNonce++
        }
    }
    // Point out extra audio tracks once per file.
    val audioCount = tracks.audio.size
    val itemId = state.currentItem?.id
    LaunchedEffect(itemId, audioCount > 1) {
        if (itemId != null && audioCount > 1 && vm.audioHintShownFor != itemId && vm.resumeHint.value == null) {
            vm.audioHintShownFor = itemId
            delay(1_200)
            showToast("$audioCount audio tracks — tap the audio button to switch", 3_500)
        }
    }
    LaunchedEffect(levelNonce) {
        if (levelIndicator != null) {
            delay(900)
            levelIndicator = null
        }
    }
    var videoRect by remember { mutableStateOf(Rect.Zero) }

    // Subtitle placement: live while dragging, persisted per orientation when the gesture ends.
    val savedPlacement = if (landscape) prefs.subtitles.placementLandscape else prefs.subtitles.placementPortrait
    // One state object for the screen's lifetime (the gesture handler holds on to it); rotation swaps in
    // the other orientation's saved placement through the LaunchedEffect below.
    var livePlacement by remember { mutableStateOf(savedPlacement) }
    var draggingSubtitles by remember { mutableStateOf(false) }
    var rawPlacement by remember { mutableStateOf(savedPlacement) }
    var snapped by remember { mutableStateOf(false) }
    LaunchedEffect(savedPlacement) { if (!draggingSubtitles) livePlacement = savedPlacement }
    var subtitleBounds by remember { mutableStateOf<Rect?>(null) }
    var subtitleArea by remember { mutableStateOf(IntSize.Zero) }
    var stickyFrame by remember { mutableStateOf(SubtitleFrame.EMPTY) }
    val frame = if (draggingSubtitles && liveFrame.isEmpty) stickyFrame else liveFrame

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volumeLevel by remember { mutableFloatStateOf(0f) }
    var brightness by remember {
        mutableFloatStateOf(
            activity.window.attributes.screenBrightness.takeIf { it >= 0f }
                ?: (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f),
        )
    }

    val seekIncrement = prefs.playback.seekIncrementMs
    val currentState by rememberUpdatedState(state)
    val isLandscape by rememberUpdatedState(landscape)
    val boostAllowed by rememberUpdatedState(prefs.playback.volumeBoost)
    val rememberedHoldSpeed by rememberUpdatedState(prefs.playback.holdSpeed)
    val holdStepPx = with(density) { 40.dp.toPx() }

    val handler = remember(seekIncrement) {
        object : PlayerGestureHandler {
            var seekStartPosition = 0L
            var seekTarget = 0L
            var lastScrubAt = 0L
            var holdFrom = 1f
            var holdStart = 0
            var holdIndex = 0

            override fun onTap() {
                when {
                    vm.locked -> showUnlock = true
                    vm.panel != null -> vm.panel = null
                    else -> controlsVisible = !controlsVisible
                }
            }

            override fun onDoubleTap(side: TapSide, streak: Int) {
                when (side) {
                    TapSide.CENTER -> {
                        val s = currentState
                        val willPlay = !(s.isPlaying || (s.playWhenReady && s.status == PlaybackStatus.BUFFERING))
                        vm.engine.togglePlayPause()
                        playFlash = willPlay
                        playFlashNonce++
                    }
                    TapSide.LEFT, TapSide.RIGHT -> {
                        val sign = if (side == TapSide.RIGHT) 1 else -1
                        vm.engine.seekBy(sign * seekIncrement)
                        doubleTap = side to (streak * seekIncrement / 1000).toInt()
                        doubleTapNonce++
                    }
                }
            }

            override fun onSeekStart() {
                if (!currentState.isSeekable || currentState.durationMs <= 0) return
                seekStartPosition = vm.engine.positionMs
                seekTarget = seekStartPosition
                scrubbing = true
                if (currentState.currentItem?.isStream != true) vm.engine.setScrubbing(true)
            }

            override fun onSeekDrag(fractionOfWidth: Float) {
                val duration = currentState.durationMs
                if (!scrubbing || duration <= 0) return
                val range = if (duration < 90_000) duration else (duration * 0.35f).toLong().coerceIn(60_000, 300_000)
                seekTarget = (seekStartPosition + (fractionOfWidth * range).toLong()).coerceIn(0, duration)
                position = seekTarget
                seekPreview = seekTarget to (seekTarget - seekStartPosition)
                val now = System.currentTimeMillis()
                if (currentState.currentItem?.isStream != true && now - lastScrubAt > 90) {
                    lastScrubAt = now
                    vm.engine.seekTo(seekTarget)
                }
            }

            override fun onSeekEnd() {
                if (!scrubbing) return
                vm.engine.seekTo(seekTarget)
                vm.engine.setScrubbing(false)
                scrubbing = false
                seekPreview = null
            }

            override fun onBrightness(delta: Float) {
                brightness = (brightness + delta).coerceIn(0.01f, 1f)
                activity.window.attributes = activity.window.attributes.apply { screenBrightness = brightness }
                levelIndicator = true to brightness
                levelNonce++
            }

            // 0..1 is the system volume; with boost allowed, 1..2 continues into 100-200 % gain.
            override fun onVolume(delta: Float) {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                if (levelIndicator?.first != false) {
                    val system = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                    volumeLevel = system + if (system >= 1f) (currentState.volumeBoostPercent - 100) / 100f else 0f
                }
                val previous = volumeLevel
                var next = (volumeLevel + delta).coerceIn(0f, if (boostAllowed) 2f else 1f)
                // A short stop at 100 % so going into the boost is a deliberate second swipe.
                if (previous < 1f && next > 1f) {
                    next = 1f
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                volumeLevel = next
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (next.coerceAtMost(1f) * max).roundToInt(), 0)
                val boost = if (next > 1f) 100 + ((next - 1f) * 100).roundToInt() else 100
                if (boost != currentState.volumeBoostPercent) vm.engine.setVolumeBoost(boost)
                levelIndicator = false to volumeLevel
                levelNonce++
            }

            override fun onVerticalEnd() {
                levelNonce++
            }

            override fun onZoom(zoom: Float, pan: Offset, centroid: Offset) {
                vm.zoom = (vm.zoom * zoom).coerceIn(0.5f, 5f)
                val limitX = videoRect.width / 2f
                val limitY = videoRect.height / 2f
                vm.pan = Offset((vm.pan.x + pan.x).coerceIn(-limitX, limitX), (vm.pan.y + pan.y).coerceIn(-limitY, limitY))
                showToast("Zoom ${(vm.zoom * 100).roundToInt()}%")
            }

            override fun onZoomEnd() {
                if (abs(vm.zoom - 1f) < 0.06f) vm.resetZoom()
            }

            override fun onSubtitleStart() {
                draggingSubtitles = true
                controlsVisible = false
                rawPlacement = livePlacement
                stickyFrame = liveFrame
            }

            override fun onSubtitleTransform(pan: Offset, zoom: Float) {
                val bounds = subtitleBounds ?: return
                val margin = with(density) { 12.dp.toPx() }
                var p = SubtitlePlacementMath.drag(
                    rawPlacement, pan.x, pan.y, bounds.width, bounds.height,
                    subtitleArea.width.toFloat(), subtitleArea.height.toFloat(), margin,
                )
                if (zoom != 1f) p = SubtitlePlacementMath.zoom(p, zoom)
                rawPlacement = p
                val (snappedPlacement, didSnap) = SubtitlePlacementMath.snapToCenter(p)
                if (didSnap && !snapped) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                snapped = didSnap || snappedPlacement.centerX == 0.5f
                livePlacement = snappedPlacement
            }

            override fun onSubtitleEnd(moved: Boolean) {
                draggingSubtitles = false
                snapped = false
                if (moved) vm.saveSubtitlePlacement(isLandscape, livePlacement)
            }

            override fun onHoldStart(): Boolean {
                val s = currentState
                val playing = s.isPlaying || (s.playWhenReady && s.status == PlaybackStatus.BUFFERING)
                if (!playing || s.isLive || vm.panel != null) return false
                holdFrom = s.speed
                holdStart = HoldSpeed.startIndex(rememberedHoldSpeed)
                holdIndex = holdStart
                applyHoldSpeed()
                controlsVisible = false
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                return true
            }

            override fun onHoldSlide(dx: Float) {
                val next = HoldSpeed.indexAfterSlide(holdStart, dx, holdStepPx)
                if (next == holdIndex) return
                holdIndex = next
                applyHoldSpeed()
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }

            override fun onHoldEnd() {
                if (holdSpeed == null) return
                holdSpeed = null
                // Straight to the engine: a held speed-up is temporary, not the "remembered speed".
                vm.engine.setSpeed(holdFrom)
                vm.rememberHoldSpeed(HoldSpeed.STEPS[holdIndex])
            }

            private fun applyHoldSpeed() {
                val speed = HoldSpeed.STEPS[holdIndex]
                vm.engine.setSpeed(speed)
                holdSpeed = speed
                holdShown = speed
            }
        }
    }

    val actions = PlayerActions(
        back = onBack,
        playPause = { vm.engine.togglePlayPause(); interactionNonce++ },
        seekBy = { vm.engine.seekBy(it); interactionNonce++ },
        scrubStart = { scrubbing = true; if (item?.isStream != true) vm.engine.setScrubbing(true) },
        scrub = { position = it; if (item?.isStream != true) vm.engine.seekTo(it) },
        scrubEnd = { vm.engine.seekTo(it); position = it; vm.engine.setScrubbing(false); scrubbing = false; interactionNonce++ },
        previous = { vm.engine.previous() },
        next = { vm.engine.next() },
        lock = { vm.locked = true; controlsVisible = false; showUnlock = true },
        cycleAspect = {
            vm.aspectMode = vm.aspectMode.next()
            vm.resetZoom()
            showToast(vm.aspectMode.label)
            interactionNonce++
        },
        rotate = {
            showToast(vm.cycleRotation().label)
            vm.requestedOrientation()?.let { activity.requestedOrientation = it }
        },
        pip = onEnterPip,
        openPanel = { vm.panel = it },
    )

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.attachSubtitle(uri)
            showToast("Subtitle added")
        }
    }

    BackHandler(enabled = vm.panel != null || vm.locked || vm.subtitlePreview) {
        when {
            vm.panel != null -> vm.panel = null
            vm.subtitlePreview -> vm.subtitlePreview = false
            vm.locked -> showUnlock = true
        }
    }

    val safe: PaddingValues = WindowInsets.displayCutout.union(WindowInsets.systemBarsIgnoringVisibility).asPaddingValues()
    val pipSupported = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (isAudio && !isInPip) {
            AudioPlayerLayout(
                state, position, buffered, sleep, actions, vm::cycleRepeat, vm::toggleShuffle, safe,
                favorite = fileActions.favorite,
                onFavorite = fileActions.toggleFavorite,
            )
        } else {
            VideoSurface(
                engine = vm.engine,
                videoSize = state.videoSize,
                aspectMode = vm.aspectMode,
                zoom = vm.zoom,
                pan = vm.pan,
                onVideoRect = { videoRect = it; onVideoRect(it) },
            )

            // Subtitles render inside the safe area so they never sit under a notch or the nav bar,
            // and glide up out of the way when the bottom controls would cover them.
            val subtitleFontScale = if (isInPip) 0.45f else 1f
            val controlsZone = with(density) { (if (landscape) 118.dp else 176.dp).toPx() }
            val naturalBottom = livePlacement.bottomY * subtitleArea.height
            val showingControls = controlsVisible && !vm.locked && !isInPip && !vm.subtitlePreview && !draggingSubtitles
            val topZone = with(density) { (if (landscape) 84.dp else 96.dp).toPx() }
            val naturalTop = naturalBottom - (subtitleBounds?.height ?: 0f)
            val targetLift = when {
                !showingControls -> 0f
                naturalTop < topZone -> -(topZone - naturalTop) // push down below the title bar
                else -> (naturalBottom - (subtitleArea.height - controlsZone)).coerceAtLeast(0f)
            }
            val lift by animateFloatAsState(targetLift, tween(220), label = "subtitleLift")
            SubtitleOverlay(
                frame = frame,
                style = prefs.subtitles.style,
                placement = livePlacement,
                videoRect = videoRect,
                fontScale = subtitleFontScale,
                previewText = if (vm.subtitlePreview) PREVIEW_TEXT else null,
                highlight = draggingSubtitles || vm.subtitlePreview,
                snapped = snapped,
                onBlockBounds = { subtitleBounds = it },
                modifier = Modifier
                    .padding(if (isInPip) PaddingValues(0.dp) else safe)
                    .onSizeChanged { subtitleArea = it }
                    .graphicsLayer { translationY = -lift },
            )

            if (!isInPip) {
                val slop = with(density) { 18.dp.toPx() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .playerGestures(
                            config = GestureConfig(
                                locked = vm.locked,
                                doubleTapSeek = prefs.playback.doubleTapToSeek && state.isSeekable,
                                swipeSeek = prefs.playback.swipeToSeek && state.isSeekable,
                                brightness = prefs.playback.brightnessGesture,
                                volume = prefs.playback.volumeGesture,
                                pinchZoom = prefs.playback.pinchToZoom,
                                holdForSpeed = prefs.playback.holdForSpeed,
                            ),
                            isOnSubtitle = { point -> subtitleBounds?.inflate(slop)?.contains(point) == true },
                            handler = handler,
                        ),
                )
            }

            // Feedback layer
            doubleTap?.let { (side, seconds) ->
                Box(Modifier.fillMaxSize(), contentAlignment = if (side == TapSide.RIGHT) Alignment.CenterEnd else Alignment.CenterStart) {
                    DoubleTapFeedback(side, seconds)
                }
            }
            seekPreview?.let { (target, delta) ->
                SeekPreview(target, delta, state.durationMs, Modifier.align(Alignment.Center))
            }
            // With the controls showing, their own play button already reflects the change.
            if (!controlsVisible || vm.locked) {
                playFlash?.let { playing -> PlayPauseFlash(playing, playFlashNonce, Modifier.align(Alignment.Center)) }
            }
            AnimatedVisibility(
                visible = holdSpeed != null,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(safe).padding(top = 18.dp),
            ) {
                HoldSpeedPill(holdShown)
            }
            levelIndicator?.let { (isBrightness, level) ->
                LevelIndicator(
                    icon = if (isBrightness) (if (level > 0.5f) Lucide.Sun else Lucide.SunDim)
                    else (if (level <= 0f) Lucide.VolumeX else Lucide.Volume2),
                    level = level,
                    label = if (isBrightness) "Brightness" else "Volume",
                    maxLevel = if (!isBrightness && prefs.playback.volumeBoost) 2f else 1f,
                    modifier = Modifier.align(if (isBrightness) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 40.dp),
                )
            }

            if (!isInPip) {
                AnimatedVisibility(
                    visible = controlsVisible && !vm.locked && !vm.subtitlePreview,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    val video = tracks.selectedVideo
                    val audio = tracks.selectedAudio
                    PlayerControls(
                        model = ControlsModel(
                            title = item?.title ?: "",
                            meta = listOfNotNull(
                                video?.let { resolutionLabel(it.width, it.height) },
                                video?.codec,
                                audio?.let { listOfNotNull(it.codec?.substringBefore(" ("), channelLayoutName(it.channels)).joinToString(" ") },
                                if (state.isCompatibilityEngine) "VLC engine" else null,
                            ).joinToString("  ·  ").ifBlank { null },
                            isPlaying = state.isPlaying || (state.playWhenReady && state.status == PlaybackStatus.BUFFERING),
                            positionMs = position,
                            durationMs = state.durationMs,
                            bufferedMs = buffered,
                            seekable = state.isSeekable,
                            isLive = state.isLive,
                            hasQueue = state.queue.size > 1,
                            hasPrevious = state.hasPrevious,
                            hasNext = state.hasNext,
                            subtitlesOn = tracks.selectedSubtitle != null,
                            hasSubtitles = tracks.subtitles.isNotEmpty(),
                            audioTrackCount = tracks.audio.size,
                            audioLabel = tracks.selectedAudio?.let { a ->
                                val lang = a.language?.substringBefore('-')?.uppercase()?.take(3) ?: "Audio"
                                "$lang ${tracks.audio.indexOf(a) + 1}/${tracks.audio.size}"
                            },
                            speed = state.speed,
                            seekIncrementMs = seekIncrement,
                            pipSupported = pipSupported,
                            buffering = state.status == PlaybackStatus.BUFFERING && state.playWhenReady,
                        ),
                        actions = actions,
                        safePadding = safe,
                        compact = !landscape,
                    )
                }

                // Locked: only a lock button, shown briefly after a tap.
                AnimatedVisibility(
                    visible = vm.locked && showUnlock,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Button(
                        onClick = { vm.locked = false; showUnlock = false; controlsVisible = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.16f), contentColor = Color.White),
                    ) {
                        Icon(Lucide.LockOpen, null)
                        Text("  Tap to unlock")
                    }
                }
                if (vm.locked && !showUnlock) {
                    Icon(Lucide.Lock, "Screen locked", tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.align(Alignment.TopEnd).padding(safe).padding(20.dp))
                }

                if (vm.subtitlePreview) {
                    Button(
                        onClick = { vm.subtitlePreview = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        modifier = Modifier.align(Alignment.TopCenter).padding(safe).padding(top = 16.dp),
                    ) { Text("Done") }
                }
            }
        }

        val buffering = (state.status == PlaybackStatus.BUFFERING && state.playWhenReady) || (preparing && state.queue.isEmpty())
        if (buffering && (!controlsVisible || vm.locked || isInPip || isAudio)) {
            BufferingRing(Modifier.align(Alignment.Center))
        }

        if (!isInPip) {
            val compatibilityNotice = state.compatibilityReason?.takeIf {
                state.error == null && !state.isCompatibilityEngine && compatibilityDismissedFor != item?.id
            }
            AnimatedVisibility(
                visible = compatibilityNotice != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(safe).padding(top = 64.dp, start = 16.dp, end = 16.dp),
            ) {
                compatibilityNotice?.let { reason ->
                    CompatibilityNotice(
                        reason,
                        onUse = if (prefs.playback.compatibilityEngine != CompatibilityMode.OFF) vm::useCompatibilityEngine else null,
                        onDismiss = { compatibilityDismissedFor = item?.id },
                    )
                }
            }
            AnimatedVisibility(
                visible = resumeHint != null && resumeHint?.itemId == item?.id && compatibilityNotice == null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(safe).padding(top = 64.dp),
            ) {
                resumeHint?.let { hint ->
                    ResumePill(
                        hint.positionMs, hint.resumed,
                        onPrimary = { if (hint.resumed) vm.startOver() else vm.resumeAt(hint.positionMs) },
                        onDismiss = vm::dismissResumeHint,
                    )
                }
            }
            AnimatedVisibility(
                visible = toast != null,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut(),
                // Centred in the free space when a side panel is open (landscape), so it isn't hidden.
                modifier = Modifier.align(Alignment.TopCenter).padding(safe).padding(
                    top = if (landscape) 76.dp else 120.dp,
                    start = 24.dp,
                    end = 24.dp + if (landscape && vm.panel != null) 400.dp else 0.dp,
                ),
            ) { toast?.let { ToastPill(it) } }

            state.error?.let { error ->
                ErrorCard(
                    error = error,
                    onRetry = vm.engine::retry,
                    onClose = onBack,
                    onSkip = if (state.hasNext) ({ vm.engine.next(); vm.engine.retry() }) else null,
                    onUseCompatibility = vm::useCompatibilityEngine.takeIf {
                        state.compatibilityReason != null && prefs.playback.compatibilityEngine != CompatibilityMode.OFF
                    },
                )
            }

            PlayerPanelsLayer(vm, landscape, state, tracks, sleep, prefs, fileActions, onLoadSubtitle = {
                subtitlePicker.launch(arrayOf("application/x-subrip", "text/*", "application/octet-stream", "*/*"))
            }, onMoveSubtitles = {
                vm.panel = null
                vm.subtitlePreview = true
                controlsVisible = false
            })
        }
    }

    if (addingToPlaylist) {
        val playlists by vm.playlists.collectAsStateWithLifecycle()
        AddToPlaylistDialog(
            items = listOfNotNull(item),
            playlists = playlists,
            onDismiss = { addingToPlaylist = false },
            onAdd = { id ->
                vm.addToPlaylist(id)
                addingToPlaylist = false
                showToast("Added to “${playlists.firstOrNull { it.playlist.id == id }?.playlist?.name ?: "playlist"}”")
            },
            onCreate = { name ->
                vm.createPlaylist(name)
                addingToPlaylist = false
                showToast("Added to new playlist “${name.trim()}”")
            },
        )
    }

    DisposableEffect(Unit) { onDispose { vm.persist() } }
}

/** What the player can do with the playing file itself (from the options panel, or the heart for songs). */
private class FileActions(
    /** Null when the file can't be a favourite (it isn't in the library). */
    val favorite: Boolean?,
    val toggleFavorite: () -> Unit,
    val addToPlaylist: () -> Unit,
    /** Null when the file can't be deleted from here (a stream, or another app's file). */
    val delete: (() -> Unit)?,
)

@Composable
private fun PlayerPanelsLayer(
    vm: PlayerViewModel,
    landscape: Boolean,
    state: app.mvoon.core.player.PlaybackState,
    tracks: app.mvoon.core.player.TrackSet,
    sleep: app.mvoon.core.player.SleepTimerState,
    prefs: app.mvoon.core.settings.AppPreferences,
    fileActions: FileActions,
    onLoadSubtitle: () -> Unit,
    onMoveSubtitles: () -> Unit,
) {
    var shown by remember { mutableStateOf(vm.panel) }
    if (vm.panel != null) shown = vm.panel
    val close = { vm.panel = null }
    PanelHost(visible = vm.panel != null, landscape = landscape, onDismiss = close) {
        when (shown) {
            Panel.AUDIO -> AudioTracksPanel(
                tracks,
                onSelect = { vm.selectAudio(it); if (!it.isSupported && !state.isCompatibilityEngine) close() },
                onSync = { vm.panel = Panel.SYNC },
                onClose = close,
                canUseCompatibility = vm.canUseCompatibility,
                isCompatibilityEngine = state.isCompatibilityEngine,
            )
            Panel.SUBTITLES -> SubtitlesPanel(
                tracks,
                onSelect = { vm.selectSubtitle(it) },
                onLoadFile = onLoadSubtitle,
                onTiming = { vm.panel = Panel.SYNC },
                onStyle = { vm.panel = Panel.SUBTITLE_STYLE },
                onMove = onMoveSubtitles,
                onReset = { vm.resetSubtitlePlacement() },
                onClose = close,
                compatibilityMode = state.isCompatibilityEngine,
            )
            Panel.SUBTITLE_STYLE -> SubtitleStylePanel(
                prefs.subtitles.style,
                imageSubtitleSelected = tracks.selectedSubtitle?.isBitmapSubtitle == true,
                onChange = vm::updateSubtitleStyle,
                onClose = close,
                compatibilityMode = state.isCompatibilityEngine,
            )
            Panel.SYNC -> SyncPanel(state.audioDelayMs, state.subtitleDelayMs, vm.engine::setAudioDelayMs, vm.engine::setSubtitleDelayMs, close)
            Panel.SPEED -> SpeedPanel(state.speed, vm::setSpeed, close)
            Panel.SLEEP -> SleepPanel(sleep, vm::setSleepTimer, close)
            Panel.MORE -> MorePanel(
                state, sleep, prefs.playback.backgroundPlayback,
                favorite = fileActions.favorite,
                onFavorite = fileActions.toggleFavorite,
                onAddToPlaylist = fileActions.addToPlaylist,
                onDelete = fileActions.delete,
                onOpen = { vm.panel = it },
                onRepeat = vm::cycleRepeat,
                onShuffle = vm::toggleShuffle,
                onBackgroundPlay = { enabled -> vm.updatePlayback { it.copy(backgroundPlayback = enabled) } },
                onClose = close,
            )
            Panel.INFO -> InfoPanel(state, tracks, close)
            Panel.QUEUE -> QueuePanel(state.queue, state.currentIndex, onSkip = { vm.engine.skipTo(it) }, onClose = close)
            Panel.ASPECT -> AspectPanel(vm.aspectMode, vm.zoom, onSelect = { vm.aspectMode = it; vm.resetZoom() }, onResetZoom = vm::resetZoom, onClose = close)
            null -> Unit
        }
    }
}
