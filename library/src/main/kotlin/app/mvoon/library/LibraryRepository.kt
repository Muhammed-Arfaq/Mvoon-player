package app.mvoon.library

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import app.mvoon.core.media.ExternalSubtitle
import app.mvoon.core.media.MediaKind
import app.mvoon.core.media.PlayableItem
import app.mvoon.core.media.TrackMemory
import app.mvoon.library.db.FavoriteEntity
import app.mvoon.library.db.FolderSourceEntity
import app.mvoon.library.db.LibraryRow
import app.mvoon.library.db.MediaEntity
import app.mvoon.library.db.MvoonDatabase
import app.mvoon.library.db.PlaybackStateEntity
import app.mvoon.library.db.PlaylistEntity
import app.mvoon.library.db.PlaylistItemEntity
import app.mvoon.library.scan.FolderTreeScanner
import app.mvoon.library.scan.MediaFiles
import app.mvoon.library.scan.MediaLocation
import app.mvoon.library.scan.MediaStoreScanner
import app.mvoon.library.scan.SidecarSubtitleFinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Single entry point to the media library, playback history, favourites and playlists. */
class LibraryRepository(
    private val context: Context,
    private val db: MvoonDatabase,
    private val scope: CoroutineScope,
) {
    private val mediaStoreScanner = MediaStoreScanner(context)
    private val treeScanner = FolderTreeScanner(context, db.media())
    private val subtitleFinder = SidecarSubtitleFinder(context, treeScanner)
    private val scanMutex = Mutex()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val videos: Flow<List<LibraryRow>> = db.media().observeByKind(MediaKind.VIDEO.name)
    val audio: Flow<List<LibraryRow>> = db.media().observeByKind(MediaKind.AUDIO.name)
    val all: Flow<List<LibraryRow>> = db.media().observeAll()
    val folders = db.media().observeFolders()
    val folderSources = db.folderSources().observeAll()
    val playlists = db.playlists().observeSummaries()
    /** The video played most recently (the library's play button resumes it). */
    val lastVideo: Flow<PlaybackStateEntity?> = db.playbackState().observeLastPlayed(MediaKind.VIDEO.name)
    val recent: Flow<List<PlaybackStateEntity>> = db.playbackState().observeRecent(60)

    fun folderItems(folderKey: String) = db.media().observeFolder(folderKey)
    fun playlist(id: Long) = db.playlists().observe(id)
    fun playlistItems(id: Long) = db.playlists().observeItems(id)
    fun isFavorite(id: String) = db.favorites().observeIsFavorite(id)

    // ---------------------------------------------------------------- scanning

    /** Re-reads MediaStore and every granted folder. Safe to call often; runs one scan at a time. */
    suspend fun refresh(includeFolders: Boolean = true) = withContext(Dispatchers.IO) {
        scanMutex.withLock {
            _isScanning.value = true
            try {
                val items = runCatching { mediaStoreScanner.scan() }.getOrElse { emptyList() }
                db.media().replaceSource(app.mvoon.library.db.Sources.MEDIA_STORE, items)
                if (includeFolders) {
                    db.folderSources().all().forEach { source -> scanFolder(Uri.parse(source.treeUri), source.displayName) }
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    private var observer: ContentObserver? = null
    private var pendingRefresh: Job? = null

    /** Watches MediaStore so new downloads / deleted files show up without manual refresh. */
    fun startObservingMediaStore() {
        if (observer != null) return
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                pendingRefresh?.cancel()
                pendingRefresh = scope.launch {
                    delay(2_500) // coalesce bursts (e.g. a whole album being copied)
                    refresh(includeFolders = false)
                }
            }
        }.also {
            context.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, it)
            context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, it)
        }
    }

    suspend fun addFolder(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        // Write access too (the folder picker grants it), so files in the folder can be deleted from Mvoon.
        val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(treeUri, readWrite) }
            .onFailure { context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri).let { id ->
                val path = id.substringAfter(':', id)
                path.substringAfterLast('/').ifBlank { if (id.startsWith("primary")) "Internal storage" else "Storage" }
            }
        }.getOrDefault("Folder")
        scanMutex.withLock { scanFolder(treeUri, name) }
    }

    private suspend fun scanFolder(treeUri: Uri, name: String): Int {
        val stillGranted = context.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }
        if (!stillGranted) {
            db.media().deleteRoot(treeUri.toString())
            db.folderSources().delete(treeUri.toString())
            return 0
        }
        val items = runCatching { treeScanner.scan(treeUri, name) }.getOrElse { emptyList() }
        db.media().replaceSource(app.mvoon.library.db.Sources.FOLDER, items, root = treeUri.toString())
        db.folderSources().upsert(FolderSourceEntity(treeUri.toString(), name, now(), now(), items.size))
        return items.size
    }

    suspend fun removeFolder(treeUri: String) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(treeUri)
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            .onFailure { runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        db.media().deleteRoot(treeUri)
        db.folderSources().delete(treeUri)
    }

    // ---------------------------------------------------------------- playback history

    suspend fun playbackState(id: String): PlaybackStateEntity? = db.playbackState().get(id)

    fun observePlaybackState(id: String) = db.playbackState().observe(id)

    suspend fun media(id: String): MediaEntity? = db.media().get(id)

    suspend fun mediaList(ids: List<String>): List<MediaEntity> = db.media().getAll(ids)

    suspend fun savePlayback(
        item: PlayableItem,
        positionMs: Long,
        durationMs: Long,
        memory: TrackMemory?,
        completed: Boolean,
        countPlay: Boolean = false,
        usedCompatibilityEngine: Boolean = false,
    ) {
        val existing = db.playbackState().get(item.id)
        // Treat the last 3% (or 20 s) as finished so credits don't leave items "in progress".
        val nearEnd = durationMs > 0 && (durationMs - positionMs < maxOf(20_000L, durationMs * 3 / 100))
        val done = completed || nearEnd
        db.playbackState().upsert(
            PlaybackStateEntity(
                mediaId = item.id,
                uri = item.uri,
                title = item.title,
                kind = item.kind.name,
                positionMs = if (done) 0 else positionMs,
                durationMs = durationMs.takeIf { it > 0 } ?: existing?.durationMs ?: item.durationMs,
                lastPlayedAt = now(),
                completed = done,
                playCount = (existing?.playCount ?: 0) + if (countPlay) 1 else 0,
                isStream = item.isStream,
                audioTrackIndex = memory?.audioTrackIndex ?: existing?.audioTrackIndex,
                subtitleTrackIndex = memory?.subtitleTrackIndex ?: existing?.subtitleTrackIndex,
                audioDelayMs = memory?.audioDelayMs ?: existing?.audioDelayMs ?: 0,
                subtitleDelayMs = memory?.subtitleDelayMs ?: existing?.subtitleDelayMs ?: 0,
                engine = if (usedCompatibilityEngine) PlaybackStateEntity.ENGINE_COMPATIBILITY else existing?.engine ?: PlaybackStateEntity.ENGINE_SYSTEM,
            ),
        )
    }

    suspend fun setWatched(id: String, watched: Boolean) {
        if (db.playbackState().get(id) != null) db.playbackState().setCompleted(id, watched)
    }

    suspend fun forget(id: String) = db.playbackState().delete(id)

    /** The file was deleted from the device: drop it from the library, history, favourites and playlists. */
    suspend fun removeDeleted(id: String) = withContext(Dispatchers.IO) {
        db.media().delete(listOf(id))
        db.playbackState().delete(id)
        db.favorites().remove(id)
        db.playlists().deleteItemsFor(id)
    }

    suspend fun clearHistory() = db.playbackState().clear()

    // ---------------------------------------------------------------- favourites & playlists

    suspend fun setFavorite(id: String, favorite: Boolean) {
        if (favorite) db.favorites().add(FavoriteEntity(id, now())) else db.favorites().remove(id)
    }

    suspend fun createPlaylist(name: String, items: List<PlayableItem> = emptyList()): Long {
        val id = db.playlists().insert(PlaylistEntity(name = name.trim().ifBlank { "Playlist" }, createdAt = now(), updatedAt = now()))
        if (items.isNotEmpty()) addToPlaylist(id, items)
        return id
    }

    suspend fun addToPlaylist(playlistId: Long, items: List<PlayableItem>) {
        val start = db.playlists().maxPosition(playlistId) + 1
        db.playlists().insertItems(
            items.mapIndexed { i, item ->
                PlaylistItemEntity(playlistId = playlistId, mediaId = item.id, uri = item.uri, title = item.title, kind = item.kind.name, position = start + i)
            },
        )
        db.playlists().touch(playlistId, now())
    }

    suspend fun removeFromPlaylist(itemId: Long) = db.playlists().deleteItem(itemId)

    suspend fun renamePlaylist(id: Long, name: String) = db.playlists().rename(id, name, now())

    suspend fun deletePlaylist(id: Long) = db.playlists().delete(id)

    suspend fun reorderPlaylist(id: Long, orderedItemIds: List<Long>) = db.playlists().reorder(id, orderedItemIds, now())

    suspend fun playlistSnapshot(id: Long): List<PlaylistItemEntity> = db.playlists().items(id)

    // ---------------------------------------------------------------- subtitles & locations

    /** Sidecar subtitles for any URI (library item, file opened from another app, SAF document). */
    suspend fun findSubtitles(uri: String): List<ExternalSubtitle> = withContext(Dispatchers.IO) {
        val location = locationFor(uri) ?: return@withContext emptyList()
        runCatching { subtitleFinder.find(location) }.getOrDefault(emptyList())
    }

    suspend fun locationFor(uri: String): MediaLocation? = withContext(Dispatchers.IO) {
        db.media().get(uri)?.let { m ->
            return@withContext MediaLocation(m.uri, m.fileName, m.title, m.relativePath, m.absolutePath, m.volume, m.sourceRoot)
        }
        val parsed = Uri.parse(uri)
        when (parsed.scheme) {
            ContentResolver.SCHEME_FILE -> parsed.path?.let { path ->
                val name = path.substringAfterLast('/')
                MediaLocation(uri, name, MediaFiles.titleFromFileName(name), absolutePath = path)
            }
            ContentResolver.SCHEME_CONTENT -> resolveContent(parsed)
            else -> null
        }
    }

    private fun resolveContent(uri: Uri): MediaLocation? = runCatching {
        if (uri.authority == MediaStore.AUTHORITY) {
            val projection = mutableListOf(MediaStore.MediaColumns.DISPLAY_NAME, @Suppress("DEPRECATION") MediaStore.MediaColumns.DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projection += MediaStore.MediaColumns.RELATIVE_PATH
                projection += MediaStore.MediaColumns.VOLUME_NAME
            }
            context.contentResolver.query(uri, projection.toTypedArray(), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0) ?: return@use null
                    return@runCatching MediaLocation(
                        uri = uri.toString(), fileName = name, title = MediaFiles.titleFromFileName(name),
                        absolutePath = c.getString(1),
                        relativePath = if (projection.size > 2) c.getString(2) else null,
                        volume = if (projection.size > 3) c.getString(3) else null,
                    )
                }
                null
            }
        }
        val name = displayName(uri) ?: return@runCatching null
        if (DocumentsContract.isDocumentUri(context, uri) && uri.authority == "com.android.externalstorage.documents") {
            val docId = DocumentsContract.getDocumentId(uri)
            val volumePart = docId.substringBefore(':')
            val path = docId.substringAfter(':', "")
            val relative = path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
            val isTree = runCatching { DocumentsContract.getTreeDocumentId(uri) }.isSuccess
            return@runCatching MediaLocation(
                uri = uri.toString(), fileName = name, title = MediaFiles.titleFromFileName(name),
                relativePath = relative,
                volume = if (volumePart == "primary") "external_primary" else volumePart.lowercase(),
                sourceRoot = if (isTree) DocumentsContract.buildTreeDocumentUri(uri.authority, DocumentsContract.getTreeDocumentId(uri)).toString() else null,
            )
        }
        MediaLocation(uri.toString(), name, MediaFiles.titleFromFileName(name))
    }.getOrNull()

    fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun now() = System.currentTimeMillis()
}
