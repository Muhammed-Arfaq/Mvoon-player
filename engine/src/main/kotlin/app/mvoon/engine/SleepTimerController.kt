package app.mvoon.engine

import android.os.Handler
import android.os.SystemClock
import app.mvoon.core.player.SleepTimerOption
import app.mvoon.core.player.SleepTimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sleep timer shared by every engine: after N minutes the volume fades out over [FADE_MS] and
 * playback pauses. "End of item" is reported by the engine through [onItemEnded].
 */
class SleepTimerController(
    private val handler: Handler,
    private val isPlaying: () -> Boolean,
    private val setVolume: (Float) -> Unit,
    private val pause: () -> Unit,
    private val onFired: () -> Unit,
) {
    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    fun set(option: SleepTimerOption?) {
        cancelCallbacks()
        setVolume(1f)
        _state.value = when (option) {
            null -> SleepTimerState()
            is SleepTimerOption.After -> {
                val delay = option.minutes * 60_000L
                handler.postDelayed(startFade, (delay - FADE_MS).coerceAtLeast(0))
                SleepTimerState(active = true, endsAtEpochMs = System.currentTimeMillis() + delay)
            }
            SleepTimerOption.EndOfItem -> SleepTimerState(active = true, endOfItem = true)
        }
    }

    /** Call when the current item finishes; returns true if the timer fired (the engine should stop there). */
    fun onItemEnded(): Boolean {
        if (!_state.value.endOfItem) return false
        _state.value = SleepTimerState()
        onFired()
        return true
    }

    fun cancel() {
        cancelCallbacks()
        _state.value = SleepTimerState()
    }

    private fun cancelCallbacks() {
        handler.removeCallbacks(startFade)
        handler.removeCallbacks(fadeStep)
    }

    private var fadeStartedAt = 0L
    private val startFade = Runnable {
        fadeStartedAt = SystemClock.elapsedRealtime()
        handler.post(fadeStep)
    }
    private val fadeStep: Runnable = object : Runnable {
        override fun run() {
            val progress = (SystemClock.elapsedRealtime() - fadeStartedAt).toFloat() / FADE_MS
            if (progress >= 1f || !isPlaying()) {
                pause()
                setVolume(1f)
                _state.value = SleepTimerState()
                onFired()
            } else {
                setVolume(1f - progress)
                handler.postDelayed(this, 100)
            }
        }
    }

    companion object {
        const val FADE_MS = 8_000L
    }
}
