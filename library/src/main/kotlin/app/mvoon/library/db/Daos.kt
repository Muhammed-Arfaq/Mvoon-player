package app.mvoon.library.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query(
        """
        SELECT m.*, p.positionMs AS resumePositionMs, p.durationMs AS playedDurationMs, p.lastPlayedAt AS lastPlayedAt,
               p.completed AS completed, (f.mediaId IS NOT NULL) AS isFavorite
        FROM media m
        LEFT JOIN playback_state p ON p.mediaId = m.id
        LEFT JOIN favorites f ON f.mediaId = m.id
        WHERE m.kind = :kind
        """,
    )
    fun observeByKind(kind: String): Flow<List<LibraryRow>>

    @Query(
        """
        SELECT m.*, p.positionMs AS resumePositionMs, p.durationMs AS playedDurationMs, p.lastPlayedAt AS lastPlayedAt,
               p.completed AS completed, (f.mediaId IS NOT NULL) AS isFavorite
        FROM media m
        LEFT JOIN playback_state p ON p.mediaId = m.id
        LEFT JOIN favorites f ON f.mediaId = m.id
        WHERE m.folderKey = :folderKey
        """,
    )
    fun observeFolder(folderKey: String): Flow<List<LibraryRow>>

    @Query(
        """
        SELECT m.*, p.positionMs AS resumePositionMs, p.durationMs AS playedDurationMs, p.lastPlayedAt AS lastPlayedAt,
               p.completed AS completed, (f.mediaId IS NOT NULL) AS isFavorite
        FROM media m
        LEFT JOIN playback_state p ON p.mediaId = m.id
        LEFT JOIN favorites f ON f.mediaId = m.id
        """,
    )
    fun observeAll(): Flow<List<LibraryRow>>

    @Query(
        """
        SELECT folderKey, folderName, folderPath,
               SUM(CASE WHEN kind = 'VIDEO' THEN 1 ELSE 0 END) AS videoCount,
               SUM(CASE WHEN kind = 'AUDIO' THEN 1 ELSE 0 END) AS audioCount,
               SUM(sizeBytes) AS totalBytes,
               MAX(dateAdded) AS latestAdded,
               uri AS coverUri,
               kind AS coverKind
        FROM media
        GROUP BY folderKey
        """,
    )
    fun observeFolders(): Flow<List<FolderRow>>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun get(id: String): MediaEntity?

    @Query("SELECT * FROM media WHERE id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<MediaEntity>

    @Query("SELECT id FROM media WHERE source = :source")
    suspend fun idsForSource(source: String): List<String>

    @Query("SELECT id FROM media WHERE sourceRoot = :root")
    suspend fun idsForRoot(root: String): List<String>

    @Query("SELECT COUNT(*) FROM media WHERE source = 'mediastore' AND volume = :volume AND relativePath = :relativePath AND fileName = :fileName")
    suspend fun mediaStoreCopyExists(volume: String, relativePath: String, fileName: String): Int

    @Upsert
    suspend fun upsert(items: List<MediaEntity>)

    @Query("DELETE FROM media WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("DELETE FROM media WHERE sourceRoot = :root")
    suspend fun deleteRoot(root: String)

    @Transaction
    suspend fun replaceSource(source: String, items: List<MediaEntity>, root: String? = null) {
        val existing = if (root != null) idsForRoot(root) else idsForSource(source)
        val keep = items.mapTo(HashSet()) { it.id }
        existing.filterNot(keep::contains).chunked(500).forEach { delete(it) }
        items.chunked(500).forEach { upsert(it) }
    }
}

@Dao
interface PlaybackStateDao {
    @Query("SELECT * FROM playback_state WHERE mediaId = :id")
    suspend fun get(id: String): PlaybackStateEntity?

    @Query("SELECT * FROM playback_state WHERE mediaId = :id")
    fun observe(id: String): Flow<PlaybackStateEntity?>

    @Upsert
    suspend fun upsert(state: PlaybackStateEntity)

    @Query("SELECT * FROM playback_state ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlaybackStateEntity>>

    @Query("SELECT * FROM playback_state WHERE kind = :kind ORDER BY lastPlayedAt DESC LIMIT 1")
    fun observeLastPlayed(kind: String): Flow<PlaybackStateEntity?>

    @Query("UPDATE playback_state SET completed = :completed, positionMs = 0 WHERE mediaId = :id")
    suspend fun setCompleted(id: String, completed: Boolean)

    @Query("DELETE FROM playback_state WHERE mediaId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM playback_state")
    suspend fun clear()
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE mediaId = :id")
    suspend fun remove(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaId = :id)")
    fun observeIsFavorite(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaId = :id)")
    suspend fun isFavorite(id: String): Boolean
}

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.*, COUNT(i.id) AS itemCount,
               (SELECT uri FROM playlist_items WHERE playlistId = p.id ORDER BY position LIMIT 1) AS coverUri
        FROM playlists p LEFT JOIN playlist_items i ON i.playlistId = p.id
        GROUP BY p.id ORDER BY p.updatedAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observe(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY position")
    fun observeItems(id: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY position")
    suspend fun items(id: Long): List<PlaylistItemEntity>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)

    @Query("DELETE FROM playlist_items WHERE mediaId = :mediaId")
    suspend fun deleteItemsFor(mediaId: String)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :id")
    suspend fun maxPosition(id: Long): Int

    @Query("UPDATE playlist_items SET position = :position WHERE id = :itemId")
    suspend fun setPosition(itemId: Long, position: Int)

    @Transaction
    suspend fun reorder(playlistId: Long, orderedItemIds: List<Long>, now: Long) {
        orderedItemIds.forEachIndexed { index, itemId -> setPosition(itemId, index) }
        touch(playlistId, now)
    }
}

@Dao
interface FolderSourceDao {
    @Query("SELECT * FROM folder_sources ORDER BY displayName")
    fun observeAll(): Flow<List<FolderSourceEntity>>

    @Query("SELECT * FROM folder_sources")
    suspend fun all(): List<FolderSourceEntity>

    @Upsert
    suspend fun upsert(source: FolderSourceEntity)

    @Query("DELETE FROM folder_sources WHERE treeUri = :uri")
    suspend fun delete(uri: String)
}
