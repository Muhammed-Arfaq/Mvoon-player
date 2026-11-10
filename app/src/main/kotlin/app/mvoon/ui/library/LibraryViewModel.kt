package app.mvoon.ui.library

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mvoon.appContainer
import app.mvoon.core.media.MediaKind
import app.mvoon.core.media.PlayableItem
import app.mvoon.core.media.StreamType
import app.mvoon.core.settings.LibraryLayout
import app.mvoon.core.settings.SortField
import app.mvoon.core.settings.SortOrder
import app.mvoon.library.db.FolderRow
import app.mvoon.library.db.LibraryRow
import app.mvoon.library.db.PlaybackStateEntity
import app.mvoon.library.db.mediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryTab(val label: String) { HOME("Home"), VIDEOS("Videos"), MUSIC("Music"), FOLDERS("Folders"), PLAYLISTS("Playlists") }

enum class LibraryFilter(val label: String) { ALL("All"), FAVORITES("Favourites"), UNWATCHED("Unwatched"), IN_PROGRESS("In progress"), RECENT("Recent") }

enum class AccessLevel { NONE, PARTIAL, FULL }

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app.appContainer
    val repo = container.library
    private val settings = container.settings
    val coordinator = container.playback
    val engine = container.engine

    val preferences = coordinator.preferences
    val playerState = engine.state
    val isScanning = repo.isScanning

    var tab by mutableStateOf(LibraryTab.HOME)
    var videoFilter by mutableStateOf(LibraryFilter.ALL)
    var musicFilter by mutableStateOf(LibraryFilter.ALL)
    var query by mutableStateOf("")
    var access by mutableStateOf(AccessLevel.NONE)

    val videos: StateFlow<List<LibraryRow>> = combine(repo.videos, preferences, snapshotFlow { videoFilter }) { rows, prefs, filter ->
        rows.visible(prefs.library.hiddenFolders).filtered(filter).sorted(prefs.library.videoSort, filter)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val music: StateFlow<List<LibraryRow>> = combine(repo.audio, preferences, snapshotFlow { musicFilter }) { rows, prefs, filter ->
        rows.visible(prefs.library.hiddenFolders).filtered(filter).sorted(prefs.library.audioSort, filter)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<FolderRow>> = combine(repo.folders, preferences) { rows, prefs ->
        rows.filterNot { it.folderKey in prefs.library.hiddenFolders }.sortedBy { it.folderName.lowercase() }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** False until the library database has been read once — screens show skeletons meanwhile. */
    val loaded: StateFlow<Boolean> = repo.all.map { true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Home dashboard numbers and shelves; null while loading. */
    val dashboard: StateFlow<Dashboard?> = combine(repo.videos, repo.audio, preferences) { v, a, prefs ->
        val videos = v.visible(prefs.library.hiddenFolders)
        val songs = a.visible(prefs.library.hiddenFolders)
        val played = songs.filter { it.lastPlayedAt != null }
        Dashboard(
            videos = videos.size,
            watchedVideos = videos.count { it.lastPlayedAt != null },
            inProgressVideos = videos.count { (it.resumePositionMs ?: 0) > 5_000 && it.completed != true },
            videoMs = videos.sumOf { it.durationMs },
            songs = songs.size,
            playedSongs = played.size,
            favouriteSongs = songs.count { it.isFavorite },
            musicMs = songs.sumOf { it.durationMs },
            recentlyAdded = videos.sortedByDescending { it.media.dateAdded }.take(5),
            recentlyWatched = videos.filter { it.lastPlayedAt != null }.sortedByDescending { it.lastPlayedAt }.take(10),
            recentMusic = played.sortedByDescending { it.lastPlayedAt }.take(12),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val playlists = repo.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val folderSources = repo.folderSources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The video played most recently, for the round play button (null when nothing was watched yet). */
    val lastVideo: StateFlow<PlaybackStateEntity?> = repo.lastVideo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val searchResults: StateFlow<List<LibraryRow>> = combine(repo.all, snapshotFlow { query }.debounce(120)) { rows, q ->
        val needle = q.trim().lowercase()
        if (needle.isEmpty()) {
            emptyList()
        } else {
            rows.asSequence().filter { r ->
                val m = r.media
                m.title.lowercase().contains(needle) || m.fileName.lowercase().contains(needle) ||
                    m.folderName.lowercase().contains(needle) || m.artist?.lowercase()?.contains(needle) == true ||
                    m.album?.lowercase()?.contains(needle) == true
            }.sortedWith(compareBy({ !it.media.title.lowercase().startsWith(needle) }, { it.media.title.lowercase() })).take(300).toList()
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun List<LibraryRow>.visible(hidden: Set<String>) = if (hidden.isEmpty()) this else filterNot { it.media.folderKey in hidden }

    private fun List<LibraryRow>.filtered(filter: LibraryFilter): List<LibraryRow> = when (filter) {
        LibraryFilter.ALL -> this
        LibraryFilter.FAVORITES -> filter { it.isFavorite }
        LibraryFilter.UNWATCHED -> filter { it.lastPlayedAt == null }
        LibraryFilter.IN_PROGRESS -> filter { (it.resumePositionMs ?: 0) > 5_000 && it.completed != true }
        LibraryFilter.RECENT -> filter { it.lastPlayedAt != null }
    }

    private fun List<LibraryRow>.sorted(order: SortOrder, filter: LibraryFilter): List<LibraryRow> {
        if (filter == LibraryFilter.RECENT) return sortedByDescending { it.lastPlayedAt ?: 0 }
        val comparator: Comparator<LibraryRow> = when (order.field) {
            SortField.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.media.title }
            SortField.DATE_ADDED -> compareBy { it.media.dateAdded }
            SortField.SIZE -> compareBy { it.media.sizeBytes }
            SortField.DURATION -> compareBy { maxOf(it.media.durationMs, it.playedDurationMs ?: 0) }
            SortField.LAST_PLAYED -> compareBy { it.lastPlayedAt ?: 0 }
        }
        return sortedWith(if (order.descending) comparator.reversed() else comparator)
    }

    // ---------------------------------------------------------------- actions

    fun refresh() = viewModelScope.launch { repo.refresh() }

    private var firstAccessCheck = true

    fun onAccessChanged(level: AccessLevel) {
        val changed = level != access
        access = level
        if (level != AccessLevel.NONE) repo.startObservingMediaStore()
        // Always scan once per launch: granted folders work even without media permission.
        if (changed || firstAccessCheck) refresh()
        firstAccessCheck = false
    }

    fun addFolder(uri: Uri, onDone: (Int) -> Unit) = viewModelScope.launch {
        val count = runCatching { repo.addFolder(uri) }.getOrDefault(0)
        onDone(count)
    }

    fun removeFolder(treeUri: String) = viewModelScope.launch { repo.removeFolder(treeUri) }

    fun play(context: Context, rows: List<LibraryRow>, index: Int, shuffle: Boolean = false) =
        coordinator.playRows(context, rows, index, shuffle)

    fun playUrl(context: Context, url: String, type: StreamType, title: String?) = coordinator.playUrl(context, url, type, title)

    fun playUris(context: Context, uris: List<Uri>) = coordinator.playUris(context, uris)

    /**
     * The round play button (as in MX Player): continue the last video where it was left. A library
     * video brings the rest of its folder along, so the next episode plays on.
     */
    fun playLastVideo(context: Context) = viewModelScope.launch {
        val entry = lastVideo.value ?: return@launch
        val all = repo.videos.first()
        val row = all.firstOrNull { it.media.id == entry.mediaId }
        if (row != null) {
            val folder = all.filter { it.media.folderKey == row.media.folderKey }.sortedWith { a, b -> naturalCompare(a.media.title, b.media.title) }
            coordinator.playRows(context, folder, folder.indexOf(row).coerceAtLeast(0), resume = true)
        } else {
            val item = PlayableItem(
                id = entry.mediaId, uri = entry.uri, title = entry.title, kind = entry.mediaKind,
                isStream = entry.isStream, durationMs = entry.durationMs,
            )
            coordinator.playItems(context, listOf(item), 0, resume = true)
        }
    }

    /** The file was deleted from the device: take it out of what's playing and forget it everywhere. */
    fun onDeleted(file: DeletableFile) {
        coordinator.onFileDeleted(file.id)
    }

    fun setVideoSort(order: SortOrder) = updateLibrary { it.copy(videoSort = order) }
    fun setAudioSort(order: SortOrder) = updateLibrary { it.copy(audioSort = order) }
    fun toggleLayout() = updateLibrary { it.copy(layout = if (it.layout == LibraryLayout.GRID) LibraryLayout.LIST else LibraryLayout.GRID) }
    fun hideFolder(key: String) = updateLibrary { it.copy(hiddenFolders = it.hiddenFolders + key) }
    fun unhideFolder(key: String) = updateLibrary { it.copy(hiddenFolders = it.hiddenFolders - key) }
    fun finishOnboarding() = updateLibrary { it.copy(onboardingDone = true) }

    private fun updateLibrary(transform: (app.mvoon.core.settings.LibraryPreferences) -> app.mvoon.core.settings.LibraryPreferences) =
        viewModelScope.launch { settings.update { it.copy(library = transform(it.library)) } }

    fun setFavorite(id: String, favorite: Boolean) = viewModelScope.launch { repo.setFavorite(id, favorite) }
    fun setWatched(id: String, watched: Boolean) = viewModelScope.launch { repo.setWatched(id, watched) }
    fun forget(id: String) = viewModelScope.launch { repo.forget(id) }

    fun createPlaylist(name: String, items: List<PlayableItem>) = viewModelScope.launch { repo.createPlaylist(name, items) }
    fun addToPlaylist(id: Long, items: List<PlayableItem>) = viewModelScope.launch { repo.addToPlaylist(id, items) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { repo.deletePlaylist(id) }
    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch { repo.renamePlaylist(id, name) }
    fun removeFromPlaylist(itemId: Long) = viewModelScope.launch { repo.removeFromPlaylist(itemId) }
    fun reorderPlaylist(id: Long, ids: List<Long>) = viewModelScope.launch { repo.reorderPlaylist(id, ids) }

    fun playPlaylist(context: Context, id: Long, shuffle: Boolean = false, startIndex: Int = 0) = viewModelScope.launch {
        val items = repo.playlistSnapshot(id)
        if (items.isEmpty()) return@launch
        val known = repo.mediaList(items.map { it.mediaId }).associateBy { it.id }
        coordinator.playItems(
            context,
            items.map { entry ->
                val m = known[entry.mediaId]
                PlayableItem(
                    id = entry.mediaId, uri = entry.uri, title = m?.title ?: entry.title, kind = entry.mediaKind,
                    subtitle = m?.artist ?: m?.folderName, durationMs = m?.durationMs ?: 0, sizeBytes = m?.sizeBytes ?: 0,
                    isStream = entry.uri.startsWith("http", true) || entry.uri.startsWith("rtsp", true),
                )
            },
            startIndex, shuffle,
        )
    }

    fun clearHistory() = viewModelScope.launch { repo.clearHistory() }
}

fun LibraryRow.toPlayable(): PlayableItem = PlayableItem(
    id = media.id, uri = media.uri, title = media.title,
    subtitle = if (media.mediaKind == MediaKind.AUDIO) media.artist else media.folderName,
    kind = media.mediaKind, mimeType = media.mimeType, durationMs = media.durationMs, sizeBytes = media.sizeBytes,
)

/** Fraction watched, or null when there's nothing to show. */
val LibraryRow.progress: Float?
    get() {
        val pos = resumePositionMs ?: return null
        val duration = maxOf(media.durationMs, playedDurationMs ?: 0)
        if (completed == true || pos <= 0 || duration <= 0) return null
        return (pos.toFloat() / duration).coerceIn(0f, 1f)
    }

val LibraryRow.durationMs: Long get() = maxOf(media.durationMs, playedDurationMs ?: 0)

fun LibraryRow.isNew(now: Long = System.currentTimeMillis()): Boolean =
    lastPlayedAt == null && now - media.dateAdded < 3L * 24 * 60 * 60 * 1000

data class Dashboard(
    val videos: Int,
    val watchedVideos: Int,
    val inProgressVideos: Int,
    val videoMs: Long,
    val songs: Int,
    val playedSongs: Int,
    val favouriteSongs: Int,
    val musicMs: Long,
    val recentlyAdded: List<LibraryRow>,
    /** Videos with playback history, newest first (the hero's "Watching" shelf). */
    val recentlyWatched: List<LibraryRow>,
    /** Songs with playback history, newest first (the hero's "Listening" shelf). */
    val recentMusic: List<LibraryRow>,
)
