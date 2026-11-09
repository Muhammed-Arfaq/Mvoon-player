package app.mvoon

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.mvoon.core.media.ExternalSubtitle
import app.mvoon.core.media.MediaKind
import app.mvoon.core.media.PlayableItem
import app.mvoon.core.player.PlaybackStatus
import app.mvoon.core.player.PlayerEngine
import app.mvoon.core.settings.CompatibilityMode
import app.mvoon.core.settings.SubtitleAutoMode
import app.mvoon.engine.AndroidPlayerEngine
import app.mvoon.engine.EngineConfig
import app.mvoon.engine.Media3PlayerEngine
import app.mvoon.playback.PlaybackRouter
import app.mvoon.subtitles.SubtitleFormats
import app.mvoon.vlc.VlcPlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * The libVLC compatibility engine and the router that brings it in, on a real device/emulator with a
 * real SurfaceView. Samples Media3 can't play (WMV/ASF, RealMedia, legacy AVI codecs) are public
 * test files from samples.ffmpeg.org. Results are logged under the "MvoonTest" tag.
 */
@RunWith(AndroidJUnit4::class)
class CompatibilityEngineTest {

    @get:Rule
    val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var surface: SurfaceView? = null
    private val created = mutableListOf<AndroidPlayerEngine>()

    @Before
    fun setUp() {
        compose.setContent { AndroidView(factory = { SurfaceView(it).also { v -> surface = v } }, modifier = Modifier.fillMaxSize()) }
        compose.waitForIdle()
    }

    @After
    fun tearDown() {
        onMain { created.forEach { it.release() } }
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

    private fun item(name: String, subs: List<ExternalSubtitle> = emptyList(), kind: MediaKind = MediaKind.VIDEO) =
        PlayableItem(id = name, uri = Uri.fromFile(asset(name)).toString(), title = name.substringAfterLast('/'), kind = kind, externalSubtitles = subs)

    private fun <E : AndroidPlayerEngine> create(engine: () -> E): E = onMain {
        engine().also {
            created += it
            it.attachSurface(surface!!)
        }
    }

    private fun waitFor(engine: PlayerEngine, what: String, timeoutMs: Long = 20_000, allowError: Boolean = false, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (onMain(condition)) return
            if (!allowError) onMain { engine.state.value.error }?.let { fail("Playback error while waiting for $what: $it") }
            Thread.sleep(40)
        }
        fail("Timed out waiting for $what; state=${onMain { engine.state.value.copy(queue = emptyList()) }} tracks=${onMain { engine.tracks.value }}")
    }

    private fun log(message: String) = Log.i("MvoonTest", message)

    // ------------------------------------------------------------ tests

    @Test
    fun filesTheSystemPlayerCantPlay_playInTheCompatibilityEngine() {
        // name to kind: firstrun.rm is RealAudio (Cook) only.
        val samples = listOf(
            "compat/welcome3.wmv" to MediaKind.VIDEO,
            "compat/firstrun.rm" to MediaKind.AUDIO,
            "compat/nokia_n90.wmv" to MediaKind.VIDEO,
            "compat/divx5.avi" to MediaKind.VIDEO,
        )
        val report = StringBuilder()
        for ((name, kind) in samples) {
            // 1. What the system engine (Media3 + FFmpeg audio) makes of it.
            val system = create { Media3PlayerEngine(context) }
            onMain { system.load(listOf(item(name, kind = kind)), 0, true) }
            waitFor(system, "$name: system verdict", allowError = true) {
                val s = system.state.value
                s.compatibilityReason != null || s.error != null || (s.isPlaying && system.positionMs > 1_500)
            }
            val sys = onMain { system.state.value }
            val sysVerdict = sys.compatibilityReason ?: sys.error?.title ?: "plays (video=${sys.decoders.videoDecoder}, audio=${sys.decoders.audioDecoder})"
            onMain { system.release() }

            // 2. The compatibility engine.
            val vlc = create { VlcPlayerEngine(context) }
            onMain { vlc.load(listOf(item(name, kind = kind)), 0, true) }
            // The first libVLC start after installation indexes system fonts (~10 s on the emulator).
            waitFor(vlc, "$name: VLC playing", timeoutMs = 60_000) {
                val s = vlc.state.value
                s.isPlaying && vlc.positionMs > minOf(1_000L, s.durationMs / 3)
            }
            if (kind == MediaKind.VIDEO) waitFor(vlc, "$name: VLC video size") { vlc.state.value.videoSize != null }
            val s = onMain { vlc.state.value }
            val t = onMain { vlc.tracks.value }
            val stats = onMain { vlc.stats() }!!
            assertNull("$name: ${s.error}", s.error)
            if (t.audio.isNotEmpty()) assertTrue("$name: audio actually played ($stats)", stats.playedAudioBuffers > 0)
            if (kind == MediaKind.VIDEO) assertTrue("$name: pictures displayed ($stats)", stats.displayedPictures > 0)
            assertTrue(s.isCompatibilityEngine)
            assertEquals(VlcPlayerEngine.ENGINE_NAME, s.engineName)
            assertTrue("$name: track listed", if (kind == MediaKind.VIDEO) t.video.isNotEmpty() else t.audio.isNotEmpty())
            report.append("\n  $name\n    system: $sysVerdict\n    VLC: video=${s.decoders.videoFormat} audio=${s.decoders.audioFormat} size=${s.videoSize} duration=${s.durationMs}\n    $stats")
            onMain { vlc.release() }

            // ASF and RealMedia have no Media3 extractor: the system engine must say so (and offer VLC).
            if (name.endsWith(".wmv") || name.endsWith(".rm")) assertNotNull("$name: compatibility reason", sys.compatibilityReason)
        }
        log("Compatibility engine:$report")
    }

    @Test
    fun compatibilityEngine_tracksSubtitlesSyncAndSeek() {
        val vlc = create { VlcPlayerEngine(context) }
        val srt = ExternalSubtitle(Uri.fromFile(asset("mvoon_multiaudio.en.srt")).toString(), "English (file)", "en", SubtitleFormats.MIME_SUBRIP)
        onMain {
            vlc.updateConfig(EngineConfig(subtitleMode = SubtitleAutoMode.ALWAYS, subtitleLanguages = listOf("fr")))
            vlc.load(listOf(item("mvoon_multiaudio.mkv", subs = listOf(srt))), 0, true)
        }
        waitFor(vlc, "two audio tracks") { vlc.tracks.value.audio.size == 2 }
        val audio = onMain { vlc.tracks.value.audio }
        log("VLC audio tracks: ${audio.map { "${it.label}/${it.language}/${it.codec}/${it.channels}ch" }}")
        assertEquals("en", audio[0].language)
        assertEquals("de", audio[1].language)
        assertTrue(audio[0].codec!!, audio[0].codec!!.startsWith("E-AC-3"))
        assertTrue(audio[1].codec!!, audio[1].codec!!.startsWith("AC-3"))
        waitFor(vlc, "playing") { vlc.state.value.isPlaying && vlc.positionMs > 1_000 }
        val eac3Buffers = onMain { vlc.stats()!!.playedAudioBuffers }
        assertTrue("E-AC-3 audio reached the output", eac3Buffers > 0)

        // Audio switching keeps playing.
        onMain { vlc.selectAudioTrack(audio[1].id) }
        waitFor(vlc, "German selected") { vlc.tracks.value.selectedAudio?.language == "de" }
        val afterSwitch = onMain { vlc.positionMs }
        waitFor(vlc, "still playing after switch") { vlc.positionMs > afterSwitch + 800 }
        waitFor(vlc, "AC-3 audio reaching the output") { vlc.stats()!!.playedAudioBuffers > eac3Buffers + 20 }
        log("VLC audio stats after switching to AC-3: ${onMain { vlc.stats() }}")

        // Embedded + sidecar subtitles, auto-selected by language preference.
        waitFor(vlc, "external subtitle listed") { vlc.tracks.value.subtitles.any { it.isExternal } }
        waitFor(vlc, "French subtitles auto-selected") { vlc.tracks.value.selectedSubtitle?.language == "fr" }
        val subs = onMain { vlc.tracks.value.subtitles }
        log("VLC subtitle tracks: ${subs.map { "${it.label}/${it.language}/${it.codec}/external=${it.isExternal}" }}")
        val external = subs.first { it.isExternal }
        assertEquals("English (file)", external.label)
        assertEquals("en", external.language)
        onMain { vlc.selectSubtitleTrack(external.id) }
        waitFor(vlc, "sidecar selected") { vlc.tracks.value.selectedSubtitle?.isExternal == true }
        onMain { vlc.selectSubtitleTrack(null) }
        waitFor(vlc, "subtitles off") { vlc.tracks.value.selectedSubtitle == null }

        // Sync offsets and seeking.
        onMain {
            vlc.setAudioDelayMs(250)
            vlc.setSubtitleDelayMs(-400)
        }
        assertEquals(250L, onMain { vlc.state.value.audioDelayMs })
        assertEquals(-400L, onMain { vlc.state.value.subtitleDelayMs })
        onMain { vlc.seekTo(5_000) }
        waitFor(vlc, "seek to 5 s") { vlc.positionMs in 4_500..7_000 }
        onMain { vlc.pause() }
        waitFor(vlc, "paused") { !vlc.state.value.isPlaying }
        val pausedAt = onMain { vlc.positionMs }
        Thread.sleep(600)
        assertTrue("position frozen while paused", abs(onMain { vlc.positionMs } - pausedAt) < 150)
        onMain { vlc.play() }
        waitFor(vlc, "resumed") { vlc.state.value.isPlaying && vlc.positionMs > pausedAt + 300 }
        log("VLC tracks, subtitles, delays, seek and pause/resume OK")
    }

    @Test
    fun router_switchKeepsPosition_sessionFollows_andReturnsToSystemEngine() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val router = create { PlaybackRouter(Media3PlayerEngine(context), { VlcPlayerEngine(context) }, scope) }
            var sessionPlayer: Player? = null
            onMain {
                router.updateConfig(EngineConfig(compatibilityMode = CompatibilityMode.ASK))
                sessionPlayer = router.sessionPlayer()
                router.onSessionPlayerChanged = { sessionPlayer = it }
                router.load(listOf(item("mvoon_multiaudio.mkv")), 0, true)
            }
            assertTrue(onMain { sessionPlayer } is ExoPlayer)
            waitFor(router, "system engine playing") { router.state.value.isPlaying && router.positionMs > 2_500 }
            assertFalse(onMain { router.state.value.isCompatibilityEngine })

            // Manual switch straight onto audio track 2 — what choosing a track the phone can't
            // decode does in the audio panel.
            val before = onMain { router.positionMs }
            onMain { router.switchToCompatibility(audioTrackIndex = 1) }
            waitFor(router, "VLC playing after switch") { router.state.value.isCompatibilityEngine && router.state.value.isPlaying && router.positionMs > 0 }
            waitFor(router, "VLC playing the chosen (German) track") { router.tracks.value.selectedAudio?.language == "de" }
            val after = onMain { router.positionMs }
            log("Switch: system at $before ms -> VLC at $after ms")
            assertTrue("position kept ($before -> $after)", abs(after - before) < 2_000)

            // The media session now drives VLC: notification / headset commands work through it.
            val player = onMain { sessionPlayer }!!
            assertFalse(player is ExoPlayer)
            assertEquals("mvoon_multiaudio.mkv", onMain { player.currentMediaItem?.mediaMetadata?.title?.toString() })
            onMain { player.pause() }
            waitFor(router, "paused via session") { !router.state.value.playWhenReady && !router.state.value.isPlaying }
            onMain { player.seekTo(6_000) }
            waitFor(router, "seek via session") { router.positionMs in 5_500..8_000 }
            onMain { player.play() }
            waitFor(router, "playing via session") { router.state.value.isPlaying && player.playbackState == Player.STATE_READY }

            // Next normal file goes back to the system engine.
            onMain { router.load(listOf(item("mvoon_multiaudio.mkv")), 0, true) }
            waitFor(router, "back on the system engine") {
                !router.state.value.isCompatibilityEngine && router.state.value.isPlaying && router.positionMs > 500
            }
            assertTrue(onMain { sessionPlayer } is ExoPlayer)

            // AUTOMATIC: an unplayable file switches by itself.
            onMain {
                router.updateConfig(EngineConfig(compatibilityMode = CompatibilityMode.AUTOMATIC))
                router.load(listOf(item("compat/welcome3.wmv")), 0, true)
            }
            waitFor(router, "automatic switch to VLC", allowError = true) {
                router.state.value.isCompatibilityEngine && router.state.value.isPlaying && router.state.value.status == PlaybackStatus.READY
            }
            assertNull(onMain { router.state.value.error })
            log("Router: manual switch, session hand-over, return to system engine and automatic switch OK")
        } finally {
            scope.cancel()
        }
    }
}
