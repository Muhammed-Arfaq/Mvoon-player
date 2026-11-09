package app.mvoon

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.mvoon.core.media.ExternalSubtitle
import app.mvoon.core.media.MediaKind
import app.mvoon.core.media.PlayableItem
import app.mvoon.core.player.PlaybackStatus
import app.mvoon.core.player.SubtitleCue
import app.mvoon.core.player.SubtitleFrame
import app.mvoon.core.settings.SubtitleAutoMode
import app.mvoon.engine.EngineConfig
import app.mvoon.engine.Media3PlayerEngine
import app.mvoon.engine.SubtitleFilePreparer
import app.mvoon.subtitles.SubtitleFormats
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real playback on a device/emulator through the production engine (ExoPlayer + FFmpeg renderers,
 * sync renderers, track logic). Media comes from AndroidX Media3's public test samples plus
 * tools/make_test_media.py. Results are logged under the "MvoonTest" tag.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackEngineTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var engine: Media3PlayerEngine

    @Before
    fun setUp() {
        onMain { engine = Media3PlayerEngine(context) }
    }

    @After
    fun tearDown() {
        onMain { engine.releasePlayer() }
    }

    // ------------------------------------------------------------ helpers

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private fun asset(name: String): File = File(context.cacheDir, "test-media/$name").also { file ->
        if (!file.exists()) {
            file.parentFile!!.mkdirs()
            instrumentation.context.assets.open("media/$name").use { input -> file.outputStream().use(input::copyTo) }
        }
    }

    private fun item(name: String, kind: MediaKind = MediaKind.VIDEO, subs: List<ExternalSubtitle> = emptyList()) =
        PlayableItem(id = name, uri = Uri.fromFile(asset(name)).toString(), title = name, kind = kind, externalSubtitles = subs)

    private fun waitFor(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (onMain(condition)) return
            onMain { engine.state.value.error }?.let { fail("Playback error while waiting for $what: $it") }
            Thread.sleep(40)
        }
        fail("Timed out waiting for $what; state=${onMain { engine.state.value.copy(queue = emptyList()) }}")
    }

    private fun SubtitleFrame.text() = cues.filterIsInstance<SubtitleCue.Text>().joinToString(" | ") { it.text.text }

    private fun log(message: String) = Log.i("MvoonTest", message)

    private fun deviceHas(mime: String) = MediaCodecUtil.getDecoderInfos(mime, false, false).isNotEmpty()

    // ------------------------------------------------------------ tests

    @Test
    fun eac3InMkv_decodesWithFfmpeg_andAudioTracksSwitch() {
        val deviceEac3 = deviceHas(MimeTypes.AUDIO_E_AC3)
        onMain { engine.load(listOf(item("mvoon_multiaudio.mkv")), 0, true) }
        waitFor("two audio tracks") { engine.tracks.value.audio.size == 2 }
        val audio = onMain { engine.tracks.value.audio }
        assertEquals("en", audio[0].language)
        assertEquals("de", audio[1].language)
        assertTrue(audio[0].codec!!, audio[0].codec!!.startsWith("E-AC-3"))
        assertTrue(audio[1].codec!!, audio[1].codec!!.startsWith("AC-3"))
        assertTrue("default English track selected", audio[0].isSelected)

        waitFor("playing past 1.5 s") { engine.state.value.isPlaying && engine.positionMs > 1_500 }
        val eac3State = onMain { engine.state.value }
        val decoder = eac3State.decoders.audioDecoder!!
        log("E-AC-3: device decoder present=$deviceEac3, used decoder=$decoder, format=${eac3State.decoders.audioFormat}, support=${audio[0].decoderSupport}")
        if (!deviceEac3) assertTrue("E-AC-3 should use FFmpeg, was $decoder", decoder.contains("ffmpeg", ignoreCase = true))
        assertTrue(eac3State.decoders.audioFormat!!, eac3State.decoders.audioFormat!!.startsWith("E-AC-3"))

        onMain { engine.selectAudioTrack(engine.tracks.value.audio[1].id) }
        waitFor("German AC-3 selected") { engine.tracks.value.selectedAudio?.language == "de" }
        waitFor("AC-3 decoding") { engine.state.value.decoders.audioFormat?.startsWith("AC-3") == true }
        val afterSwitch = onMain { engine.positionMs }
        waitFor("still playing after switch") { engine.positionMs > afterSwitch + 1_000 }
        log("Switched to AC-3: decoder=${onMain { engine.state.value.decoders.audioDecoder }} format=${onMain { engine.state.value.decoders.audioFormat }}")

        onMain { engine.selectAudioTrack(engine.tracks.value.audio[0].id) }
        waitFor("back to E-AC-3") { engine.state.value.decoders.audioFormat?.startsWith("E-AC-3") == true }
        assertNull(onMain { engine.state.value.error })
    }

    @Test
    fun dolbyAndDtsSamples_decodeToTheEnd() {
        val samples = listOf(
            "sample_eac3.mp4", "sample_eac3joc.mp4", "sample_ac3.mp4",
            "sample_dts.ts", "sample_with_dts_hd_ma.mkv", "sample_dthd.mp4",
        )
        val report = StringBuilder()
        for (name in samples) {
            onMain { engine.load(listOf(item(name, MediaKind.AUDIO)), 0, true) }
            waitFor("$name ended") { engine.state.value.status == PlaybackStatus.ENDED }
            val s = onMain { engine.state.value }
            assertNull("$name: ${s.error}", s.error)
            assertTrue("$name: no audio decoder initialised", s.decoders.audioDecoder != null)
            report.append("\n  $name -> ${s.decoders.audioDecoder} | ${s.decoders.audioFormat}")
        }
        log("Dolby/DTS family:$report")
    }

    @Test
    fun otherFormats_play() {
        val samples = listOf(
            "bear.opus" to MediaKind.AUDIO, "bear_with_picture.flac" to MediaKind.AUDIO,
            "sample.avi" to MediaKind.VIDEO, "sample_h265.ts" to MediaKind.VIDEO,
            "bbb_642x642_768kbps_30fps_vp9.webm" to MediaKind.VIDEO, "long_1080p_av1.mp4" to MediaKind.VIDEO,
        )
        val report = StringBuilder()
        for ((name, kind) in samples) {
            onMain { engine.load(listOf(item(name, kind)), 0, true) }
            waitFor("$name decoding") {
                val d = engine.state.value.decoders
                (if (kind == MediaKind.VIDEO) d.videoDecoder != null else d.audioDecoder != null) && engine.positionMs > 300 ||
                    engine.state.value.status == PlaybackStatus.ENDED
            }
            val s = onMain { engine.state.value }
            assertNull("$name: ${s.error}", s.error)
            report.append("\n  $name -> video=${s.decoders.videoDecoder} (${s.decoders.videoFormat}) audio=${s.decoders.audioDecoder} (${s.decoders.audioFormat})")
        }
        log("Other formats:$report")
    }

    @Test
    fun embeddedSubtitles_followLanguagePreference_andSwitch() {
        onMain { engine.updateConfig(EngineConfig(subtitleMode = SubtitleAutoMode.ALWAYS, subtitleLanguages = listOf("fr"))) }
        onMain { engine.load(listOf(item("mvoon_multiaudio.mkv")), 0, true) }
        waitFor("French subtitles auto-selected") { engine.tracks.value.selectedSubtitle?.language == "fr" }
        waitFor("French cue on screen") { engine.subtitles.value.text().contains("Sous-titre intégré") }
        onMain { engine.selectSubtitleTrack(engine.tracks.value.subtitles.first { it.language == "en" }.id) }
        waitFor("English cue on screen") { engine.subtitles.value.text().contains("Embedded English") }
        onMain { engine.selectSubtitleTrack(null) }
        waitFor("subtitles off") { engine.tracks.value.selectedSubtitle == null && engine.subtitles.value.isEmpty }
        log("Embedded subtitle selection by preference + manual switching OK")
    }

    @Test
    fun subtitleDelay_shiftsCueTiming() {
        fun firstCueAt(delayMs: Long): Long {
            onMain {
                engine.updateConfig(EngineConfig(subtitleMode = SubtitleAutoMode.ALWAYS, subtitleLanguages = listOf("en")))
                engine.load(listOf(item("mvoon_multiaudio.mkv")), 0, false)
            }
            waitFor("ready") { engine.state.value.status == PlaybackStatus.READY }
            onMain { engine.setSubtitleDelayMs(delayMs) }
            Thread.sleep(600) // debounce + resync seek
            waitFor("ready after resync") { engine.state.value.status == PlaybackStatus.READY }
            onMain { engine.play() }
            waitFor("first cue", 15_000) { !engine.subtitles.value.isEmpty }
            return onMain { engine.positionMs }
        }
        val base = firstCueAt(0)
        val delayed = firstCueAt(1_000)
        log("First cue at ${base} ms without delay, ${delayed} ms with +1000 ms delay")
        assertTrue("base=$base delayed=$delayed", delayed - base in 700..1_400)
    }

    @Test
    fun sidecarSubtitle_inWindows1251_isRepairedAndShown() {
        val srt = asset("mvoon_multiaudio.ru.srt")
        val original = ExternalSubtitle(Uri.fromFile(srt).toString(), "Russian", "ru", SubtitleFormats.MIME_SUBRIP)
        val prepared = SubtitleFilePreparer(context).prepare(original, null)
        assertNotEquals("legacy-encoded file should be converted", original.uri, prepared.uri)
        onMain {
            engine.updateConfig(EngineConfig(subtitleMode = SubtitleAutoMode.OFF))
            engine.load(listOf(item("mvoon_multiaudio.mkv", subs = listOf(prepared.copy(isUserAdded = true)))), 0, true)
        }
        waitFor("Russian cue decoded correctly") { engine.subtitles.value.text().contains("Русские субтитры") }
        log("Windows-1251 sidecar subtitle displayed as: ${onMain { engine.subtitles.value.text() }}")
    }

    @Test
    fun pgsImageSubtitles_arriveAsBitmaps() {
        onMain {
            engine.updateConfig(EngineConfig(subtitleMode = SubtitleAutoMode.ALWAYS))
            engine.load(listOf(item("sample_with_pgs_subtitles.mkv")), 0, true)
        }
        waitFor("subtitle track") { engine.tracks.value.subtitles.isNotEmpty() }
        val track = onMain { engine.tracks.value.subtitles.first() }
        assertTrue("PGS should be flagged as image subtitle: $track", track.isBitmapSubtitle)
        if (!track.isSelected) onMain { engine.selectSubtitleTrack(track.id) }
        waitFor("bitmap cue", 15_000) { engine.subtitles.value.cues.any { it is SubtitleCue.Image } }
        val image = onMain { engine.subtitles.value.cues.filterIsInstance<SubtitleCue.Image>().first() }
        log("PGS cue: ${image.image.width}x${image.image.height} at left=${image.left} top=${image.top} width=${image.width}")
    }

    @Test
    fun audioDelay_movesTheMasterClock() {
        onMain { engine.load(listOf(item("mvoon_multiaudio.mkv")), 0, true) }
        waitFor("playing") { engine.state.value.isPlaying && engine.positionMs > 800 }
        val player = onMain { engine.currentPlayer!! }
        val clock0 = onMain { player.currentPosition }
        val wall0 = SystemClock.elapsedRealtime()
        onMain { engine.setAudioDelayMs(2_000) }
        Thread.sleep(1_200)
        val clock1 = onMain { player.currentPosition }
        val wall1 = SystemClock.elapsedRealtime()
        val jump = (clock1 - clock0) - (wall1 - wall0)
        log("Audio delay +2000 ms: master clock jumped by $jump ms beyond wall time")
        assertTrue("clock jump $jump", jump in 1_600..2_400)
        assertEquals(2_000L, onMain { engine.state.value.audioDelayMs })
        onMain { engine.setAudioDelayMs(-300) }
        Thread.sleep(1_000)
        val p = onMain { engine.positionMs }
        waitFor("still playing after negative delay") { engine.positionMs > p + 500 }
    }

    @Test
    fun seeking_landsOnTarget() {
        onMain { engine.load(listOf(item("mvoon_multiaudio.mkv")), 0, true) }
        waitFor("ready") { engine.state.value.status == PlaybackStatus.READY }
        onMain { engine.seekTo(9_000) }
        waitFor("position past 9 s") { engine.positionMs in 9_000..12_400 }
        log("Seek to 9000 ms -> ${onMain { engine.positionMs }} ms")
    }
}
