package app.mvoon.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mvoon.ui.theme.LocalMvoonExtras
import kotlinx.coroutines.delay

/** A soft light sweep across a placeholder while content loads. */
fun Modifier.shimmer(): Modifier = composed {
    val dark = LocalMvoonExtras.current.isDark
    val scheme = MaterialTheme.colorScheme
    val base = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerHigh
    val highlight = if (dark) scheme.surfaceContainerHighest else scheme.surfaceContainerLowest
    val sweep by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1_250, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    drawBehind {
        val w = size.width
        drawRect(
            Brush.linearGradient(
                listOf(base, highlight, base),
                start = Offset(w * sweep - w, 0f),
                end = Offset(w * sweep, size.height),
            ),
        )
    }
}

@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    Box(modifier.clip(shape).shimmer())
}

@Composable
fun SkeletonLine(width: Dp, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    SkeletonBox(modifier.width(width).height(height), RoundedCornerShape(height / 2))
}

/** Placeholder shaped like a video card while the library loads. */
@Composable
fun VideoCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SkeletonBox(Modifier.fillMaxWidth().aspectRatio(16f / 10f), RoundedCornerShape(22.dp))
        SkeletonLine(130.dp, Modifier.padding(start = 4.dp))
        SkeletonLine(80.dp, Modifier.padding(start = 4.dp), height = 10.dp)
    }
}

/** Placeholder shaped like a list row (song, folder, recent item). */
@Composable
fun RowSkeleton(modifier: Modifier = Modifier, square: Boolean = true) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SkeletonBox(if (square) Modifier.size(52.dp) else Modifier.width(92.dp).aspectRatio(16f / 10f), RoundedCornerShape(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonLine(170.dp)
            SkeletonLine(110.dp, height = 10.dp)
        }
        SkeletonLine(34.dp, height = 10.dp)
    }
}

/**
 * Remembers which list items already played their entrance, so the first screenful cascades in
 * once and items scrolled back into view don't animate again.
 */
class StaggerState internal constructor() {
    internal val shown = HashSet<Any>()
    internal val createdAt = SystemClock.uptimeMillis()
}

@Composable
fun rememberStaggerState(): StaggerState = remember { StaggerState() }

/** Fade and rise into place, delayed by position, the first time [key] appears. */
fun Modifier.staggeredEntrance(state: StaggerState, key: Any, index: Int): Modifier = composed {
    val animate = remember(key) {
        key !in state.shown && index < 14 && SystemClock.uptimeMillis() - state.createdAt < 1_500
    }
    if (!animate) {
        state.shown += key
        return@composed this
    }
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        state.shown += key
        delay(index * 38L)
        progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 260f))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 28.dp.toPx()
    }
}
