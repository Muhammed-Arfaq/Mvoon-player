package app.mvoon.library.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.mvoon.core.media.MediaKind

object Sources {
    const val MEDIA_STORE = "mediastore"
    const val FOLDER = "folder"
}

/** One indexed file. `id` is its URI string, which is also the key for resume/favourites/playlists. */
@Entity(
    tableName = "media",
    indices = [Index("folderKey"), Index("kind"), Index("dateAdded"), Index("source")],
)
data class MediaEntity(
    @PrimaryKey val id: String,
    val uri: String,
    /** [MediaKind] name. Stored as text so the schema has no dependency on :core types. */
    val kind: String,
    val title: String,
    val fileName: String,
    val folderKey: String,
    val folderName: String,
    /** Human-readable location, e.g. "Internal storage › Movies". */
    val folderPath: String,
    /** MediaStore relative path ("Movies/Trailers/") or a folder-tree document path. */
    val relativePath: String?,
    val absolutePath: String?,
    val volume: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val dateAdded: Long,
    val dateModified: Long,
    val artist: String?,
    val album: String?,
    val source: String,
    /** The folder-tree URI this file was found under (folder sources only). */
    val sourceRoot: String?,
)

@Entity(tableName = "playback_state", indices = [Index("lastPlayedAt")])
data class PlaybackStateEntity(
    @PrimaryKey val mediaId: String,
    val uri: String,
    val title: String,
    /** [MediaKind] name. Stored as text so the schema has no dependency on :core types. */
    val kind: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayedAt: Long,
    val completed: Boolean,
    val playCount: Int,
    val isStream: Boolean,
    val audioTrackIndex: Int?,
    val subtitleTrackIndex: Int?,
    val audioDelayMs: Long,
    val subtitleDelayMs: Long,
    /** "media3" or "vlc": the engine that last played this file (sticky once it needed VLC). */
    @ColumnInfo(defaultValue = "media3") val engine: String = ENGINE_SYSTEM,
) {
    companion object {
        const val ENGINE_SYSTEM = "media3"
        const val ENGINE_COMPATIBILITY = "vlc"
    }
}

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val mediaId: String,
    val addedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_items",
    indices = [Index("playlistId"), Index("mediaId")],
    foreignKeys = [
        ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val mediaId: String,
    val uri: String,
    val title: String,
    /** [MediaKind] name. Stored as text so the schema has no dependency on :core types. */
    val kind: String,
    val position: Int,
)

/** A folder the user granted via the system folder picker (covers SD cards, USB, non-indexed files). */
@Entity(tableName = "folder_sources")
data class FolderSourceEntity(
    @PrimaryKey val treeUri: String,
    val displayName: String,
    val addedAt: Long,
    val lastScanAt: Long,
    val itemCount: Int,
)

/** A library row enriched with the user's state for that file. */
data class LibraryRow(
    @Embedded val media: MediaEntity,
    @ColumnInfo(name = "resumePositionMs") val resumePositionMs: Long?,
    @ColumnInfo(name = "playedDurationMs") val playedDurationMs: Long?,
    @ColumnInfo(name = "lastPlayedAt") val lastPlayedAt: Long?,
    @ColumnInfo(name = "completed") val completed: Boolean?,
    @ColumnInfo(name = "isFavorite") val isFavorite: Boolean,
)

data class FolderRow(
    val folderKey: String,
    val folderName: String,
    val folderPath: String,
    val videoCount: Int,
    val audioCount: Int,
    val totalBytes: Long,
    val latestAdded: Long,
    val coverUri: String?,
    val coverKind: String?,
)

data class PlaylistSummary(
    @Embedded val playlist: PlaylistEntity,
    val itemCount: Int,
    val coverUri: String?,
)

fun kindOf(name: String?): MediaKind = if (name == MediaKind.AUDIO.name) MediaKind.AUDIO else MediaKind.VIDEO

val MediaEntity.mediaKind: MediaKind get() = kindOf(kind)
val PlaybackStateEntity.mediaKind: MediaKind get() = kindOf(kind)
val PlaylistItemEntity.mediaKind: MediaKind get() = kindOf(kind)
