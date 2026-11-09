package app.mvoon.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mvoon.core.util.formatDuration
import app.mvoon.core.util.formatSpeed
import app.mvoon.ui.theme.Brand
import app.mvoon.ui.theme.TabularNumbers
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.AudioLines
import com.composables.icons.lucide.Captions
import com.composables.icons.lucide.CaptionsOff
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.ListVideo
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.PictureInPicture2
import com.composables.icons.lucide.Ratio
import com.composables.icons.lucide.RotateCw

/** Everything the controls can ask the player to do. */
class PlayerActions(
    val back: () -> Unit,
    val playPause: () -> Unit,
    val seekBy: (Long) -> Unit,
    val scrubStart: () -> Unit,
    val scrub: (Long) -> Unit,
    val scrubEnd: (Long) -> Unit,
    val previous: () -> Unit,
    val next: () -> Unit,
    val lock: () -> Unit,
    val cycleAspect: () -> Unit,
    val rotate: () -> Unit,
    val pip: () -> Unit,
    val openPanel: (Panel) -> Unit,
)

data class ControlsModel(
    val title: String,
    val meta: String?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val bufferedMs: Long,
    val seekable: Boolean,
    val isLive: Boolean,
    val hasQueue: Boolean,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val subtitlesOn: Boolean,
    val hasSubtitles: Boolean,
    val audioTrackCount: Int,
    /** Short label of the selected audio track, e.g. "EN 2/3". */
    val audioLabel: String? = null,
    val speed: Float,
    val seekIncrementMs: Long,
    val pipSupported: Boolean,
    val buffering: Boolean = false,
)

@Composable
fun PlayerControls(model: ControlsModel, actions: PlayerActions, safePadding: PaddingValues, compact: Boolean) {
    Box(Modifier.fillMaxSize()) {
        // Scrims keep white controls legible over bright footage without dimming the whole picture.
        Box(
            Modifier.fillMaxWidth().height(150.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent))),
        )
        Box(
            Modifier.fillMaxWidth().height(if (compact) 210.dp else 190.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)))),
        )

        Column(Modifier.fillMaxSize().padding(safePadding)) {
            TopBar(model, actions)
            Spacer(Modifier.weight(1f))
            BottomBar(model, actions, compact)
        }

        CenterTransport(model, actions, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun TopBar(model: ControlsModel, actions: PlayerActions) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlIcon(Lucide.ChevronLeft, "Back", actions.back)
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                model.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (model.meta != null) {
                Text(
                    model.meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (model.hasSubtitles) {
            ControlIcon(
                if (model.subtitlesOn) Lucide.Captions else Lucide.CaptionsOff,
                if (model.subtitlesOn) "Subtitles on" else "Subtitles off",
                onClick = { actions.openPanel(Panel.SUBTITLES) },
            )
        } else {
            ControlIcon(Lucide.CaptionsOff, "Subtitles", onClick = { actions.openPanel(Panel.SUBTITLES) })
        }
        if (model.audioTrackCount > 1) {
            Row(
                Modifier
                    .padding(horizontal = 4.dp)
                    .clip(CircleShape)
                    .background(Brand.Azure.copy(alpha = 0.22f))
                    .clickable(onClickLabel = "Audio tracks") { actions.openPanel(Panel.AUDIO) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Lucide.AudioLines, contentDescription = null, tint = Brand.Sky, modifier = Modifier.size(18.dp))
                Text(model.audioLabel ?: "${model.audioTrackCount}", style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
            }
        }
        ControlIcon(Lucide.EllipsisVertical, "More options", onClick = { actions.openPanel(Panel.MORE) })
    }
}

@Composable
private fun CenterTransport(model: ControlsModel, actions: PlayerActions, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        if (model.hasQueue) {
            ControlIcon(Icons.Rounded.SkipPrevious, "Previous", actions.previous, size = 34.dp, enabled = model.hasPrevious || model.seekable)
        }
        if (model.seekable) {
            ControlIcon(Icons.Rounded.Replay10, "Back ${model.seekIncrementMs / 1000} seconds", { actions.seekBy(-model.seekIncrementMs) }, size = 34.dp)
        }
        Box(contentAlignment = Alignment.Center) {
            PlayPauseButton(model.isPlaying, actions.playPause)
            if (model.buffering) BufferingRing(size = 92.dp)
        }
        if (model.seekable) {
            ControlIcon(Icons.Rounded.Forward10, "Forward ${model.seekIncrementMs / 1000} seconds", { actions.seekBy(model.seekIncrementMs) }, size = 34.dp)
        }
        if (model.hasQueue) ControlIcon(Icons.Rounded.SkipNext, "Next", actions.next, size = 34.dp, enabled = model.hasNext)
    }
}

@Composable
fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 76.dp, tint: Color = Brand.OnAzure, container: Color = Brand.Azure) {
    // A second tap right after the first is ignored: "double-tap the middle to pause" landing on this
    // button must pause, not pause and immediately play again.
    var lastClick by remember { mutableLongStateOf(0L) }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(onClickLabel = if (isPlaying) "Pause" else "Play") {
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastClick >= 400) {
                    lastClick = now
                    onClick()
                }
            }
            .semantics { contentDescription = if (isPlaying) "Pause" else "Play" },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut()) },
            label = "playPause",
        ) { playing ->
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

@Composable
private fun BottomBar(model: ControlsModel, actions: PlayerActions, compact: Boolean) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }
    var showRemaining by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        val shownPosition = if (scrubbing) scrubPosition.toLong() else model.positionMs
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (model.isLive) "LIVE" else formatDuration(shownPosition),
                style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                color = if (model.isLive) Brand.Coral else Color.White,
            )
            Spacer(Modifier.weight(1f))
            if (!model.isLive && model.durationMs > 0) {
                Text(
                    if (showRemaining) formatDuration(shownPosition - model.durationMs) else formatDuration(model.durationMs),
                    style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClickLabel = "Toggle remaining time") { showRemaining = !showRemaining }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        MvoonSeekBar(
            positionMs = model.positionMs,
            durationMs = model.durationMs,
            bufferedMs = model.bufferedMs,
            enabled = model.seekable && model.durationMs > 0,
            onScrubStart = { scrubbing = true; actions.scrubStart() },
            onScrub = { scrubPosition = it.toFloat(); actions.scrub(it) },
            onScrubEnd = { scrubbing = false; actions.scrubEnd(it) },
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (compact) Arrangement.SpaceBetween else Arrangement.spacedBy(6.dp),
        ) {
            ControlIcon(Lucide.Lock, "Lock screen", actions.lock, size = 24.dp)
            ControlIcon(Lucide.Ratio, "Aspect ratio", actions.cycleAspect, size = 24.dp)
            ControlIcon(Lucide.RotateCw, "Rotation", actions.rotate, size = 24.dp)
            if (!compact) Spacer(Modifier.weight(1f))
            SpeedChip(model.speed) { actions.openPanel(Panel.SPEED) }
            if (model.hasQueue) ControlIcon(Lucide.ListVideo, "Queue", { actions.openPanel(Panel.QUEUE) }, size = 24.dp)
            if (model.pipSupported) ControlIcon(Lucide.PictureInPicture2, "Picture-in-picture", actions.pip, size = 24.dp)
        }
    }
}

@Composable
private fun SpeedChip(speed: Float, onClick: () -> Unit) {
    Text(
        formatSpeed(speed),
        style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
        color = if (speed != 1f) Color.Black else Color.White,
        modifier = Modifier
            .clip(CircleShape)
            .then(if (speed != 1f) Modifier.background(Brand.horizontal()) else Modifier.background(Color.White.copy(alpha = 0.14f)))
            .clickable(onClickLabel = "Playback speed", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun ControlIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 24.dp,
    enabled: Boolean = true,
    tint: Color = Color.White,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(maxOf(48.dp, size + 20.dp))) {
        Icon(icon, contentDescription = description, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(size))
    }
}

/**
 * Timeline with buffered range, iridescent progress and a thumb that swells while dragging.
 * Scrubbing reports positions continuously (the engine renders preview frames in scrubbing mode).
 */
@Composable
fun MvoonSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    enabled: Boolean,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.22f),
    bufferColor: Color = Color.White.copy(alpha = 0.38f),
    thumbColor: Color = Color.White,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val duration = durationMs.coerceAtLeast(1)
    val fraction = if (dragging) dragFraction else (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val buffered = (bufferedMs.toFloat() / duration).coerceIn(0f, 1f)
    val thumb by animateDpAsState(if (dragging) 9.dp else 6.dp, spring(stiffness = Spring.StiffnessMediumLow), label = "thumb")
    val trackHeight by animateDpAsState(if (dragging) 6.dp else 4.dp, label = "track")
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics {
                contentDescription = "Playback position"
                stateDescription = "${formatDuration(positionMs)} of ${formatDuration(durationMs)}"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                if (enabled) {
                    setProgress { value ->
                        onScrubEnd((value.coerceIn(0f, 1f) * duration).toLong())
                        true
                    }
                }
            }
            .pointerInput(enabled, duration) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onScrubEnd(((offset.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                }
            }
            .pointerInput(enabled, duration) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onScrubStart()
                        onScrub((dragFraction * duration).toLong())
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onScrub((dragFraction * duration).toLong())
                    },
                    onDragEnd = {
                        dragging = false
                        onScrubEnd((dragFraction * duration).toLong())
                    },
                    onDragCancel = {
                        dragging = false
                        onScrubEnd((dragFraction * duration).toLong())
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            val h = trackHeight.toPx()
            val y = size.height / 2f - h / 2f
            val r = CornerRadius(h / 2f)
            drawRoundRect(trackColor, Offset(0f, y), Size(size.width, h), r)
            drawRoundRect(bufferColor, Offset(0f, y), Size(size.width * buffered, h), r)
            drawRoundRect(Brand.horizontal(), Offset(0f, y), Size(size.width * fraction, h), r)
            val cx = size.width * fraction
            if (dragging) drawCircle(Brand.Sky.copy(alpha = 0.35f), thumb.toPx() * 2.1f, Offset(cx, size.height / 2f))
            drawCircle(thumbColor, thumb.toPx(), Offset(cx, size.height / 2f))
        }
        AnimatedVisibility(
            visible = dragging,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = with(density) { (widthPx * fraction).toDp() } - 34.dp, y = (-34).dp),
        ) {
            Text(
                formatDuration((dragFraction * duration).toLong()),
                style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                color = Color.Black,
                modifier = Modifier
                    .width(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .padding(vertical = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
