package app.mvoon.ui.player

import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import app.mvoon.core.player.VideoSize
import app.mvoon.engine.AndroidPlayerEngine

/** Size of the video picture inside a [containerW]×[containerH] area for an aspect mode (before zoom). */
fun videoDisplaySize(containerW: Float, containerH: Float, video: VideoSize?, mode: AspectMode): Size {
    if (containerW <= 0f || containerH <= 0f) return Size.Zero
    val natural = video?.aspectRatio?.takeIf { it > 0f } ?: (16f / 9f)
    fun fit(a: Float) = if (containerW / containerH > a) Size(containerH * a, containerH) else Size(containerW, containerW / a)
    fun fill(a: Float) = if (containerW / containerH > a) Size(containerW, containerW / a) else Size(containerH * a, containerH)
    return when (mode) {
        AspectMode.FIT -> fit(natural)
        AspectMode.FILL -> fill(natural)
        AspectMode.STRETCH -> Size(containerW, containerH)
        AspectMode.RATIO_16_9, AspectMode.RATIO_4_3, AspectMode.RATIO_21_9 -> fit(mode.forcedAspect!!)
        AspectMode.ORIGINAL -> video?.let { Size(it.width * it.pixelWidthHeightRatio, it.height.toFloat()) } ?: fit(natural)
    }
}

/**
 * Hosts the SurfaceView (most power-efficient output; supports HDR and hardware overlays).
 * Zoom/pan are applied as view transforms, which SurfaceView honours on Android 7+.
 */
@Composable
fun VideoSurface(
    engine: AndroidPlayerEngine,
    videoSize: VideoSize?,
    aspectMode: AspectMode,
    zoom: Float,
    pan: Offset,
    onVideoRect: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        val size = videoDisplaySize(cw, ch, videoSize, aspectMode)
        val density = LocalDensity.current
        val widthDp = with(density) { size.width.toDp() }
        val heightDp = with(density) { size.height.toDp() }
        Box(Modifier.size(widthDp, heightDp)) {
            AndroidView(
                factory = { context -> SurfaceView(context).also(engine::attachSurface) },
                update = { view ->
                    view.scaleX = zoom
                    view.scaleY = zoom
                    view.translationX = pan.x
                    view.translationY = pan.y
                },
                onRelease = engine::detachSurface,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val scaled = Size(size.width * zoom, size.height * zoom)
        val rect = Rect(Offset((cw - scaled.width) / 2f + pan.x, (ch - scaled.height) / 2f + pan.y), scaled)
        LaunchedEffect(rect) { onVideoRect(rect) }
    }
}
