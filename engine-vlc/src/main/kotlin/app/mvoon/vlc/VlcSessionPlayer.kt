package app.mvoon.vlc

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import app.mvoon.core.media.PlayableItem
import app.mvoon.core.player.PlaybackStatus
import app.mvoon.core.player.RepeatMode
import app.mvoon.engine.MediaItemMapper
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Media3 [Player] facade over [VlcPlayerEngine], so the MediaSession (notification, lock screen,
 * Bluetooth / headset keys) keeps working while the compatibility engine plays. All commands are
 * forwarded to the engine; the engine calls [refresh] whenever its state changes.
 */
internal class VlcSessionPlayer(private val engine: VlcPlayerEngine) : SimpleBasePlayer(Looper.getMainLooper()) {

    private var mappedQueue: List<PlayableItem> = emptyList()
    private var mappedItems: List<MediaItem> = emptyList()

    fun refresh() = invalidateState()

    override fun getState(): State {
        val s = engine.state.value
        if (s.queue !== mappedQueue) {
            mappedQueue = s.queue
            mappedItems = s.queue.map(MediaItemMapper::toMediaItem)
        }
        val current = s.currentIndex.coerceIn(0, (s.queue.size - 1).coerceAtLeast(0))
        val playlist = s.queue.mapIndexed { i, item ->
            val durationMs = if (i == current && s.durationMs > 0) s.durationMs else item.durationMs
            MediaItemData.Builder("$i:${item.id}")
                .setMediaItem(mappedItems[i])
                .setDurationUs(if (durationMs > 0) durationMs * 1000 else C.TIME_UNSET)
                .setIsSeekable(i != current || s.isSeekable)
                .build()
        }
        val playbackState = when {
            s.queue.isEmpty() || s.error != null -> Player.STATE_IDLE
            else -> when (s.status) {
                PlaybackStatus.IDLE -> Player.STATE_IDLE
                PlaybackStatus.BUFFERING -> Player.STATE_BUFFERING
                PlaybackStatus.READY -> Player.STATE_READY
                PlaybackStatus.ENDED -> Player.STATE_ENDED
            }
        }
        return State.Builder()
            .setAvailableCommands(COMMANDS)
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(current)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(s.playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs { engine.positionMs }
            .setContentBufferedPositionMs { engine.bufferedPositionMs }
            .setPlaybackParameters(PlaybackParameters(s.speed))
            .setRepeatMode(
                when (s.repeatMode) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                },
            )
            .setShuffleModeEnabled(s.shuffle)
            .setSeekBackIncrementMs(engine.seekIncrementMs)
            .setSeekForwardIncrementMs(engine.seekIncrementMs)
            .setPlayerError(
                s.error?.let { PlaybackException(it.title, null, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED) },
            )
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> = done {
        if (playWhenReady) engine.play() else engine.pause()
    }

    override fun handlePrepare(): ListenableFuture<*> = done {
        val s = engine.state.value
        if (s.queue.isNotEmpty() && s.status == PlaybackStatus.IDLE) engine.retry()
    }

    // A session "stop" (e.g. a headset stop key) pauses instead: the queue stays loaded so the app
    // UI can still resume. The app clears the queue itself through PlayerEngine.stop().
    override fun handleStop(): ListenableFuture<*> = done { engine.pause() }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> = done {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> engine.next()
            Player.COMMAND_SEEK_TO_PREVIOUS -> engine.previous()
            else -> {
                val target = if (positionMs == C.TIME_UNSET) 0 else positionMs
                if (mediaItemIndex != engine.state.value.currentIndex) engine.skipTo(mediaItemIndex, target) else engine.seekTo(target)
            }
        }
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> =
        done { engine.setSpeed(playbackParameters.speed) }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> = done {
        engine.setRepeatMode(
            when (repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
        )
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> =
        done { engine.setShuffle(shuffleModeEnabled) }

    // The engine outlives any one MediaSession; PlaybackService releases it separately.
    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    private inline fun done(block: () -> Unit): ListenableFuture<*> {
        block()
        return Futures.immediateVoidFuture()
    }

    private companion object {
        val COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SET_SPEED_AND_PITCH,
                Player.COMMAND_SET_SHUFFLE_MODE,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_RELEASE,
            )
            .build()
    }
}
