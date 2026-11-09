package app.mvoon.subtitles

import app.mvoon.core.settings.SubtitlePlacement
import kotlin.math.abs

/**
 * Geometry for draggable / pinch-resizable subtitles. Everything is in pixels of the visible area
 * (safe area of the screen). The block is always kept fully on screen, whatever its current size.
 */
object SubtitlePlacementMath {

    data class Box(val left: Float, val top: Float, val width: Float, val height: Float) {
        val right: Float get() = left + width
        val bottom: Float get() = top + height
        fun contains(x: Float, y: Float, slop: Float = 0f): Boolean =
            x >= left - slop && x <= right + slop && y >= top - slop && y <= bottom + slop
    }

    /** Where to draw a block of [blockW]×[blockH] for [placement] inside an [areaW]×[areaH] area. */
    fun layout(
        placement: SubtitlePlacement,
        blockW: Float,
        blockH: Float,
        areaW: Float,
        areaH: Float,
        margin: Float,
    ): Box {
        val left = clampAxis(placement.centerX * areaW - blockW / 2f, blockW, areaW, margin)
        val top = clampAxis(placement.bottomY * areaH - blockH, blockH, areaH, margin)
        return Box(left, top, blockW, blockH)
    }

    /** Moves the block by a drag delta and returns the (clamped) normalised placement. */
    fun drag(
        placement: SubtitlePlacement,
        dx: Float,
        dy: Float,
        blockW: Float,
        blockH: Float,
        areaW: Float,
        areaH: Float,
        margin: Float,
    ): SubtitlePlacement {
        if (areaW <= 0f || areaH <= 0f) return placement
        val box = layout(placement, blockW, blockH, areaW, areaH, margin)
        val left = clampAxis(box.left + dx, blockW, areaW, margin)
        val top = clampAxis(box.top + dy, blockH, areaH, margin)
        return placement.copy(
            centerX = ((left + blockW / 2f) / areaW).coerceIn(0f, 1f),
            bottomY = ((top + blockH) / areaH).coerceIn(0f, 1f),
        )
    }

    fun zoom(placement: SubtitlePlacement, factor: Float): SubtitlePlacement {
        if (factor.isNaN() || factor <= 0f) return placement
        return placement.copy(
            scale = (placement.scale * factor).coerceIn(SubtitlePlacement.MIN_SCALE, SubtitlePlacement.MAX_SCALE),
        )
    }

    /**
     * Snaps the horizontal centre to the middle of the screen when the user drags close to it.
     * @return the snapped placement and whether a snap happened (for haptic feedback).
     */
    fun snapToCenter(placement: SubtitlePlacement, threshold: Float = 0.025f): Pair<SubtitlePlacement, Boolean> =
        if (abs(placement.centerX - 0.5f) <= threshold && placement.centerX != 0.5f) {
            placement.copy(centerX = 0.5f) to true
        } else {
            placement to false
        }

    private fun clampAxis(start: Float, size: Float, area: Float, margin: Float): Float {
        val min = margin
        val max = area - margin - size
        // A block larger than the area is centred instead of clamped to one edge.
        return if (max < min) (area - size) / 2f else start.coerceIn(min, max)
    }
}
