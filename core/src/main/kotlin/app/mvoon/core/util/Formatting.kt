package app.mvoon.core.util

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/** 65_000 → "1:05", 3_725_000 → "1:02:05". Negative values get a leading minus. */
fun formatDuration(ms: Long, forceHours: Boolean = false): String {
    val negative = ms < 0
    val totalSeconds = abs(ms) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    val body = if (h > 0 || forceHours) {
        "$h:${m.pad2()}:${s.pad2()}"
    } else {
        "$m:${s.pad2()}"
    }
    return if (negative) "-$body" else body
}

/** Compact signed offset for sync controls: 0 → "0 ms", 1250 → "+1.25 s", -80 → "−80 ms". */
fun formatOffset(ms: Long): String {
    if (ms == 0L) return "0 ms"
    val sign = if (ms > 0) "+" else "−"
    val a = abs(ms)
    return if (a < 1000) "$sign$a ms" else {
        val seconds = a / 1000.0
        val text = if (a % 1000 == 0L) seconds.toLong().toString() else "%.2f".format(seconds).trimEnd('0').trimEnd('.')
        "$sign$text s"
    }
}

/** 1536 → "1.5 KB" (binary units, one decimal). */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val group = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(group)
    return if (group == 0) "$bytes B" else "%.1f %s".format(value, units[group])
}

fun formatSpeed(speed: Float): String {
    val text = if (speed % 1f == 0f) "%.1f".format(speed) else "%.2f".format(speed).trimEnd('0')
    return "$text×"
}

/** "1920×1080" → "1080p", 3840×2160 → "4K". Uses the short side so portrait video is labelled right. */
fun resolutionLabel(width: Int, height: Int): String? {
    if (width <= 0 || height <= 0) return null
    val shortSide = minOf(width, height)
    val longSide = maxOf(width, height)
    return when {
        longSide >= 7680 -> "8K"
        longSide >= 3800 -> "4K"
        longSide >= 2500 -> "1440p"
        shortSide >= 1000 || longSide >= 1900 -> "1080p"
        shortSide >= 700 || longSide >= 1270 -> "720p"
        shortSide >= 470 -> "480p"
        else -> "${shortSide}p"
    }
}

private fun Long.pad2(): String = toString().padStart(2, '0')
