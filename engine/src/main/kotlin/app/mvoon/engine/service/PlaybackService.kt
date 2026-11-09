package app.mvoon.engine.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import app.mvoon.engine.AndroidPlayerEngine
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

/** Implemented by the Application so the service can reach the process-wide engine and app UI. */
interface EngineHost {
    val playerEngine: AndroidPlayerEngine
    val notificationIconRes: Int
    fun sessionActivityIntent(): PendingIntent
}

/**
 * Owns the [MediaSession]: media notification, lock-screen / Bluetooth / headset controls,
 * Android Auto-style media buttons and foreground-service lifetime for background audio.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val host: EngineHost get() = application as EngineHost

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(host.notificationIconRes) },
        )
        val engine = host.playerEngine
        session = buildSession(engine.sessionPlayer())
        // The engine may rebuild its player (decoder preference) or switch to the compatibility
        // engine; the session follows so notification and media buttons keep working.
        engine.onSessionPlayerChanged = { newPlayer -> session?.player = newPlayer }
    }

    private fun buildSession(player: Player): MediaSession {
        val seekBack = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setDisplayName("Back 10 seconds")
            .setSlots(CommandButton.SLOT_BACK)
            .build()
        val seekForward = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setDisplayName("Forward 10 seconds")
            .setSlots(CommandButton.SLOT_FORWARD)
            .build()
        return MediaSession.Builder(this, player)
            .setSessionActivity(host.sessionActivityIntent())
            .setBitmapLoader(CacheBitmapLoader(ArtworkBitmapLoader(this)))
            .setMediaButtonPreferences(listOf(seekBack, seekForward))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away keeps music going (it has a notification), but stops paused/idle sessions.
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onDestroy() {
        host.playerEngine.onSessionPlayerChanged = null
        session?.release()
        session = null
        host.playerEngine.release()
        super.onDestroy()
    }

    companion object {
        /** Binds a controller so the service (and its foreground notification) exists while UI is shown. */
        fun connect(context: Context): ListenableFuture<MediaController> =
            MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
                .buildAsync()

        fun release(future: ListenableFuture<MediaController>) = MediaController.releaseFuture(future)

        val directExecutor get() = MoreExecutors.directExecutor()
    }
}
