package app.mvoon

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.mvoon.core.media.MediaKind
import app.mvoon.core.media.PlayableItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * "Delete" from the player while a file plays: the queue loses it, the next file plays on, and
 * deleting the last one stops playback (the screen then closes). Checked on both engines.
 * No real files are deleted here: this is what happens after Android confirmed the deletion.
 */
@RunWith(AndroidJUnit4::class)
class PlayingFileDeletionTest {

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

    private fun item(name: String, vlc: Boolean) = PlayableItem(
        id = "delete-test:$name", uri = Uri.fromFile(asset(name)).toString(), title = name, kind = MediaKind.VIDEO,
        preferCompatibilityEngine = vlc,
    )

    private fun waitFor(what: String, timeoutMs: Long = 30_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (onMain(condition)) return
            Thread.sleep(40)
        }
        fail("Timed out waiting for $what; state=${onMain { engine.state.value.copy(queue = emptyList()) }}")
    }

    private fun deletingWalksThroughTheQueue(vlc: Boolean) {
        val queue = listOf(item("mvoon_multiaudio.mkv", vlc), item("sample_with_ass_subtitles.mkv", vlc), item("bbb_642x642_768kbps_30fps_vp9.webm", vlc))
        onMain { engine.load(queue, 0, playWhenReady = true) }
        // libVLC's first start after an install indexes fonts and plugins first, which can take a while.
        waitFor("first file playing", timeoutMs = 90_000) { engine.state.value.isPlaying && engine.state.value.currentItem?.id == queue[0].id }
        assertEquals(vlc, onMain { engine.state.value.isCompatibilityEngine })

        // A file further down the queue: it just disappears, the current one keeps playing.
        assertTrue(onMain { coordinator.onFileDeleted(queue[1].id) })
        assertEquals(listOf(queue[0].id, queue[2].id), onMain { engine.state.value.queue.map { it.id } })
        assertEquals(queue[0].id, onMain { engine.state.value.currentItem?.id })

        // The playing file: the next one takes over and plays.
        assertTrue(onMain { coordinator.onFileDeleted(queue[0].id) })
        waitFor("next file playing") { engine.state.value.currentItem?.id == queue[2].id && engine.state.value.isPlaying }
        assertEquals(1, onMain { engine.state.value.queue.size })

        // The last file: playback stops and the player is told to close.
        assertFalse(onMain { coordinator.onFileDeleted(queue[2].id) })
        assertTrue(onMain { engine.state.value.queue.isEmpty() })
        Log.i("MvoonTest", "Deleting while playing OK on the ${if (vlc) "VLC" else "system"} engine")
    }

    @Test
    fun deletingFilesWhilePlaying_systemEngine() = deletingWalksThroughTheQueue(vlc = false)

    @Test
    fun deletingFilesWhilePlaying_compatibilityEngine() = deletingWalksThroughTheQueue(vlc = true)
}
