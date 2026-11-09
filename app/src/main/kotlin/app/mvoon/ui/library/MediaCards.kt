package app.mvoon.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mvoon.core.media.MediaKind
import app.mvoon.core.util.formatDuration
import app.mvoon.core.util.formatFileSize
import app.mvoon.core.util.resolutionLabel
import app.mvoon.library.db.FolderRow
import app.mvoon.library.db.LibraryRow
import app.mvoon.library.db.PlaylistSummary
import app.mvoon.library.db.kindOf
import app.mvoon.ui.components.Badge
import app.mvoon.ui.components.GeneratedArt
import app.mvoon.ui.components.AccentProgress
import app.mvoon.ui.components.IconTile
import app.mvoon.ui.components.MediaThumbnail
import app.mvoon.ui.components.rememberInteraction
import app.mvoon.ui.components.rememberPressScale
import app.mvoon.ui.theme.LocalMvoonExtras
import app.mvoon.ui.theme.Brand
import app.mvoon.ui.theme.TabularNumbers
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Lucide

private val CardShape = RoundedCornerShape(24.dp)
private val ThumbShape = RoundedCornerShape(18.dp)

@Composable
fun VideoCard(row: LibraryRow, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = rememberInteraction()
    val scale = rememberPressScale(interaction)
    val m = row.media
    val extras = LocalMvoonExtras.current
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CardShape)
            .background(extras.card)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "More options")
            .padding(7.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${m.title}, ${formatDuration(row.durationMs)}" },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(ThumbShape)) {
            MediaThumbnail(m.uri, MediaKind.VIDEO, Modifier.fillMaxSize(), seed = m.folderName + m.title)
            Row(Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                resolutionLabel(m.width, m.height)?.takeIf { it == "4K" || it == "8K" || it == "1440p" }?.let { Badge(it) }
                if (row.isNew()) Badge("NEW", container = Brand.Azure, content = Brand.OnAzure)
            }
            if (row.isFavorite) {
                Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Favorite, "Favourite", tint = Brand.Coral, modifier = Modifier.size(15.dp))
                }
            }
            if (row.durationMs > 0) {
                Badge(formatDuration(row.durationMs), Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
            row.progress?.let {
                AccentProgress(it, Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 8.dp).padding(end = 52.dp), track = Color.White.copy(alpha = 0.35f), height = 4.dp)
            }
        }
        Text(
            m.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 9.dp),
        )
        Text(
            listOfNotNull(m.folderName, m.sizeBytes.takeIf { it > 0 }?.let(::formatFileSize)).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 1.dp, bottom = 5.dp),
        )
    }
}

@Composable
fun MediaRow(row: LibraryRow, onClick: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier, playing: Boolean = false) {
    val m = row.media
    val isAudio = kindOf(m.kind) == MediaKind.AUDIO
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(if (playing) Modifier.background(scheme.primary.copy(alpha = 0.16f)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onMore, onLongClickLabel = "More options")
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .then(if (isAudio) Modifier.size(52.dp) else Modifier.width(104.dp).aspectRatio(16f / 10f))
                .clip(RoundedCornerShape(16.dp)),
        ) {
            MediaThumbnail(m.uri, if (isAudio) MediaKind.AUDIO else MediaKind.VIDEO, Modifier.fillMaxSize(), iconSize = 20.dp, seed = if (isAudio) m.album ?: m.title else m.folderName + m.title)
            row.progress?.let { AccentProgress(it, Modifier.align(Alignment.BottomCenter).padding(6.dp), track = Color.White.copy(alpha = 0.35f), height = 3.dp) }
        }
        Column(Modifier.weight(1f)) {
            Text(m.title, style = MaterialTheme.typography.titleSmall, maxLines = if (isAudio) 1 else 2, overflow = TextOverflow.Ellipsis, color = if (playing) scheme.onSurface else scheme.onSurface)
            Text(
                if (isAudio) {
                    listOfNotNull(m.artist, m.album).joinToString(" · ").ifBlank { m.folderName }
                } else {
                    listOfNotNull(resolutionLabel(m.width, m.height), m.sizeBytes.takeIf { it > 0 }?.let(::formatFileSize), m.folderName).joinToString(" · ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (row.isFavorite) Icon(Icons.Rounded.Favorite, "Favourite", tint = Brand.Coral, modifier = Modifier.size(15.dp))
        if (row.durationMs > 0) {
            Text(formatDuration(row.durationMs), style = MaterialTheme.typography.labelMedium.merge(TabularNumbers), color = scheme.onSurfaceVariant)
        }
        IconButton(onClick = onMore) { Icon(Lucide.EllipsisVertical, "More options for ${m.title}", tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
fun FolderCard(folder: FolderRow, onClick: () -> Unit, onLongClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(LocalMvoonExtras.current.card)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Folder options")
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp))) {
            if (folder.coverUri != null) {
                MediaThumbnail(folder.coverUri, kindOf(folder.coverKind), Modifier.fillMaxSize(), seed = folder.folderName)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                Icon(Lucide.Folder, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(20.dp))
            } else {
                IconTile(Lucide.Folder, size = 58.dp, container = scheme.primary.copy(alpha = 0.2f))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(folder.folderName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    folder.videoCount.takeIf { it > 0 }?.let { "$it video${if (it == 1) "" else "s"}" },
                    folder.audioCount.takeIf { it > 0 }?.let { "$it track${if (it == 1) "" else "s"}" },
                    formatFileSize(folder.totalBytes),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(folder.folderPath, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Lucide.ChevronRight, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun PlaylistCard(summary: PlaylistSummary, onClick: () -> Unit, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = rememberInteraction()
    val scale = rememberPressScale(interaction)
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CardShape)
            .background(LocalMvoonExtras.current.card)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp))) {
            if (summary.coverUri != null) {
                MediaThumbnail(summary.coverUri, MediaKind.VIDEO, Modifier.fillMaxSize(), seed = summary.playlist.name)
            } else {
                GeneratedArt(summary.playlist.name, MediaKind.AUDIO, iconSize = 36.dp)
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(10.dp).size(42.dp).clip(CircleShape).background(Brand.Azure)
                    .combinedClickable(onClick = onPlay, onClickLabel = "Play ${summary.playlist.name}"),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, null, tint = Brand.OnAzure) }
        }
        Text(summary.playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp, top = 10.dp))
        Text(
            "${summary.itemCount} item${if (summary.itemCount == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
        )
    }
}
