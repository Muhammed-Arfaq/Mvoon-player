package app.mvoon.core

import app.mvoon.core.player.HoldSpeed
import org.junit.Assert.assertEquals
import org.junit.Test

class HoldSpeedTest {

    @Test
    fun startsAtTwoTimesUnlessAnotherStepWasPicked() {
        assertEquals(2f, HoldSpeed.STEPS[HoldSpeed.startIndex(2f)])
        assertEquals(3f, HoldSpeed.STEPS[HoldSpeed.startIndex(3f)])
        // Not a step (e.g. an old or corrupt value): fall back to 2×.
        assertEquals(2f, HoldSpeed.STEPS[HoldSpeed.startIndex(1.25f)])
    }

    @Test
    fun slidingMovesOneStepPerStepWidthAndStopsAtTheEnds() {
        val start = HoldSpeed.startIndex(2f)
        assertEquals(2f, HoldSpeed.STEPS[HoldSpeed.indexAfterSlide(start, 10f, 100f)]) // within the dead zone
        assertEquals(2.5f, HoldSpeed.STEPS[HoldSpeed.indexAfterSlide(start, 100f, 100f)])
        assertEquals(1.5f, HoldSpeed.STEPS[HoldSpeed.indexAfterSlide(start, -100f, 100f)])
        assertEquals(4f, HoldSpeed.STEPS[HoldSpeed.indexAfterSlide(start, 5_000f, 100f)])
        assertEquals(1.5f, HoldSpeed.STEPS[HoldSpeed.indexAfterSlide(start, -5_000f, 100f)])
    }

    @Test
    fun coversOneAndAHalfToFourInHalfSteps() {
        assertEquals(listOf(1.5f, 2f, 2.5f, 3f, 3.5f, 4f), HoldSpeed.STEPS)
        assertEquals("2×", HoldSpeed.label(2f))
        assertEquals("2.5×", HoldSpeed.label(2.5f))
    }
}
