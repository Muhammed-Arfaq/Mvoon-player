package app.mvoon.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

enum class TapSide { LEFT, CENTER, RIGHT }

/** Which gestures are active right now (from settings + player state). */
data class GestureConfig(
    val locked: Boolean = false,
    val doubleTapSeek: Boolean = true,
    val swipeSeek: Boolean = true,
    val brightness: Boolean = true,
    val volume: Boolean = true,
    val pinchZoom: Boolean = true,
    val subtitleGestures: Boolean = true,
    val holdForSpeed: Boolean = true,
)

interface PlayerGestureHandler {
    fun onTap()
    fun onDoubleTap(side: TapSide, streak: Int)
    fun onSeekStart()
    fun onSeekDrag(fractionOfWidth: Float)
    fun onSeekEnd()
    fun onBrightness(delta: Float)
    fun onVolume(delta: Float)
    fun onVerticalEnd()
    fun onZoom(zoom: Float, pan: Offset, centroid: Offset)
    fun onZoomEnd()
    fun onSubtitleStart()
    fun onSubtitleTransform(pan: Offset, zoom: Float)
    fun onSubtitleEnd(moved: Boolean)

    /** A finger rests on the video: start fast playback. Returns false when it doesn't apply (paused, live). */
    fun onHoldStart(): Boolean

    /** The holding finger moved [dx] pixels sideways from where it went down. */
    fun onHoldSlide(dx: Float)
    fun onHoldEnd()
}

private enum class Mode { UNDECIDED, SEEK, BRIGHTNESS, VOLUME, ZOOM, HOLD, IGNORED }

/**
 * One gesture detector for the whole video area. The first finger's position decides the owner:
 * touches that start on the subtitle block move/resize subtitles; everything else drives playback.
 * This keeps the two gesture families from ever fighting over the same touch.
 */
fun Modifier.playerGestures(
    config: GestureConfig,
    isOnSubtitle: (Offset) -> Boolean,
    handler: PlayerGestureHandler,
): Modifier = pointerInput(config) {
    val edgeGuard = 28.dp.toPx() // system gesture areas (status bar pull-down, home bar)
    val streakWindow = 650L
    var lastSeekTapAt = 0L
    var lastSeekSide: TapSide? = null
    var streak = 0

    fun sideOf(x: Float): TapSide = when {
        x < size.width * 0.38f -> TapSide.LEFT
        x > size.width * 0.62f -> TapSide.RIGHT
        else -> TapSide.CENTER
    }

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)

        if (config.locked) {
            if (waitForUpOrCancellation() != null) handler.onTap()
            return@awaitEachGesture
        }

        // Rapid taps after a double-tap keep adding seek steps (no need to double-tap again).
        val downSide = sideOf(down.position.x)
        if (config.doubleTapSeek && downSide != TapSide.CENTER && downSide == lastSeekSide &&
            down.uptimeMillis - lastSeekTapAt < streakWindow
        ) {
            val up = waitForUpOrCancellation()
            if (up != null) {
                streak++
                lastSeekTapAt = up.uptimeMillis
                handler.onDoubleTap(downSide, streak)
            }
            return@awaitEachGesture
        }

        if (config.subtitleGestures && isOnSubtitle(down.position)) {
            handleSubtitleGesture(handler)
            return@awaitEachGesture
        }

        val start = down.position
        val inEdgeZone = start.y < edgeGuard || start.y > size.height - edgeGuard
        var mode = Mode.UNDECIDED
        var lastY = start.y
        // One finger resting still for a long-press moment turns into fast playback until it lifts.
        val holdAt = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
        var holdPossible = config.holdForSpeed
        var now = down.uptimeMillis

        try {
            while (true) {
                val event = if (mode == Mode.UNDECIDED && holdPossible) {
                    withTimeoutOrNull((holdAt - now).coerceAtLeast(1L)) { awaitPointerEvent() }
                } else {
                    awaitPointerEvent()
                }
                if (event == null) {
                    holdPossible = false
                    if (handler.onHoldStart()) mode = Mode.HOLD
                    continue
                }
                now = event.changes.maxOf { it.uptimeMillis }
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2) holdPossible = false

                if (pressed.size >= 2 && config.pinchZoom && (mode == Mode.UNDECIDED || mode == Mode.ZOOM)) {
                    mode = Mode.ZOOM
                    handler.onZoom(event.calculateZoom(), event.calculatePan(), event.calculateCentroid(useCurrent = true))
                    event.changes.forEach(PointerInputChange::consume)
                    continue
                }

                val change = event.changes.firstOrNull { it.id == down.id } ?: pressed.first()
                when (mode) {
                    Mode.UNDECIDED -> {
                        val delta = change.position - start
                        if (delta.getDistance() > viewConfiguration.touchSlop) {
                            mode = when {
                                abs(delta.x) > abs(delta.y) * 1.2f -> if (config.swipeSeek) Mode.SEEK else Mode.IGNORED
                                inEdgeZone -> Mode.IGNORED
                                start.x < size.width / 2f -> if (config.brightness) Mode.BRIGHTNESS else Mode.IGNORED
                                else -> if (config.volume) Mode.VOLUME else Mode.IGNORED
                            }
                            lastY = change.position.y
                            if (mode == Mode.SEEK) handler.onSeekStart()
                        }
                    }
                    Mode.SEEK -> {
                        handler.onSeekDrag((change.position.x - start.x) / size.width)
                        change.consume()
                    }
                    Mode.BRIGHTNESS, Mode.VOLUME -> {
                        val dy = (lastY - change.position.y) / (size.height * 0.75f)
                        lastY = change.position.y
                        if (mode == Mode.BRIGHTNESS) handler.onBrightness(dy) else handler.onVolume(dy)
                        change.consume()
                    }
                    Mode.HOLD -> {
                        handler.onHoldSlide(change.position.x - start.x)
                        change.consume()
                    }
                    Mode.ZOOM, Mode.IGNORED -> change.consume()
                }
            }
        } finally {
            // Also runs if the detector restarts mid-gesture, so a held speed-up can never stick.
            if (mode == Mode.HOLD) handler.onHoldEnd()
        }

        when (mode) {
            Mode.SEEK -> handler.onSeekEnd()
            Mode.BRIGHTNESS, Mode.VOLUME -> handler.onVerticalEnd()
            Mode.ZOOM -> handler.onZoomEnd()
            Mode.HOLD, Mode.IGNORED -> Unit
            Mode.UNDECIDED -> {
                // A plain tap. Wait briefly for a second tap before toggling the controls.
                val canDoubleTap = config.doubleTapSeek || downSide == TapSide.CENTER
                if (!canDoubleTap) {
                    handler.onTap()
                    return@awaitEachGesture
                }
                val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                    awaitFirstDown(requireUnconsumed = false)
                }
                if (second == null) {
                    handler.onTap()
                } else {
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        val side = sideOf(second.position.x)
                        if (side == TapSide.CENTER || config.doubleTapSeek) {
                            streak = 1
                            lastSeekSide = side.takeIf { it != TapSide.CENTER }
                            lastSeekTapAt = up.uptimeMillis
                            handler.onDoubleTap(side, streak)
                        } else {
                            handler.onTap()
                        }
                    }
                }
            }
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.handleSubtitleGesture(
    handler: PlayerGestureHandler,
) {
    handler.onSubtitleStart()
    var moved = false
    var travelled = 0f
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.none { it.pressed }) break
        val zoom = event.calculateZoom()
        val pan = event.calculatePan()
        travelled += pan.getDistance()
        val multiTouch = event.changes.count { it.pressed } > 1
        if (multiTouch || travelled > viewConfiguration.touchSlop) {
            moved = true
            handler.onSubtitleTransform(pan, zoom)
            event.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
        }
    }
    handler.onSubtitleEnd(moved)
    if (!moved) handler.onTap()
}
