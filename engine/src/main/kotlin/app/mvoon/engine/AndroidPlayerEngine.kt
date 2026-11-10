package app.mvoon.engine

import android.view.SurfaceView
import androidx.media3.common.Player
import app.mvoon.core.media.PlayableItem
import app.mvoon.core.player.PlayerEngine

/**
 * Android-specific additions to [PlayerEngine]: video output, the Media3 [Player] the media session
 * exposes (notification, lock screen, headset/Bluetooth keys), and lifecycle. Implemented by the
 * system engine (Media3), the compatibility engine (libVLC) and the router that switches between them.
 */
interface AndroidPlayerEngine : PlayerEngine {
    fun attachSurface(view: SurfaceView)
    fun detachSurface(view: SurfaceView)

    /** Replaces an upcoming queue entry (e.g. once its sidecar subtitles were found). */
    fun updateQueueItem(index: Int, item: PlayableItem)

    /**
     * Takes an item out of the queue (its file was deleted). Removing the current item moves on to
     * the one after it; removing the last remaining item stops playback.
     */
    fun removeFromQueue(itemId: String)
    fun updateConfig(newConfig: EngineConfig)

    /** The player the [androidx.media3.session.MediaSession] should expose right now. */
    fun sessionPlayer(): Player

    /** Fired when [sessionPlayer] changes (engine rebuilt or switched). */
    var onSessionPlayerChanged: ((Player) -> Unit)?

    /** Frees decoders and native resources. A later [load] recreates them. */
    fun release()
}
