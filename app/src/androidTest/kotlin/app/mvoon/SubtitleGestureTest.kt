package app.mvoon

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mvoon.core.player.StyledText
import app.mvoon.core.player.SubtitleCue
import app.mvoon.core.player.SubtitleFrame
import app.mvoon.core.settings.SubtitlePlacement
import app.mvoon.core.settings.SubtitleStyle
import app.mvoon.subtitles.SubtitlePlacementMath
import app.mvoon.ui.player.GestureConfig
import app.mvoon.ui.player.PlayerGestureHandler
import app.mvoon.ui.player.SubtitleOverlay
import app.mvoon.ui.player.TapSide
import app.mvoon.ui.player.playerGestures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the production subtitle overlay + unified gesture detector with real touch events and
 * checks that subtitle gestures and playback gestures never interfere.
 */
@RunWith(AndroidJUnit4::class)
class SubtitleGestureTest {

    @get:Rule
    val compose = createComposeRule()

    private var placement by mutableStateOf(SubtitlePlacement.DEFAULT)
    private var bounds: Rect? = null
    private var area = IntSize.Zero
    private var taps = 0
    private var seeks = 0
    private var vertical = 0
    private var videoZooms = 0
    private var subtitleTransforms = 0
    private val doubleTaps = mutableListOf<TapSide>()
    private var holdStarts = 0
    private var holdSlide = 0f
    private var holdEnds = 0

    private val handler = object : PlayerGestureHandler {
        override fun onTap() { taps++ }
        override fun onDoubleTap(side: TapSide, streak: Int) { doubleTaps += side }
        override fun onSeekStart() { seeks++ }
        override fun onSeekDrag(fractionOfWidth: Float) {}
        override fun onSeekEnd() {}
        override fun onBrightness(delta: Float) { vertical++ }
        override fun onVolume(delta: Float) { vertical++ }
        override fun onVerticalEnd() {}
        override fun onZoom(zoom: Float, pan: Offset, centroid: Offset) { videoZooms++ }
        override fun onZoomEnd() {}
        override fun onSubtitleStart() {}
        override fun onSubtitleTransform(pan: Offset, zoom: Float) {
            // Same maths PlayerScreen uses.
            val b = bounds ?: return
            subtitleTransforms++
            var p = SubtitlePlacementMath.drag(placement, pan.x, pan.y, b.width, b.height, area.width.toFloat(), area.height.toFloat(), 12f)
            if (zoom != 1f) p = SubtitlePlacementMath.zoom(p, zoom)
            placement = p
        }
        override fun onSubtitleEnd(moved: Boolean) {}
        override fun onHoldStart(): Boolean { holdStarts++; return true }
        override fun onHoldSlide(dx: Float) { holdSlide = dx }
        override fun onHoldEnd() { holdEnds++ }
    }

    private fun setUp() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { area = it }) {
                SubtitleOverlay(
                    frame = SubtitleFrame(listOf(SubtitleCue.Text(StyledText("A subtitle line to drag around")))),
                    style = SubtitleStyle(),
                    placement = placement,
                    videoRect = Rect.Zero,
                    fontScale = 1f,
                    previewText = null,
                    highlight = false,
                    snapped = false,
                    onBlockBounds = { bounds = it },
                )
                Box(
                    Modifier.fillMaxSize().testTag("gestures").playerGestures(
                        GestureConfig(),
                        isOnSubtitle = { point -> bounds?.inflate(16f)?.contains(point) == true },
                        handler = handler,
                    ),
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun dragAndPinch_moveOnlyTheSubtitles_andStayOnScreen() {
        setUp()
        val start = bounds!!
        Log.i("MvoonTest", "Subtitle block starts at $start in area $area")

        // 1. Drag the subtitle block upwards.
        compose.onNodeWithTag("gestures").performTouchInput {
            swipe(start.center, start.center + Offset(-80f, -500f), durationMillis = 400)
        }
        compose.waitForIdle()
        val moved = bounds!!
        assertTrue("subtitles should move up: $start -> $moved", moved.center.y < start.center.y - 300f)
        assertEquals("a subtitle drag must not seek", 0, seeks)
        assertEquals("a subtitle drag must not change volume/brightness", 0, vertical)

        // 2. Pinch on the subtitles to enlarge them.
        val c = moved.center
        compose.onNodeWithTag("gestures").performTouchInput {
            pinch(c - Offset(30f, 0f), c - Offset(220f, 0f), c + Offset(30f, 0f), c + Offset(220f, 0f), durationMillis = 400)
        }
        compose.waitForIdle()
        assertTrue("pinch should scale subtitles up, scale=${placement.scale}", placement.scale > 1.3f)
        assertTrue("pinch on subtitles must not zoom the video", videoZooms == 0)
        assertTrue("subtitles grew", bounds!!.height > moved.height)

        // 3. Fling them far below the screen: they must stay fully visible.
        val now = bounds!!.center
        compose.onNodeWithTag("gestures").performTouchInput { swipe(now, now + Offset(0f, 4_000f), durationMillis = 300) }
        compose.waitForIdle()
        val clamped = bounds!!
        assertTrue("kept on screen: $clamped within $area", clamped.top >= 0f && clamped.bottom <= area.height + 0.5f)

        // 4. A vertical swipe away from the subtitles is a playback gesture (volume), not a subtitle move.
        val before = placement
        compose.onNodeWithTag("gestures").performTouchInput {
            swipe(Offset(width * 0.85f, height * 0.35f), Offset(width * 0.85f, height * 0.15f), durationMillis = 300)
        }
        compose.waitForIdle()
        assertEquals("subtitle placement unchanged by playback gestures", before, placement)
        assertTrue("volume gesture fired", vertical > 0)

        // 5. A horizontal swipe in the middle of the video seeks.
        compose.onNodeWithTag("gestures").performTouchInput {
            swipe(Offset(width * 0.3f, height * 0.4f), Offset(width * 0.7f, height * 0.4f), durationMillis = 300)
        }
        compose.waitForIdle()
        assertTrue("seek gesture fired", seeks > 0)
        Log.i("MvoonTest", "Gesture separation OK: final placement=$placement transforms=$subtitleTransforms seeks=$seeks vertical=$vertical")
    }

    @Test
    fun doubleTapInTheMiddle_isPlayPause_andTheSidesSeek() {
        setUp()
        compose.onNodeWithTag("gestures").performTouchInput { doubleClick(Offset(width * 0.5f, height * 0.3f)) }
        compose.waitForIdle()
        assertEquals(listOf(TapSide.CENTER), doubleTaps)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("gestures").performTouchInput { doubleClick(Offset(width * 0.9f, height * 0.3f)) }
        compose.waitForIdle()
        assertEquals(listOf(TapSide.CENTER, TapSide.RIGHT), doubleTaps)
        assertEquals("a double-tap is not a single tap", 0, taps)
    }

    @Test
    fun pressAndHold_speedsUp_slideChangesIt_liftingEndsIt() {
        setUp()
        compose.onNodeWithTag("gestures").performTouchInput { down(Offset(width * 0.4f, height * 0.3f)) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals("holding still starts fast playback", 1, holdStarts)
        compose.onNodeWithTag("gestures").performTouchInput { moveBy(Offset(240f, 0f)) }
        compose.waitForIdle()
        assertEquals(240f, holdSlide, 1f)
        compose.onNodeWithTag("gestures").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals("lifting the finger ends it", 1, holdEnds)
        assertEquals("sliding while holding is not a seek", 0, seeks)
        assertEquals("a hold is not a tap", 0, taps)
        Log.i("MvoonTest", "Hold OK: starts=$holdStarts slide=$holdSlide ends=$holdEnds")
    }
}
