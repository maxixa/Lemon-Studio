package com.maxixa.lemon.ui.screen

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.state.ExportController
import com.maxixa.lemon.state.ExportFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Export launchers: single-file export + stems folder export. */
internal data class ExportLaunchers(
    val createDoc: ActivityResultLauncher<String>,
    val treePicker: ActivityResultLauncher<Uri?>,
)

@Composable
internal fun rememberExportLaunchers(
    engine: EngineController,
    ctx: Context,
    scope: CoroutineScope,
    snackbarHost: SnackbarHostState,
    exportController: ExportController,
    pendingExport: ExportRequest?,
    onExportConsumed: () -> Unit,
): ExportLaunchers {
    val current by rememberUpdatedState(pendingExport)

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/*")
    ) { uri: Uri? ->
        val pend = current
        onExportConsumed()
        if (uri == null || pend == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val pfd = ctx.contentResolver.openFileDescriptor(uri, "w")
                if (pfd == null) {
                    snackbarHost.showSnackbar("Could not open destination")
                    return@launch
                }
                pfd.use {
                    val result = exportController.export(it.fd, pend.songMode, pend.rate, pend.format)
                    snackbarHost.showSnackbar(result.message)
                }
            } catch (t: Throwable) {
                snackbarHost.showSnackbar("Export error: ${t.message ?: "unknown"}")
            }
        }
    }

    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        val pend = current
        onExportConsumed()
        if (treeUri == null || pend == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                try {
                    ctx.contentResolver.takePersistableUriPermission(
                        treeUri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (_: Throwable) { }
                val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                val rootDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)

                fun createFile(name: String): Uri? = try {
                    android.provider.DocumentsContract.createDocument(
                        ctx.contentResolver, rootDocUri, "audio/wav", name,
                    )
                } catch (t: Throwable) {
                    Log.w("Lemon", "createDocument($name) failed: ${t.message}")
                    null
                }

                val mixUri = createFile("mix.wav")
                if (mixUri != null) {
                    ctx.contentResolver.openFileDescriptor(mixUri, "w")?.use { pfd ->
                        exportController.export(pfd.fd, pend.songMode, pend.rate, ExportFormat.Wav)
                    }
                }

                var written = 0
                val total = engine.tracks.size
                for (i in 1 until total) {
                    val rawName = engine.tracks[i].ifEmpty { "Track ${i + 1}" }
                    val safeName = rawName.replace(Regex("[^A-Za-z0-9 _-]"), "_")
                    val fileName = "%02d %s.wav".format(i, safeName).take(80)
                    val childUri = createFile(fileName) ?: continue
                    ctx.contentResolver.openFileDescriptor(childUri, "w")?.use { pfd ->
                        val bytes = withContext(Dispatchers.IO) {
                            engine.renderStemFd(i, pfd.fd, pend.songMode, pend.rate)
                        }
                        if (bytes > 0) written++
                    }
                }
                snackbarHost.showSnackbar("Exported mix + $written stems")
            } catch (t: Throwable) {
                snackbarHost.showSnackbar("Stem export error: ${t.message ?: "unknown"}")
            }
        }
    }

    return ExportLaunchers(createDoc, treePicker)
}
