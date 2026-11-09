package app.mvoon.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import app.mvoon.core.player.HoldSpeed
import app.mvoon.core.player.PlaybackError
import app.mvoon.core.util.formatDuration
import app.mvoon.ui.theme.Brand
import app.mvoon.ui.theme.TabularNumbers
import kotlin.math.roundToInt
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.FastForward
import com.composables.icons.lucide.Rewind

private val Glass = Color(0xCC0E1424)

/** Vertical level pill used for brightness and volume. */
@Composable
fun LevelIndicator(icon: ImageVector, level: Float, label: String, modifier: Modifier = Modifier, maxLevel: Float = 1f) {
    val boosted = level > 1f
    Column(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Glass)
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = label, tint = if (boosted) Brand.Coral else Color.White, modifier = Modifier.size(22.dp))
        Box(
            Modifier.width(6.dp).height(140.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val fill = (level / maxLevel).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(fill).background(
                    if (boosted) Brush.verticalGradient(listOf(Brand.Coral, Brand.Azure)) else Brush.verticalGradient(Brand.colors.reversed()),
                ),
            )
            if (maxLevel > 1f) {
                // Marks the system maximum; above it is the boost.
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp / maxLevel).width(12.dp).height(2.dp).background(Color.White.copy(alpha = 0.8f)))
            }
        }
        Text(
            if (boosted) "${(level * 100).roundToInt()}%" else "${(level * 100).roundToInt()}",
            style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
            color = if (boosted) Brand.Coral else Color.White,
        )
        if (boosted) Text("BOOST", style = MaterialTheme.typography.labelSmall, color = Brand.Coral)
    }
}

/** Shown while swiping horizontally to seek. */
@Composable
fun SeekPreview(targetMs: Long, deltaMs: Long, durationMs: Long, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Glass).padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            (if (deltaMs >= 0) "+" else "−") + formatDuration(kotlin.math.abs(deltaMs)),
            style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers),
            color = Color.White,
        )
        Text(
            "${formatDuration(targetMs)} / ${formatDuration(durationMs)}",
            style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
            color = Color.White.copy(alpha = 0.75f),
        )
    }
}

/** YouTube-style feedback for double-tap seeking: a soft arc on that side with the accumulated time. */
@Composable
fun DoubleTapFeedback(side: TapSide, seconds: Int, modifier: Modifier = Modifier) {
    val isRight = side == TapSide.RIGHT
    val shape = if (isRight) RoundedCornerShape(topStartPercent = 100, bottomStartPercent = 100) else RoundedCornerShape(topEndPercent = 100, bottomEndPercent = 100)
    Box(
        modifier.fillMaxHeight().fillMaxWidth(0.36f).clip(shape).background(Color.White.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val transition = rememberInfiniteTransition(label = "chevrons")
            val pulse by transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
            Icon(
                if (isRight) Lucide.FastForward else Lucide.Rewind,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(34.dp).graphicsLayer { alpha = pulse },
            )
            Text(
                "${if (isRight) "+" else "−"}$seconds s",
                style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                color = Color.White,
            )
        }
    }
}

/** Brief centre icon confirming a double-tap play/pause while the controls are hidden. */
@Composable
fun PlayPauseFlash(playing: Boolean, nonce: Int, modifier: Modifier = Modifier) {
    val progress = remember(nonce) { Animatable(0f) }
    LaunchedEffect(nonce) { progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    Box(
        modifier
            .size(88.dp)
            .graphicsLayer {
                val p = progress.value
                val scale = 0.8f + 0.25f * p
                scaleX = scale
                scaleY = scale
                alpha = if (p < 0.15f) p / 0.15f else 1f - (p - 0.15f) / 0.85f
            }
            .clip(CircleShape)
            .background(Glass)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
            contentDescription = if (playing) "Playing" else "Paused",
            tint = Color.White,
            modifier = Modifier.size(46.dp),
        )
    }
}

/**
 * Shown while a finger holds the video down: the fast speed, a running "play forward" chevron, and
 * where the speed sits in the 1.5× – 4× range (slide sideways to move along it).
 */
@Composable
fun HoldSpeedPill(speed: Float, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Glass)
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(HoldSpeed.label(speed), style = MaterialTheme.typography.titleLarge.merge(TabularNumbers), color = Color.White)
            Spacer(Modifier.width(6.dp))
            val transition = rememberInfiniteTransition(label = "fastForward")
            val phase by transition.animateFloat(0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "phase")
            Row(horizontalArrangement = Arrangement.spacedBy((-9).dp)) {
                repeat(3) { i ->
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp).graphicsLayer { alpha = if (phase.toInt() == i) 1f else 0.35f },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            HoldSpeed.STEPS.forEach { step ->
                val width by animateDpAsState(if (step == speed) 18.dp else 6.dp, label = "step")
                Box(
                    Modifier.width(width).height(6.dp).clip(CircleShape).background(
                        when {
                            step == speed -> Brand.Azure
                            step < speed -> Color.White.copy(alpha = 0.7f)
                            else -> Color.White.copy(alpha = 0.22f)
                        },
                    ),
                )
            }
        }
        Text("Slide ← → to change speed", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
    }
}

/** Short message in a glass pill ("Crop to fill", "Zoom 140%", "Rotation locked"). */
@Composable
fun ToastPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = modifier
            .clip(CircleShape)
            .background(Glass)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Indeterminate ring in the cerulean gradient. */
@Composable
fun BufferingRing(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 56.dp) {
    val transition = rememberInfiniteTransition(label = "buffering")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "angle")
    Canvas(modifier.size(size)) {
        val stroke = 4.dp.toPx()
        drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, style = Stroke(stroke), topLeft = Offset(stroke, stroke), size = Size(this.size.width - stroke * 2, this.size.height - stroke * 2))
        drawArc(
            Brush.sweepGradient(Brand.colors + Brand.colors.first()),
            angle, 110f, false,
            style = Stroke(stroke, cap = StrokeCap.Round),
            topLeft = Offset(stroke, stroke),
            size = Size(this.size.width - stroke * 2, this.size.height - stroke * 2),
        )
    }
}

@Composable
fun ResumePill(positionMs: Long, resumed: Boolean, onPrimary: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(Glass).padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (resumed) "Resumed at ${formatDuration(positionMs)}" else "Last stopped at ${formatDuration(positionMs)}",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
        Spacer(Modifier.width(6.dp))
        TextButton(onClick = onPrimary) {
            Text(if (resumed) "Start over" else "Resume", color = Brand.Azure, style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = onDismiss) { Text("Dismiss", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
fun ErrorCard(
    error: PlaybackError,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onSkip: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onUseCompatibility: (() -> Unit)? = null,
) {
    var showDetails by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF141C2F),
            modifier = Modifier.padding(24.dp).widthIn(max = 460.dp),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Lucide.CircleAlert, null, tint = Brand.Coral, modifier = Modifier.size(32.dp))
                Text(error.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(error.message, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                if (showDetails && error.technical != null) {
                    Text(
                        error.technical!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.06f)).padding(10.dp),
                    )
                }
                if (onUseCompatibility != null) {
                    Text(
                        "Mvoon's compatibility engine (VLC) decodes many formats this phone can't. It plays the file from the same spot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Brand.Azure,
                    )
                    Button(
                        onClick = onUseCompatibility,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Play with compatibility engine") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (error.technical != null) {
                        TextButton(onClick = { showDetails = !showDetails }) {
                            Text(if (showDetails) "Hide details" else "Details", color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (onSkip != null) TextButton(onClick = onSkip) { Text("Skip", color = Color.White) }
                    TextButton(onClick = onClose) { Text("Close", color = Color.White) }
                    if (error.canRetry && onUseCompatibility != null) {
                        TextButton(onClick = onRetry) { Text("Try again", color = Color.White) }
                    } else if (error.canRetry) {
                        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                            Text("Try again")
                        }
                    }
                }
            }
        }
    }
}

/** Persistent notice when the system player plays a file only partly (e.g. audio without video). */
@Composable
fun CompatibilityNotice(reason: String, onUse: (() -> Unit)?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.widthIn(max = 480.dp).clip(RoundedCornerShape(22.dp)).background(Glass)
            .padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 2.dp),
    ) {
        Text(reason, style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.padding(end = 12.dp))
        Row(Modifier.align(Alignment.End)) {
            TextButton(onClick = onDismiss) { Text("Dismiss", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge) }
            if (onUse != null) {
                TextButton(onClick = onUse) { Text("Use compatibility engine", color = Brand.Azure, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = modifier)
}
