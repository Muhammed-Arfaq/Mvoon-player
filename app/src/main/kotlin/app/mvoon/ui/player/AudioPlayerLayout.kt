package app.mvoon.ui.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mvoon.core.media.MediaKind
import app.mvoon.core.player.PlaybackState
import app.mvoon.core.player.RepeatMode
import app.mvoon.core.player.SleepTimerState
import app.mvoon.core.util.formatDuration
import app.mvoon.core.util.formatSpeed
import app.mvoon.ui.components.MediaThumbnail
import app.mvoon.ui.theme.Brand
import app.mvoon.ui.theme.TabularNumbers
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.ListVideo
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Repeat
import com.composables.icons.lucide.Repeat1
import com.composables.icons.lucide.Shuffle

@Composable
fun AudioPlayerLayout(
    state: PlaybackState,
    positionMs: Long,
    bufferedMs: Long,
    sleep: SleepTimerState,
    actions: PlayerActions,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
    safePadding: PaddingValues,
) {
    val item = state.currentItem
    Box(Modifier.fillMaxSize().background(Brand.Midnight)) {
        // Ambient backdrop: the artwork itself, heavily blurred (Android 12+), under a deep gradient.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaThumbnail(item?.artworkUri ?: item?.uri, MediaKind.AUDIO, Modifier.fillMaxSize().blur(70.dp).graphicsLayer { alpha = 0.55f }, seed = item?.subtitle ?: item?.title)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Brand.Midnight.copy(alpha = 0.95f)))))

        BoxWithConstraints(Modifier.fillMaxSize().padding(safePadding)) {
            val landscape = maxWidth > maxHeight
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ControlIcon(Lucide.ChevronLeft, "Back", actions.back)
                    Text(
                        "Now playing",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    ControlIcon(Lucide.EllipsisVertical, "More options", { actions.openPanel(Panel.MORE) })
                }
                if (landscape) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Artwork(item?.artworkUri ?: item?.uri, item?.subtitle ?: item?.title, Modifier.fillMaxHeight(0.82f))
                        Column(Modifier.weight(1f)) { Details(state, positionMs, bufferedMs, sleep, actions, onRepeat, onShuffle) }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.weight(0.6f))
                        Artwork(item?.artworkUri ?: item?.uri, item?.subtitle ?: item?.title, Modifier.fillMaxWidth().widthIn(max = 360.dp))
                        Spacer(Modifier.weight(0.5f))
                        Details(state, positionMs, bufferedMs, sleep, actions, onRepeat, onShuffle)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Artwork(uri: String?, seed: String?, modifier: Modifier) {
    MediaThumbnail(
        uri,
        MediaKind.AUDIO,
        modifier
            .aspectRatio(1f)
            .shadow(36.dp, RoundedCornerShape(32.dp), ambientColor = Brand.Azure, spotColor = Brand.Azure)
            .clip(RoundedCornerShape(32.dp)),
        iconSize = 72.dp,
        seed = seed,
    )
}

@Composable
private fun Details(
    state: PlaybackState,
    positionMs: Long,
    bufferedMs: Long,
    sleep: SleepTimerState,
    actions: PlayerActions,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
) {
    val item = state.currentItem
    Column(Modifier.fillMaxWidth()) {
        Text(
            item?.title ?: "",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item?.subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(18.dp))
        MvoonSeekBar(
            positionMs = positionMs,
            durationMs = state.durationMs,
            bufferedMs = bufferedMs,
            enabled = state.isSeekable && state.durationMs > 0,
            onScrubStart = actions.scrubStart,
            onScrub = actions.scrub,
            onScrubEnd = actions.scrubEnd,
        )
        Row {
            Text(formatDuration(positionMs), style = MaterialTheme.typography.labelMedium.merge(TabularNumbers), color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.weight(1f))
            Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelMedium.merge(TabularNumbers), color = Color.White.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ControlIcon(Lucide.Shuffle, if (state.shuffle) "Shuffle on" else "Shuffle off", onShuffle, tint = if (state.shuffle) Brand.Azure else Color.White)
            ControlIcon(Icons.Rounded.SkipPrevious, "Previous", actions.previous, size = 36.dp)
            PlayPauseButton(state.isPlaying, actions.playPause, size = 78.dp)
            ControlIcon(Icons.Rounded.SkipNext, "Next", actions.next, size = 36.dp, enabled = state.hasNext)
            ControlIcon(
                if (state.repeatMode == RepeatMode.ONE) Lucide.Repeat1 else Lucide.Repeat,
                "Repeat ${state.repeatMode.name.lowercase()}",
                onRepeat,
                tint = if (state.repeatMode != RepeatMode.OFF) Brand.Azure else Color.White,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatSpeed(state.speed),
                style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                color = Color.White,
                modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { actions.openPanel(Panel.SPEED) }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
            ControlIcon(Lucide.Moon, if (sleep.active) "Sleep timer on" else "Sleep timer", { actions.openPanel(Panel.SLEEP) }, tint = if (sleep.active) Brand.Azure else Color.White)
            ControlIcon(Lucide.ListVideo, "Queue", { actions.openPanel(Panel.QUEUE) })
        }
    }
}
