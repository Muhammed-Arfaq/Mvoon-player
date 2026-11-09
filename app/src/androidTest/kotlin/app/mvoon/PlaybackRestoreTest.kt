package app.mvoon

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.mvoon.core.media.MediaKind
import app.mvoon.core.media.PlayableItem
import app.mvoon.core.player.PlaybackStatus
import app.mvoon.playback.RestorePoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * The "paused, came back 15 minutes later, Play does nothing" bug: Android stops the playback
 * service a while after playback pauses in the background, and the service releases the engine.
 * Uses the app's own engine (router) and coordinator, exactly as the player screen does.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackRestoreTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val engine get() = context.appContainer.engine
    private val coordinator get() = context.appContainer.playback

    @After
    fun tearDown() {
        onMain { engine.stop() }
    }

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

    private fun waitFor(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (onMain(condition)) return
            onMain { engine.state.value.error }?.let { fail("Playback error while waiting for $what: $it") }
            Thread.sleep(40)
        }
        fail("Timed out waiting for $what; state=${onMain { engine.state.value.copy(queue = emptyList()) }}")
    }

    @Test
    fun releasedWhilePaused_restoreBringsTheFileBackPausedWhereItWas_andPlayWorks() {
        val name = "mvoon_multiaudio.mkv"
        val item = PlayableItem(id = "restore-test:$name", uri = Uri.fromFile(asset(name)).toString(), title = name, kind = MediaKind.VIDEO)
        onMain { engine.load(listOf(item), 0, playWhenReady = true) }
        waitFor("playing") { engine.state.value.isPlaying && engine.positionMs > 1_000 }
        onMain {
            engine.pause()
            engine.seekTo(3_000)
        }
        waitFor("paused at 3 s") {
            val s = engine.state.value
            !s.isPlaying && s.status == PlaybackStatus.READY && abs(engine.positionMs - 3_000) < 300
        }
        val queue = onMain { engine.state.value.queue }

        // What PlaybackService.onDestroy does when Android stops the service during a long pause.
        onMain { engine.release() }
        assertTrue("engine emptied", onMain { engine.state.value.queue.isEmpty() })

        // The player screen comes back (PlayerViewModel.restoreIfReleased).
        onMain { coordinator.restore(queue, 0, 1f, RestorePoint(item.id, 3_000, System.currentTimeMillis())) }
        waitFor("restored and ready") { engine.state.value.currentItem?.id == item.id && engine.state.value.status == PlaybackStatus.READY }
        val restored = onMain { engine.state.value }
        assertFalse("comes back paused", restored.playWhenReady)
        assertEquals(1f, restored.speed)
        val position = onMain { engine.positionMs }
        assertTrue("back at 3 s, was $position", abs(position - 3_000) < 500)

        onMain { engine.play() }
        waitFor("plays again") { engine.state.value.isPlaying && engine.positionMs > 3_600 }
        Log.i("MvoonTest", "Restore OK: paused at 3000 ms, restored at $position ms, playing again")
    }
}
