package app.mvoon.core

import app.mvoon.core.util.formatDuration
import app.mvoon.core.util.formatOffset
import app.mvoon.core.util.resolutionLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    @Test
    fun durations() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("1:05", formatDuration(65_000))
        assertEquals("1:02:05", formatDuration(3_725_000))
        assertEquals("-0:10", formatDuration(-10_000))
    }

    @Test
    fun offsets() {
        assertEquals("0 ms", formatOffset(0))
        assertEquals("+250 ms", formatOffset(250))
        assertEquals("−1.5 s", formatOffset(-1500))
        assertEquals("+2 s", formatOffset(2000))
    }

    @Test
    fun resolutions() {
        assertEquals("4K", resolutionLabel(3840, 2160))
        assertEquals("1080p", resolutionLabel(1920, 800)) // scope film
        assertEquals("1080p", resolutionLabel(1080, 1920)) // portrait
        assertEquals("720p", resolutionLabel(1280, 720))
    }
}
