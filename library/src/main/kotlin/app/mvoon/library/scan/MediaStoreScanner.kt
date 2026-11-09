package app.mvoon.library.scan

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import app.mvoon.core.media.MediaKind
import app.mvoon.library.db.MediaEntity
import app.mvoon.library.db.Sources
import java.io.File

/**
 * Reads the system media index. MediaStore already indexes internal storage and SD cards, so this
 * is fast (a single query per type) and battery-friendly — no file-system crawling.
 */
class MediaStoreScanner(private val context: Context) {

    fun scan(): List<MediaEntity> = scanVideos() + scanAudio()

    private fun scanVideos(): List<MediaEntity> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = baseProjection() + arrayOf(MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT)
        return query(collection, projection, null) { c, cols -> row(c, cols, MediaKind.VIDEO, MediaStore.Video.Media.EXTERNAL_CONTENT_URI) }
    }

    private fun scanAudio(): List<MediaEntity> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = baseProjection() + arrayOf(MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM)
        // Skip system sounds; keep music, podcasts, audiobooks and plain audio files.
        val selection = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND ${MediaStore.Audio.Media.IS_ALARM} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0"
        return query(collection, projection, selection) { c, cols -> row(c, cols, MediaKind.AUDIO, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) }
    }

    private fun baseProjection(): Array<String> {
        val list = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.TITLE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.Video.VideoColumns.BUCKET_DISPLAY_NAME,
            @Suppress("DEPRECATION") MediaStore.MediaColumns.DATA,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            list += MediaStore.MediaColumns.RELATIVE_PATH
            list += MediaStore.MediaColumns.VOLUME_NAME
        }
        return list.toTypedArray()
    }

    private class Columns(c: Cursor) {
        val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val name = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val title = c.getColumnIndex(MediaStore.MediaColumns.TITLE)
        val size = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
        val mime = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val added = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
        val modified = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
        val duration = c.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
        val bucket = c.getColumnIndex(MediaStore.Video.VideoColumns.BUCKET_DISPLAY_NAME)

        @Suppress("DEPRECATION")
        val data = c.getColumnIndex(MediaStore.MediaColumns.DATA)
        val relative = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1
        val volume = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) c.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME) else -1
        val width = c.getColumnIndex(MediaStore.Video.Media.WIDTH)
        val height = c.getColumnIndex(MediaStore.Video.Media.HEIGHT)
        val artist = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
        val album = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
    }

    private inline fun query(
        collection: Uri,
        projection: Array<String>,
        selection: String?,
        map: (Cursor, Columns) -> MediaEntity?,
    ): List<MediaEntity> {
        val result = ArrayList<MediaEntity>()
        context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
            val cols = Columns(cursor)
            while (cursor.moveToNext()) map(cursor, cols)?.let(result::add)
        }
        return result
    }

    private fun row(c: Cursor, cols: Columns, kind: MediaKind, base: Uri): MediaEntity? {
        val id = c.getLong(cols.id)
        val fileName = c.str(cols.name) ?: return null
        val uri = ContentUris.withAppendedId(base, id).toString()
        val data = c.str(cols.data)
        val volume = c.str(cols.volume)
        val relative = c.str(cols.relative) ?: data?.let { relativeFromData(it) }
        val folderName = relative?.trimEnd('/')?.substringAfterLast('/')?.ifBlank { null }
            ?: c.str(cols.bucket) ?: data?.let { File(it).parentFile?.name } ?: "Storage"
        val folderKey = "ms:${volume ?: "external"}:${relative ?: data?.let { File(it).parent } ?: folderName}"
        val folderPath = buildString {
            append(MediaFiles.volumeLabel(volume))
            relative?.trimEnd('/')?.takeIf { it.isNotBlank() }?.let { append(" › ").append(it.replace("/", " › ")) }
        }
        val rawTitle = c.str(cols.title)
        val title = when {
            kind == MediaKind.AUDIO && !rawTitle.isNullOrBlank() -> rawTitle
            else -> MediaFiles.titleFromFileName(fileName)
        }
        return MediaEntity(
            id = uri,
            uri = uri,
            kind = kind.name,
            title = title,
            fileName = fileName,
            folderKey = folderKey,
            folderName = folderName,
            folderPath = folderPath,
            relativePath = relative,
            absolutePath = data,
            volume = volume,
            mimeType = c.str(cols.mime),
            sizeBytes = c.long(cols.size),
            durationMs = c.long(cols.duration),
            width = c.long(cols.width).toInt(),
            height = c.long(cols.height).toInt(),
            dateAdded = c.long(cols.added) * 1000,
            dateModified = c.long(cols.modified) * 1000,
            artist = c.str(cols.artist)?.takeUnless { it == "<unknown>" },
            album = c.str(cols.album),
            source = Sources.MEDIA_STORE,
            sourceRoot = null,
        )
    }

    /** "/storage/emulated/0/Movies/a.mkv" → "Movies/" (pre-Android 10 devices). */
    private fun relativeFromData(path: String): String? {
        val match = Regex("^/storage/(?:emulated/\\d+|[^/]+)/(.*/)?[^/]+$").find(path) ?: return null
        return match.groupValues[1].ifEmpty { null }
    }

    private fun Cursor.str(index: Int): String? = if (index >= 0 && !isNull(index)) getString(index) else null
    private fun Cursor.long(index: Int): Long = if (index >= 0 && !isNull(index)) getLong(index) else 0L
}
