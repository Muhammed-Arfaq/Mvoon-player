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

private sealed interface DeleteOutcome {
    data object Deleted : DeleteOutcome

    /** Android 10: the system asks the user before an app may delete another app's file. */
    data class NeedsApproval(val request: IntentSender) : DeleteOutcome

    /** Android 9 and older: storage write permission first. */
    data object NeedsWritePermission : DeleteOutcome
    data class Failed(val message: String) : DeleteOutcome
}

/**
 * "Delete from device" for library items. Returns the function that starts a deletion.
 *
 * Files from Android's media library go through MediaStore: on Android 11+ the system shows its own
 * confirmation ([MediaStore.createDeleteRequest]), so Mvoon adds no second one. Older versions and
 * files in folders added with the folder picker get Mvoon's confirmation dialog first.
 */
@Composable
fun rememberDeleteMedia(vm: LibraryViewModel): (LibraryRow) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library by rememberUpdatedState(vm)
    var confirming by remember { mutableStateOf<LibraryRow?>(null) }
    // The row waiting for a system dialog (delete request, Android 10 approval or storage permission).
    var waiting by remember { mutableStateOf<LibraryRow?>(null) }

    fun deleted(row: LibraryRow) {
        library.onDeleted(row)
        Toast.makeText(context, "Deleted “${row.media.title}”", Toast.LENGTH_SHORT).show()
    }

    val systemRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val row = waiting ?: return@rememberLauncherForActivityResult
        waiting = null
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && row.media.source == Sources.MEDIA_STORE) {
            deleted(row) // Android 11+: the system already deleted it.
        } else {
            // Android 10: access was granted, so this time the delete goes through.
            scope.launch { if (deleteFile(context, row) == DeleteOutcome.Deleted) deleted(row) else failed(context, row) }
        }
    }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val row = waiting ?: return@rememberLauncherForActivityResult
        waiting = null
        if (!granted) {
            Toast.makeText(context, "Mvoon needs storage permission to delete files", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        scope.launch { if (deleteFile(context, row) == DeleteOutcome.Deleted) deleted(row) else failed(context, row) }
    }

    fun deleteConfirmed(row: LibraryRow) = scope.launch {
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
            is DeleteOutcome.Failed -> Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
        }
    }

    confirming?.let { row ->
        val what = if (row.media.mediaKind == MediaKind.AUDIO) "song" else "video"
        AlertDialog(
            onDismissRequest = { confirming = null },
            icon = { Icon(Lucide.Trash2, null) },
            title = { Text("Delete this $what?") },
            text = {
                Text(
                    "“${row.media.title}”" +
                        (row.media.sizeBytes.takeIf { it > 0 }?.let { " (${formatFileSize(it)})" } ?: "") +
                        " will be permanently deleted from this device. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirming = null; deleteConfirmed(row) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } },
        )
    }

    return remember(context) {
        { row ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && row.media.source == Sources.MEDIA_STORE) {
                waiting = row
                val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(Uri.parse(row.media.uri)))
                systemRequest.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } else {
                confirming = row
            }
        }
    }
}

private fun failed(context: Context, row: LibraryRow) {
    Toast.makeText(context, "Couldn't delete “${row.media.title}”", Toast.LENGTH_LONG).show()
}

/** Deletes the file behind [row] without asking (the user already confirmed). */
private suspend fun deleteFile(context: Context, row: LibraryRow): DeleteOutcome = withContext(Dispatchers.IO) {
    val uri = Uri.parse(row.media.uri)
    val resolver = context.contentResolver
    try {
        when {
            row.media.source == Sources.FOLDER || DocumentsContract.isDocumentUri(context, uri) ->
                if (DocumentsContract.deleteDocument(resolver, uri)) DeleteOutcome.Deleted else DeleteOutcome.Failed("Couldn't delete “${row.media.title}”")
            uri.scheme == "file" ->
                if (File(uri.path.orEmpty()).delete()) DeleteOutcome.Deleted else DeleteOutcome.Failed("Couldn't delete “${row.media.title}”")
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ->
                DeleteOutcome.NeedsWritePermission
            // 0 rows means it was already gone (deleted by another app): the library just catches up.
            else -> resolver.delete(uri, null, null).let { DeleteOutcome.Deleted }
        }
    } catch (e: SecurityException) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
            DeleteOutcome.NeedsApproval(e.userAction.actionIntent.intentSender)
        } else if (row.media.source == Sources.FOLDER) {
            DeleteOutcome.Failed("Mvoon may only read this folder. Remove it in Settings → Library, add it again, then delete.")
        } else {
            DeleteOutcome.Failed("Android didn't allow deleting “${row.media.title}”")
        }
    } catch (e: Exception) {
        DeleteOutcome.Failed("Couldn't delete “${row.media.title}”")
    }
}
