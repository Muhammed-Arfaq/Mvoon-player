package app.mvoon.ui.library

import androidx.compose.foundation.layout.size
import app.mvoon.ui.components.SoftIconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mvoon.core.media.MediaKind
import app.mvoon.library.db.LibraryRow
import app.mvoon.library.db.mediaKind
import app.mvoon.ui.components.EmptyState
import app.mvoon.ui.components.MediaThumbnail
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Shuffle
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X

@Composable
private fun DetailHeader(title: String, subtitle: String?, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 4.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SoftIconButton(Lucide.ChevronLeft, "Back", onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun FolderScreen(vm: LibraryViewModel, folderKey: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val rows by remember(folderKey) { vm.repo.folderItems(folderKey) }.collectAsState(initial = emptyList())
    val sorted = remember(rows) { rows.sortedWith { a, b -> naturalCompare(a.media.title, b.media.title) } }
    var actionRow by remember { mutableStateOf<LibraryRow?>(null) }
    val deleteMedia = rememberDeleteMedia(vm)
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var playlistTarget by remember { mutableStateOf<LibraryRow?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DetailHeader(sorted.firstOrNull()?.media?.folderName ?: "Folder", sorted.firstOrNull()?.media?.folderPath, onBack) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 32.dp),
        ) {
            if (sorted.isNotEmpty()) {
                item {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { vm.play(context, sorted, 0) }) { Icon(Icons.Rounded.PlayArrow, null); Text(" Play all") }
                        Button(
                            onClick = { vm.play(context, sorted, 0, shuffle = true) },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                        ) { Icon(Lucide.Shuffle, null, Modifier.size(18.dp)); Text(" Shuffle") }
                    }
                }
            }
            itemsIndexed(sorted, key = { _, r -> r.media.id }) { index, row ->
                MediaRow(row, onClick = { vm.play(context, sorted, index) }, onMore = { actionRow = row })
            }
            if (sorted.isEmpty()) item { EmptyState("Empty folder", "There's nothing playable here any more.") }
        }
    }
    actionRow?.let { row ->
        ItemActionsSheet(
            row, onDismiss = { actionRow = null },
            onPlay = { vm.play(context, listOf(row), 0) },
            onPlayFromStart = { vm.forget(row.media.id); vm.play(context, listOf(row), 0) },
            onFavorite = { vm.setFavorite(row.media.id, it) },
            onWatched = { vm.setWatched(row.media.id, it) },
            onAddToPlaylist = { playlistTarget = row; actionRow = null },
            onHideFolder = { vm.hideFolder(row.media.folderKey); onBack() },
            onDelete = { deleteMedia(row) },
        )
    }
    playlistTarget?.let { row ->
        AddToPlaylistDialog(
            listOf(row.toPlayable()), playlists,
            onDismiss = { playlistTarget = null },
            onAdd = { vm.addToPlaylist(it, listOf(row.toPlayable())); playlistTarget = null },
            onCreate = { vm.createPlaylist(it, listOf(row.toPlayable())); playlistTarget = null },
        )
    }
}

@Composable
fun PlaylistScreen(vm: LibraryViewModel, playlistId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val playlist by remember(playlistId) { vm.repo.playlist(playlistId) }.collectAsState(initial = null)
    val items by remember(playlistId) { vm.repo.playlistItems(playlistId) }.collectAsState(initial = emptyList())
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DetailHeader(playlist?.name ?: "Playlist", "${items.size} item${if (items.size == 1) "" else "s"}", onBack) {
                IconButton(onClick = { renaming = true }) { Icon(Lucide.Pencil, "Rename playlist") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Lucide.Trash2, "Delete playlist") }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 32.dp)) {
            if (items.isNotEmpty()) {
                item {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { vm.playPlaylist(context, playlistId) }) { Icon(Icons.Rounded.PlayArrow, null); Text(" Play") }
                        OutlinedButton(onClick = { vm.playPlaylist(context, playlistId, shuffle = true) }) { Icon(Lucide.Shuffle, null); Text(" Shuffle") }
                    }
                }
            } else {
                item { EmptyState("Empty playlist", "Long-press a video or song in your library and choose \"Add to playlist\".") }
            }
            itemsIndexed(items, key = { _, i -> i.id }) { index, entry ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MediaThumbnail(
                        entry.uri, entry.mediaKind,
                        Modifier
                            .width(if (entry.mediaKind == MediaKind.AUDIO) 52.dp else 92.dp)
                            .aspectRatio(if (entry.mediaKind == MediaKind.AUDIO) 1f else 16f / 9f)
                            .clip(RoundedCornerShape(12.dp)),
                        iconSize = 18.dp,
                    )
                    TextButton(onClick = { vm.playPlaylist(context, playlistId, startIndex = index) }, modifier = Modifier.weight(1f)) {
                        Text(entry.title, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                    }
                    IconButton(enabled = index > 0, onClick = {
                        val ids = items.map { it.id }.toMutableList()
                        ids.add(index - 1, ids.removeAt(index))
                        vm.reorderPlaylist(playlistId, ids)
                    }) { Icon(Lucide.ChevronUp, "Move up") }
                    IconButton(enabled = index < items.lastIndex, onClick = {
                        val ids = items.map { it.id }.toMutableList()
                        ids.add(index + 1, ids.removeAt(index))
                        vm.reorderPlaylist(playlistId, ids)
                    }) { Icon(Lucide.ChevronDown, "Move down") }
                    IconButton(onClick = { vm.removeFromPlaylist(entry.id) }) { Icon(Lucide.X, "Remove from playlist") }
                }
            }
        }
    }
    if (renaming) {
        var name by remember { mutableStateOf(playlist?.name ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename playlist") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.renamePlaylist(playlistId, name); renaming = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${playlist?.name}\"?") },
            text = { Text("The playlist is removed; your media files are not touched.") },
            confirmButton = { TextButton(onClick = { vm.deletePlaylist(playlistId); confirmDelete = false; onBack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SearchScreen(vm: LibraryViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.query = ""; onBack() }) { Icon(Lucide.ChevronLeft, "Back") }
                TextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    placeholder = { Text("Search titles, folders, artists") },
                    leadingIcon = { Icon(Lucide.Search, null) },
                    trailingIcon = { if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) { Icon(Lucide.X, "Clear search") } },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        ) {
            if (vm.query.isNotBlank() && results.isEmpty()) {
                item { EmptyState("No matches", "Nothing in your library matches \"${vm.query}\".") }
            }
            itemsIndexed(results, key = { _, r -> r.media.id }) { index, row ->
                MediaRow(
                    row,
                    onClick = {
                        val sameKind = results.filter { it.media.mediaKind == row.media.mediaKind }
                        vm.play(context, sameKind, sameKind.indexOf(row).coerceAtLeast(0))
                    },
                    onMore = {},
                )
            }
        }
    }
}
