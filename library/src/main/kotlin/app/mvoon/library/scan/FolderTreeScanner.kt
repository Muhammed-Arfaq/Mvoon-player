package app.mvoon.library.scan

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.mvoon.library.db.MediaDao
import app.mvoon.library.db.MediaEntity
import app.mvoon.library.db.Sources

/**
 * Indexes a folder the user granted through the system folder picker (Storage Access Framework).
 * Uses raw DocumentsContract queries (one per directory) — much faster than DocumentFile.
 */
class FolderTreeScanner(private val context: Context, private val mediaDao: MediaDao) {

    data class Child(val documentId: String, val name: String, val mimeType: String?, val size: Long, val modified: Long)

    suspend fun scan(treeUri: Uri, displayName: String): List<MediaEntity> {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val result = ArrayList<MediaEntity>()
        val queue = ArrayDeque<Pair<String, String>>() // documentId to path relative to the tree root
        queue += rootId to ""
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_DIRECTORIES) {
            val (dirId, relPath) = queue.removeFirst()
            visited++
            val children = listChildren(treeUri, dirId)
            if (children.any { it.name == ".nomedia" }) continue
            for (child in children) {
                if (child.name.startsWith(".")) continue
                if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    queue += child.documentId to "$relPath${child.name}/"
                    continue
                }
                val kind = MediaFiles.kindFor(child.name, child.mimeType) ?: continue
                if (isAlreadyIndexedByMediaStore(child.documentId)) continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.documentId).toString()
                val folderName = relPath.trimEnd('/').substringAfterLast('/').ifEmpty { displayName }
                result += MediaEntity(
                    id = uri,
                    uri = uri,
                    kind = kind.name,
                    title = MediaFiles.titleFromFileName(child.name),
                    fileName = child.name,
                    folderKey = "tree:$treeUri:$relPath",
                    folderName = folderName,
                    folderPath = listOf(displayName, relPath.trimEnd('/')).filter { it.isNotBlank() }.joinToString(" › ").replace("/", " › "),
                    relativePath = relPath,
                    absolutePath = null,
                    volume = null,
                    mimeType = child.mimeType,
                    sizeBytes = child.size,
                    durationMs = 0, // unknown until first played (reading every file's header would be slow)
                    width = 0,
                    height = 0,
                    dateAdded = child.modified,
                    dateModified = child.modified,
                    artist = null,
                    album = null,
                    source = Sources.FOLDER,
                    sourceRoot = treeUri.toString(),
                )
            }
        }
        return result
    }

    fun listChildren(treeUri: Uri, parentDocumentId: String): List<Child> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val out = ArrayList<Child>()
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    out += Child(
                        documentId = c.getString(0),
                        name = c.getString(1) ?: continue,
                        mimeType = c.getString(2),
                        size = if (c.isNull(3)) 0 else c.getLong(3),
                        modified = if (c.isNull(4)) 0 else c.getLong(4),
                    )
                }
            }
        }
        return out
    }

    /**
     * External-storage document ids look like "primary:Movies/a.mkv" or "1A2B-3C4D:Films/b.mkv". When
     * MediaStore already indexes the same file, skip it to avoid duplicates in the library.
     */
    private suspend fun isAlreadyIndexedByMediaStore(documentId: String): Boolean {
        val volumePart = documentId.substringBefore(':', "")
        val path = documentId.substringAfter(':', "")
        if (volumePart.isEmpty() || path.isEmpty()) return false
        val volume = if (volumePart == "primary") "external_primary" else volumePart.lowercase()
        val relative = path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val name = path.substringAfterLast('/')
        return mediaDao.mediaStoreCopyExists(volume, relative, name) > 0
    }

    private companion object {
        const val MAX_DIRECTORIES = 5_000
    }
}
