package app.mvoon.core.player

import kotlin.math.roundToInt

/**
 * Press-and-hold fast playback: while a finger rests on the video it plays at one of [STEPS];
 * sliding sideways (still holding) moves between them, and lifting the finger restores the speed.
 */
object HoldSpeed {
    val STEPS = listOf(1.5f, 2f, 2.5f, 3f, 3.5f, 4f)
    const val DEFAULT = 2f

    /** Where a hold starts: the speed picked last time, or 2× if that isn't one of the steps. */
    fun startIndex(remembered: Float): Int = STEPS.indexOf(remembered).takeIf { it >= 0 } ?: STEPS.indexOf(DEFAULT)

    /** The step after sliding [dx] pixels from where the hold began (right = faster), one step per [stepPx]. */
    fun indexAfterSlide(start: Int, dx: Float, stepPx: Float): Int =
        (start + (dx / stepPx).roundToInt()).coerceIn(0, STEPS.lastIndex)

    /** "2×", "2.5×". */
    fun label(speed: Float): String = if (speed % 1f == 0f) "${speed.toInt()}×" else "$speed×"
}
