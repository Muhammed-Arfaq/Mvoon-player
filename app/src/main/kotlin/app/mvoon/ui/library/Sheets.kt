package app.mvoon.ui.library

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mvoon.core.media.PlayableItem
import app.mvoon.core.media.StreamType
import app.mvoon.core.settings.SortField
import app.mvoon.core.settings.SortOrder
import app.mvoon.core.util.formatDuration
import app.mvoon.core.util.formatFileSize
import app.mvoon.core.util.resolutionLabel
import app.mvoon.library.db.LibraryRow
import app.mvoon.library.db.PlaylistSummary
import app.mvoon.ui.components.MvoonChip
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.FilePlus
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.ListPlus
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Trash2

@Composable
private fun SheetAction(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(icon, null, tint = if (destructive) scheme.error else scheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (destructive) scheme.error else androidx.compose.ui.graphics.Color.Unspecified)
    }
}

@Composable
fun ItemActionsSheet(
    row: LibraryRow,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayFromStart: () -> Unit,
    onFavorite: (Boolean) -> Unit,
    onWatched: (Boolean) -> Unit,
    onAddToPlaylist: () -> Unit,
    onHideFolder: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var showInfo by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Text(row.media.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
            Text(row.media.folderPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SheetAction(Icons.Rounded.PlayArrow, if ((row.resumePositionMs ?: 0) > 0) "Resume" else "Play") { onPlay(); onDismiss() }
            if ((row.resumePositionMs ?: 0) > 0) SheetAction(Lucide.RotateCcw, "Play from start") { onPlayFromStart(); onDismiss() }
            SheetAction(if (row.isFavorite) Icons.Rounded.Favorite else Lucide.Heart, if (row.isFavorite) "Remove from favourites" else "Add to favourites") {
                onFavorite(!row.isFavorite); onDismiss()
            }
            SheetAction(Lucide.ListPlus, "Add to playlist…") { onAddToPlaylist() }
            val watched = row.completed == true
            SheetAction(if (watched) Lucide.Circle else Lucide.CircleCheck, if (watched) "Mark as not played" else "Mark as played") {
                onWatched(!watched); onDismiss()
            }
            SheetAction(Lucide.Share2, "Share") {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = row.media.mimeType ?: "video/*"
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(row.media.uri))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(Intent.createChooser(intent, row.media.title)) }
                onDismiss()
            }
            SheetAction(Lucide.Info, if (showInfo) "Hide details" else "Details") { showInfo = !showInfo }
            if (showInfo) {
                val m = row.media
                Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOfNotNull(
                        "File" to m.fileName,
                        "Location" to m.folderPath,
                        m.sizeBytes.takeIf { it > 0 }?.let { "Size" to formatFileSize(it) },
                        m.durationMs.takeIf { it > 0 }?.let { "Duration" to formatDuration(it) },
                        resolutionLabel(m.width, m.height)?.let { "Resolution" to "${m.width}×${m.height} ($it)" },
                        m.mimeType?.let { "Type" to it },
                    ).forEach { (k, v) ->
                        Row {
                            Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp).size(width = 84.dp, height = 18.dp))
                            Text(v, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            SheetAction(Lucide.EyeOff, "Hide this folder from the library") { onHideFolder(); onDismiss() }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            SheetAction(Lucide.Trash2, "Delete from device", destructive = true) { onDelete(); onDismiss() }
        }
    }
}

@Composable
fun AddToPlaylistDialog(
    items: List<PlayableItem>,
    playlists: List<PlaylistSummary>,
    onDismiss: () -> Unit,
    onAdd: (Long) -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${if (items.size == 1) "to playlist" else "${items.size} items"}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                playlists.forEach { p ->
                    Text(
                        "${p.playlist.name}  ·  ${p.itemCount}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { onAdd(p.playlist.id) }.padding(vertical = 12.dp),
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("New playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name.ifBlank { "My playlist" }) }) { Text("Create & add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortSheet(current: SortOrder, onDismiss: () -> Unit, onSelect: (SortOrder) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Sort by", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            listOf(
                SortField.DATE_ADDED to "Date added",
                SortField.TITLE to "Name",
                SortField.DURATION to "Length",
                SortField.SIZE to "File size",
                SortField.LAST_PLAYED to "Last played",
            ).forEach { (field, label) ->
                SheetAction(if (current.field == field) Lucide.CircleCheck else Lucide.Circle, label) {
                    onSelect(current.copy(field = field))
                }
            }
            FlowRow(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MvoonChip("Ascending", !current.descending, { onSelect(current.copy(descending = false)) })
                MvoonChip("Descending", current.descending, { onSelect(current.copy(descending = true)) })
            }
        }
    }
}

@Composable
fun AddSourceSheet(onDismiss: () -> Unit, onOpenFile: () -> Unit, onAddFolder: () -> Unit, onNetwork: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Open", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            SheetAction(Lucide.FilePlus, "Open a file…") { onOpenFile(); onDismiss() }
            SheetAction(Lucide.FolderOpen, "Add a folder to the library…") { onAddFolder(); onDismiss() }
            SheetAction(Lucide.Globe, "Play a network stream…") { onNetwork(); onDismiss() }
            Text(
                "Folders added here (SD cards, USB drives, Download) are indexed even when Android's media scanner skips them, and let Mvoon find subtitle files next to your videos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpenUrlDialog(recent: List<String>, onDismiss: () -> Unit, onPlay: (String, StreamType, String?) -> Unit) {
    val context = LocalContext.current
    val clip = remember {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim()
            ?.takeIf { it.matches(Regex("(?i)^(https?|rtsp)://\\S+$")) }
    }
    var url by remember { mutableStateOf(clip ?: "") }
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(StreamType.AUTO) }
    val valid = url.trim().matches(Regex("(?i)^(https?|rtsp)://\\S+$"))
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Lucide.Globe, null) },
        title = { Text("Network stream") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://…/stream.m3u8") },
                    singleLine = true,
                    isError = url.isNotBlank() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Format", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(StreamType.AUTO to "Auto", StreamType.HLS to "HLS", StreamType.DASH to "DASH", StreamType.RTSP to "RTSP", StreamType.SMOOTH_STREAMING to "Smooth", StreamType.PROGRESSIVE to "File")
                        .forEach { (t, label) -> MvoonChip(label, type == t, { type = t }) }
                }
                Text(
                    "Supports HLS (.m3u8), MPEG-DASH (.mpd), SmoothStreaming, RTSP and direct links to media files. DRM-protected streams are not supported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (recent.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Lucide.History, null, modifier = Modifier.size(16.dp))
                        Text("Recent", style = MaterialTheme.typography.labelLarge)
                    }
                    recent.take(5).forEach { r ->
                        Text(r, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().clickable { url = r }.padding(vertical = 6.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onPlay(url.trim(), type, title.ifBlank { null }) }) { Text("Play") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
