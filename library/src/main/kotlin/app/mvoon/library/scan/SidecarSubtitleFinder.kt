package app.mvoon.library.scan

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import app.mvoon.core.media.ExternalSubtitle
import app.mvoon.subtitles.ExternalSubtitleMatcher
import app.mvoon.subtitles.SubtitleCandidate
import app.mvoon.subtitles.SubtitleFormats
import java.io.File

/**
 * Finds subtitle files next to a video. Android limits which non-media files an app may see, so
 * three strategies are tried in order of reliability:
 *  1. the folder-tree grant (files indexed via "Add folder") — sees everything in the folder;
 *  2. MediaStore's Files table — subtitle files are indexed for apps that can read videos;
 *  3. direct file listing — works on older Android versions.
 * Files opened from other apps (content:// without folder access) cannot be searched; the user can
 * attach a subtitle manually from the player.
 */
/** Where a media file lives, as far as we can tell. Built from a library row or from an opened URI. */
data class MediaLocation(
    val uri: String,
    val fileName: String,
    val title: String,
    val relativePath: String? = null,
    val absolutePath: String? = null,
    val volume: String? = null,
    val sourceRoot: String? = null,
)

class SidecarSubtitleFinder(private val context: Context, private val treeScanner: FolderTreeScanner) {

    fun find(media: MediaLocation): List<ExternalSubtitle> {
        val candidates = LinkedHashMap<String, SubtitleCandidate>()
        fun add(list: List<SubtitleCandidate>) = list.forEach { candidates.putIfAbsent(it.fileName.lowercase() + it.inSubtitleFolder, it) }

        media.sourceRoot?.let { root -> add(fromTree(Uri.parse(root), media)) }
        if (candidates.isEmpty() && media.relativePath != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(fromMediaStore(media))
        }
        if (candidates.isEmpty()) media.absolutePath?.let { add(fromFiles(File(it))) }

        return ExternalSubtitleMatcher.match(media.fileName, candidates.values.toList())
    }

    private fun fromTree(treeUri: Uri, media: MediaLocation): List<SubtitleCandidate> = runCatching {
        val documentId = DocumentsContract.getDocumentId(Uri.parse(media.uri))
        val parentId = documentId.substringBeforeLast('/', "").ifEmpty { DocumentsContract.getTreeDocumentId(treeUri) }
        val siblings = treeScanner.listChildren(treeUri, parentId)
        val result = siblings.filter { SubtitleFormats.isSupported(it.name) }.map {
            SubtitleCandidate(it.name, DocumentsContract.buildDocumentUriUsingTree(treeUri, it.documentId).toString())
        }.toMutableList()
        siblings.filter { it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR && ExternalSubtitleMatcher.isSubtitleFolderName(it.name) }
            .forEach { folder ->
                val inside = treeScanner.listChildren(treeUri, folder.documentId)
                // "Subs/<video name>/2_English.srt" layouts: descend one level into a folder named after the video.
                val nested = inside.firstOrNull { it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR && it.name.equals(media.title, ignoreCase = true) }
                val files = if (nested != null) treeScanner.listChildren(treeUri, nested.documentId) else inside
                files.filter { SubtitleFormats.isSupported(it.name) }.forEach {
                    result += SubtitleCandidate(it.name, DocumentsContract.buildDocumentUriUsingTree(treeUri, it.documentId).toString(), inSubtitleFolder = true)
                }
            }
        result
    }.getOrDefault(emptyList())

    private fun fromMediaStore(media: MediaLocation): List<SubtitleCandidate> = runCatching {
        val relative = media.relativePath ?: return emptyList()
        val volume = media.volume ?: MediaStore.VOLUME_EXTERNAL
        val files = MediaStore.Files.getContentUri(volume)
        val base = media.fileName.substringBeforeLast('.')
        val out = ArrayList<SubtitleCandidate>()
        val selection = "(${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\') OR " +
            "(${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? ESCAPE '\\')"
        val args = arrayOf(relative, "${escapeLike(base)}%", "${escapeLike(relative)}sub%/%")
        context.contentResolver.query(
            files,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH),
            selection, args, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (!SubtitleFormats.isSupported(name)) continue
                val rel = c.getString(2) ?: ""
                val uri = MediaStore.Files.getContentUri(volume, c.getLong(0)).toString()
                out += SubtitleCandidate(name, uri, inSubtitleFolder = rel != relative)
            }
        }
        out
    }.getOrDefault(emptyList())

    private fun fromFiles(video: File): List<SubtitleCandidate> = runCatching {
        val dir = video.parentFile ?: return emptyList()
        val out = ArrayList<SubtitleCandidate>()
        dir.listFiles()?.forEach { f ->
            when {
                f.isFile && SubtitleFormats.isSupported(f.name) -> out += SubtitleCandidate(f.name, Uri.fromFile(f).toString())
                f.isDirectory && ExternalSubtitleMatcher.isSubtitleFolderName(f.name) -> {
                    val nested = File(f, video.nameWithoutExtension).takeIf { it.isDirectory } ?: f
                    nested.listFiles()?.filter { it.isFile && SubtitleFormats.isSupported(it.name) }?.forEach {
                        out += SubtitleCandidate(it.name, Uri.fromFile(it).toString(), inSubtitleFolder = true)
                    }
                }
            }
        }
        out
    }.getOrDefault(emptyList())

    private fun escapeLike(value: String) = value.replace("%", "\\%").replace("_", "\\_")
}
