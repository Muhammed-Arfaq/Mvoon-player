package app.mvoon.core

import app.mvoon.core.player.CompatibilityPolicy
import app.mvoon.core.player.CompatibilityPolicy.Decision
import app.mvoon.core.player.ErrorKind
import app.mvoon.core.player.TrackInfo
import app.mvoon.core.player.TrackSet
import app.mvoon.core.player.TrackType
import app.mvoon.core.settings.CompatibilityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityPolicyTest {

    private fun track(type: TrackType, supported: Boolean, codec: String) =
        TrackInfo(id = codec, type = type, label = codec, codec = codec, isSupported = supported)

    @Test
    fun containerAndCodecErrorsNeedCompatibility() {
        assertNotNull(CompatibilityPolicy.reasonFor(ErrorKind.UNSUPPORTED_FORMAT, TrackSet()))
        assertNotNull(CompatibilityPolicy.reasonFor(ErrorKind.UNSUPPORTED_CODEC, TrackSet()))
        assertNotNull(CompatibilityPolicy.reasonFor(ErrorKind.DECODER_FAILED, TrackSet()))
    }

    @Test
    fun problemsVlcCannotFixAreIgnored() {
        assertNull(CompatibilityPolicy.reasonFor(ErrorKind.NETWORK, TrackSet()))
        assertNull(CompatibilityPolicy.reasonFor(ErrorKind.NO_PERMISSION, TrackSet()))
        assertNull(CompatibilityPolicy.reasonFor(ErrorKind.FILE_NOT_FOUND, TrackSet()))
        assertNull(CompatibilityPolicy.reasonFor(ErrorKind.DRM, TrackSet()))
    }

    @Test
    fun unsupportedVideoTrackIsDetectedWhileAudioPlays() {
        val tracks = TrackSet(
            video = listOf(track(TrackType.VIDEO, supported = false, codec = "VC-1")),
            audio = listOf(track(TrackType.AUDIO, supported = true, codec = "AAC")),
        )
        val reason = CompatibilityPolicy.reasonFor(null, tracks)
        assertTrue(reason!!, reason.contains("video") && reason.contains("VC-1"))
    }

    @Test
    fun healthyPlaybackNeedsNothing() {
        val tracks = TrackSet(
            video = listOf(track(TrackType.VIDEO, supported = true, codec = "H.264")),
            audio = listOf(track(TrackType.AUDIO, supported = false, codec = "AC-4"), track(TrackType.AUDIO, supported = true, codec = "AAC")),
        )
        assertNull(CompatibilityPolicy.reasonFor(null, tracks))
    }

    @Test
    fun decisionFollowsTheSetting() {
        val reason = "x"
        assertEquals(Decision.OFFER, CompatibilityPolicy.decide(reason, CompatibilityMode.ASK, alreadyUsingCompatibility = false))
        assertEquals(Decision.SWITCH, CompatibilityPolicy.decide(reason, CompatibilityMode.AUTOMATIC, alreadyUsingCompatibility = false))
        assertEquals(Decision.NONE, CompatibilityPolicy.decide(reason, CompatibilityMode.OFF, alreadyUsingCompatibility = false))
        assertEquals(Decision.NONE, CompatibilityPolicy.decide(reason, CompatibilityMode.AUTOMATIC, alreadyUsingCompatibility = true))
        assertEquals(Decision.NONE, CompatibilityPolicy.decide(null, CompatibilityMode.AUTOMATIC, alreadyUsingCompatibility = false))
    }
}
