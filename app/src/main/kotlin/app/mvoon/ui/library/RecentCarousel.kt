package app.mvoon.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselItemDrawInfo
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.mvoon.core.media.MediaKind
import app.mvoon.library.db.LibraryRow
import app.mvoon.ui.components.AccentProgress
import app.mvoon.ui.components.MediaThumbnail
import app.mvoon.ui.components.SkeletonBox
import app.mvoon.ui.theme.Brand
import com.composables.icons.lucide.AudioLines
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Lucide

private val ShelfHeight = 176.dp
private val ShelfCardShape = RoundedCornerShape(24.dp)

/**
 * The hero card's Watching / Listening shelf: a Material multi-browse carousel. The card in front is
 * full size with its caption; the ones behind it narrow into slivers, and their captions fade out
 * so text is never cut in half.
 */
@Composable
internal fun RecentCarousel(
    items: List<LibraryRow>,
    kind: MediaKind,
    nowPlayingId: String?,
    onItem: (Int, LibraryRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.size == 1) {
        // A lone item gets the whole width instead of a carousel with nothing behind it.
        val row = items[0]
        ShelfCard(
            row,
            kind,
            playing = nowPlayingId == row.media.id,
            onClick = { onItem(0, row) },
            modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth().height(ShelfHeight).clip(ShelfCardShape),
        )
        return
    }
    // Newest first: when something new is played, start again from the front.
    key(items.first().media.id) {
        val state = rememberCarouselState { items.size }
        HorizontalMultiBrowseCarousel(
            state = state,
            preferredItemWidth = if (kind == MediaKind.VIDEO) 244.dp else 172.dp,
            modifier = modifier.fillMaxWidth().height(ShelfHeight),
            itemSpacing = 8.dp,
            minSmallItemWidth = 36.dp,
            maxSmallItemWidth = 56.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) { index ->
            val row = items[index]
            val info = carouselItemDrawInfo
            ShelfCard(
                row,
                kind,
                playing = nowPlayingId == row.media.id,
                onClick = { onItem(index, row) },
                modifier = Modifier.fillMaxSize().maskClip(ShelfCardShape),
                reveal = { captionReveal(info) },
                visibleLeft = { info.maskRect.left },
                hiddenRight = { (info.maxSize - info.maskRect.right).coerceAtLeast(0f) },
            )
        }
    }
}

/**
 * 0 while a card is narrower than the front one, 1 once it is (nearly) full size. Captions are laid
 * out at full width, so they must be invisible on any card that is visibly clipped.
 */
private fun captionReveal(info: CarouselItemDrawInfo): Float {
    val fraction = info.size / info.maxSize.coerceAtLeast(1f)
    return ((fraction - 0.86f) / 0.12f).coerceIn(0f, 1f)
}

@Composable
private fun ShelfCard(
    row: LibraryRow,
    kind: MediaKind,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    reveal: () -> Float = { 1f },
    visibleLeft: () -> Float = { 0f },
    hiddenRight: () -> Float = { 0f },
) {
    val m = row.media
    val video = kind == MediaKind.VIDEO
    val progress = row.progress
    // Videos: how much is left (the caption says when). Songs: when (the caption names the artist).
    val badge = when {
        playing -> "Now playing"
        !video -> timeAgo(row.lastPlayedAt).ifBlank { null }
        progress != null -> minutesLeftLabel(row.durationMs, row.resumePositionMs ?: 0)
        row.completed == true -> "Finished"
        else -> null
    }
    val detail = if (video) timeAgo(row.lastPlayedAt)
    else m.artist?.takeIf { it.isNotBlank() } ?: m.album?.takeIf { it.isNotBlank() } ?: ""

    Box(modifier.clickable(onClickLabel = "Play ${m.title}", onClick = onClick)) {
        MediaThumbnail(m.uri, kind, Modifier.fillMaxSize(), iconSize = 30.dp, seed = if (video) m.folderName + m.title else m.album ?: m.title)
        // A scrim so the caption reads on bright frames; lighter on the cards behind.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.45f + 0.55f * reveal() }
                .drawBehind {
                    drawRect(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f)))
                },
        )
        if (badge != null) {
            ShelfBadge(
                badge,
                accent = playing,
                done = !playing && row.completed == true,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .graphicsLayer { alpha = reveal(); translationX = visibleLeft() }
                    .padding(12.dp),
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .graphicsLayer { alpha = reveal(); translationX = visibleLeft() }
                .padding(start = 14.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    m.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (video && progress != null) {
                    AccentProgress(
                        progress,
                        Modifier.padding(top = 8.dp, end = 8.dp),
                        track = Color.White.copy(alpha = 0.28f),
                        color = Brand.Azure,
                        height = 4.dp,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .graphicsLayer { translationX = -hiddenRight() - visibleLeft() }
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brand.Azure),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Brand.OnAzure, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun ShelfBadge(text: String, accent: Boolean, done: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(if (accent) Brand.Azure else Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        when {
            accent -> Icon(Lucide.AudioLines, null, tint = Color.White, modifier = Modifier.size(12.dp))
            done -> Icon(Lucide.CircleCheck, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.08.em),
            color = Color.White,
            maxLines = 1,
        )
    }
}

/** Placeholder with the carousel's shape while the library loads. */
@Composable
internal fun RecentCarouselSkeleton(kind: MediaKind, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SkeletonBox(Modifier.weight(if (kind == MediaKind.VIDEO) 0.7f else 0.5f).height(ShelfHeight), ShelfCardShape)
        SkeletonBox(Modifier.weight(if (kind == MediaKind.VIDEO) 0.22f else 0.32f).height(ShelfHeight), ShelfCardShape)
        SkeletonBox(Modifier.weight(0.08f).height(ShelfHeight), ShelfCardShape)
    }
}

internal fun timeAgo(at: Long?, now: Long = System.currentTimeMillis()): String {
    if (at == null) return ""
    val minutes = (now - at) / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        minutes < 48 * 60 -> "Yesterday"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)} days ago"
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(at))
    }
}

internal fun minutesLeftLabel(durationMs: Long, positionMs: Long): String {
    val minutes = (durationMs - positionMs).coerceAtLeast(0) / 60_000
    return if (minutes >= 1) "$minutes min left" else "Almost done"
}
