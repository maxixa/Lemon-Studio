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
import com.maxixa.lemon.state.RecentProjectsStore
import com.maxixa.lemon.util.FileCopyUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Core SAF launchers: open, sample, save doc, quick save, record permission. */
internal data class CoreLaunchers(
    val openPicker: ActivityResultLauncher<Array<String>>,
    val samplePickerLauncher: ActivityResultLauncher<Array<String>>,
    val saveDocLauncher: ActivityResultLauncher<String>,
    val quickSaveLauncher: ActivityResultLauncher<String>,
    val recordPermission: ActivityResultLauncher<String>,
)

@Composable
internal fun rememberCoreLaunchers(
    engine: EngineController,
    ctx: Context,
    scope: CoroutineScope,
    snackbarHost: SnackbarHostState,
    recentStore: RecentProjectsStore,
    pendingSampleTarget: Int?,
    onSampleTargetConsumed: () -> Unit,
    onRecentProjectsReload: () -> Unit,
    onShowRecordDialog: () -> Unit,
): CoreLaunchers {
    val currentSampleTarget by rememberUpdatedState(pendingSampleTarget)

    val openPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                if (engine.loadProjectFd(pfd.fd)) {
                    val name = ctx.contentResolver
                        .query(uri, null, null, null, null)?.use { c ->
                            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                        } ?: "project.flp"
                    recentStore.push(uri, name)
                    recentStore.lastProjectUri = uri.toString()
                    onRecentProjectsReload()
                }
            }
        } catch (t: Throwable) {
            Log.e("Lemon", "openFile failed", t)
        }
    }

    val samplePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val target = currentSampleTarget
        onSampleTargetConsumed()
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        scope.launch {
            scope.launch { snackbarHost.showSnackbar("Copying sample…") }
            val path = withContext(Dispatchers.IO) { FileCopyUtil.copyUriToAppStorage(ctx, uri) }
            if (path != null) {
                val ok = engine.setChannelSample(target, path)
                snackbarHost.showSnackbar(
                    if (ok) "Sample loaded on channel ${target + 1}"
                    else "Failed to load sample"
                )
            } else {
                snackbarHost.showSnackbar("Copy failed")
            }
        }
    }

    val saveDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val pfd = ctx.contentResolver.openFileDescriptor(uri, "w")
                if (pfd == null) {
                    snackbarHost.showSnackbar("Could not open destination")
                    return@launch
                }
                pfd.use {
                    val bytes = withContext(Dispatchers.IO) { engine.saveProject(it.fd) }
                    if (bytes > 0) {
                        recentStore.lastSaveUri = uri.toString()
                        recentStore.lastProjectUri = uri.toString()
                        val name = ctx.contentResolver
                            .query(uri, null, null, null, null)?.use { c ->
                                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                            } ?: "project.flp"
                        recentStore.push(uri, name)
                        onRecentProjectsReload()
                        snackbarHost.showSnackbar("Saved $bytes bytes")
                    } else {
                        snackbarHost.showSnackbar("Save failed (check logcat)")
                    }
                }
            } catch (t: Throwable) {
                snackbarHost.showSnackbar("Save error: ${t.message ?: "unknown"}")
            }
        }
    }

    val quickSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val pfd = ctx.contentResolver.openFileDescriptor(uri, "w") ?: return@launch
                pfd.use {
                    val bytes = withContext(Dispatchers.IO) { engine.saveProject(it.fd) }
                    if (bytes > 0) {
                        recentStore.lastSaveUri = uri.toString()
                        recentStore.lastProjectUri = uri.toString()
                        snackbarHost.showSnackbar("Saved $bytes bytes")
                    } else {
                        snackbarHost.showSnackbar("Save failed")
                    }
                }
            } catch (t: Throwable) {
                snackbarHost.showSnackbar("Save error: ${t.message}")
            }
        }
    }

    val recordPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onShowRecordDialog()
        else scope.launch { snackbarHost.showSnackbar("Microphone permission denied") }
    }

    return CoreLaunchers(openPicker, samplePickerLauncher, saveDocLauncher, quickSaveLauncher, recordPermission)
}
