package app.mvoon.ui.library

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.mvoon.core.media.MediaKind
import app.mvoon.core.util.formatFileSize
import app.mvoon.library.db.LibraryRow
import app.mvoon.library.db.Sources
import app.mvoon.library.db.mediaKind
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What deleting a file needs to know: a library row, or the file playing in the player. */
data class DeletableFile(
    val id: String,
    val uri: String,
    val title: String,
    val kind: MediaKind,
    /** [Sources.MEDIA_STORE], [Sources.FOLDER], or null for a file that isn't in the library. */
    val source: String?,
    val sizeBytes: Long = 0,
) {
    /** Android's media library owns it, so Android 11+ can confirm and delete it in one step. */
    val inMediaStore: Boolean get() = source == Sources.MEDIA_STORE || Uri.parse(uri).authority == MediaStore.AUTHORITY

    companion object {
        /** Null when the file can't be deleted from here (a stream, or another app's file). */
        fun of(id: String, uri: String, title: String, kind: MediaKind, source: String?, sizeBytes: Long = 0): DeletableFile? =
            DeletableFile(id, uri, title, kind, source, sizeBytes).takeIf { it.source != null || it.inMediaStore }
    }
}

fun LibraryRow.toDeletable() = DeletableFile(media.id, media.uri, media.title, media.mediaKind, media.source, media.sizeBytes)

private sealed interface DeleteOutcome {
    data object Deleted : DeleteOutcome

    /** Android 10: the system asks the user before an app may delete another app's file. */
    data class NeedsApproval(val request: IntentSender) : DeleteOutcome

    /** Android 9 and older: storage write permission first. */
    data object NeedsWritePermission : DeleteOutcome
    data class Failed(val message: String) : DeleteOutcome
}

/**
 * "Delete from device", from the library or the player. Returns the function that starts a deletion;
 * [onDeleted] runs once the file is really gone, [onCancelled] when it isn't (declined or failed).
 *
 * Files from Android's media library go through MediaStore: on Android 11+ the system shows its own
 * confirmation ([MediaStore.createDeleteRequest]), so Mvoon adds no second one. Older versions and
 * files in folders added with the folder picker get Mvoon's confirmation dialog first.
 */
@Composable
fun rememberDeleteMedia(onDeleted: (DeletableFile) -> Unit, onCancelled: () -> Unit = {}): (DeletableFile) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val afterDelete by rememberUpdatedState(onDeleted)
    val afterCancel by rememberUpdatedState(onCancelled)
    var confirming by remember { mutableStateOf<DeletableFile?>(null) }
    // The file waiting for a system dialog (delete request, Android 10 approval or storage permission).
    var waiting by remember { mutableStateOf<DeletableFile?>(null) }

    fun deleted(row: DeletableFile) {
        Toast.makeText(context, "Deleted “${row.title}”", Toast.LENGTH_SHORT).show()
        afterDelete(row)
    }

    val systemRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val row = waiting ?: return@rememberLauncherForActivityResult
        waiting = null
        if (result.resultCode != Activity.RESULT_OK) {
            afterCancel()
            return@rememberLauncherForActivityResult
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && row.inMediaStore) {
            deleted(row) // Android 11+: the system already deleted it.
        } else {
            // Android 10: access was granted, so this time the delete goes through.
            scope.launch {
                if (deleteFile(context, row) == DeleteOutcome.Deleted) {
                    deleted(row)
                } else {
                    failed(context, row)
                    afterCancel()
                }
            }
        }
    }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val row = waiting ?: return@rememberLauncherForActivityResult
        waiting = null
        if (!granted) {
            Toast.makeText(context, "Mvoon needs storage permission to delete files", Toast.LENGTH_LONG).show()
            afterCancel()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            if (deleteFile(context, row) == DeleteOutcome.Deleted) {
                deleted(row)
            } else {
                failed(context, row)
                afterCancel()
            }
        }
    }

    fun deleteConfirmed(row: DeletableFile) = scope.launch {
        when (val outcome = deleteFile(context, row)) {
            DeleteOutcome.Deleted -> deleted(row)
            is DeleteOutcome.NeedsApproval -> {
                waiting = row
                systemRequest.launch(IntentSenderRequest.Builder(outcome.request).build())
            }
            DeleteOutcome.NeedsWritePermission -> {
                waiting = row
                writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            is DeleteOutcome.Failed -> {
                Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                afterCancel()
            }
        }
    }

    confirming?.let { row ->
        val what = if (row.kind == MediaKind.AUDIO) "song" else "video"
        AlertDialog(
            onDismissRequest = {
                confirming = null
                afterCancel()
            },
            icon = { Icon(Lucide.Trash2, null) },
            title = { Text("Delete this $what?") },
            text = {
                Text(
                    "“${row.title}”" +
                        (row.sizeBytes.takeIf { it > 0 }?.let { " (${formatFileSize(it)})" } ?: "") +
                        " will be permanently deleted from this device. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirming = null; deleteConfirmed(row) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirming = null
                    afterCancel()
                }) { Text("Cancel") }
            },
        )
    }

    return remember(context) {
        { row ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && row.inMediaStore) {
                waiting = row
                val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(Uri.parse(row.uri)))
                systemRequest.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } else {
                confirming = row
            }
        }
    }
}

private fun failed(context: Context, row: DeletableFile) {
    Toast.makeText(context, "Couldn't delete “${row.title}”", Toast.LENGTH_LONG).show()
}

/** Deletes the file behind [row] without asking (the user already confirmed). */
private suspend fun deleteFile(context: Context, row: DeletableFile): DeleteOutcome = withContext(Dispatchers.IO) {
    val uri = Uri.parse(row.uri)
    val resolver = context.contentResolver
    try {
        when {
            row.source == Sources.FOLDER || DocumentsContract.isDocumentUri(context, uri) ->
                if (DocumentsContract.deleteDocument(resolver, uri)) DeleteOutcome.Deleted else DeleteOutcome.Failed("Couldn't delete “${row.title}”")
            uri.scheme == "file" ->
                if (File(uri.path.orEmpty()).delete()) DeleteOutcome.Deleted else DeleteOutcome.Failed("Couldn't delete “${row.title}”")
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ->
                DeleteOutcome.NeedsWritePermission
            // 0 rows means it was already gone (deleted by another app): the library just catches up.
            else -> resolver.delete(uri, null, null).let { DeleteOutcome.Deleted }
        }
    } catch (e: SecurityException) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
            DeleteOutcome.NeedsApproval(e.userAction.actionIntent.intentSender)
        } else if (row.source == Sources.FOLDER) {
            DeleteOutcome.Failed("Mvoon may only read this folder. Remove it in Settings → Library, add it again, then delete.")
        } else {
            DeleteOutcome.Failed("Android didn't allow deleting “${row.title}”")
        }
    } catch (e: Exception) {
        DeleteOutcome.Failed("Couldn't delete “${row.title}”")
    }
}
