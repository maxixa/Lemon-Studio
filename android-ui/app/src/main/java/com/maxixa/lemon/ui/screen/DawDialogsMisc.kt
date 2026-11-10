package com.maxixa.lemon.ui.screen

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.state.RecentProject
import com.maxixa.lemon.state.RecentProjectsStore
import com.maxixa.lemon.ui.settings.ColorPickerDialog
import com.maxixa.lemon.ui.settings.RecentProjectsDialog
import com.maxixa.lemon.ui.settings.SampleEditorDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.maxixa.lemon.state.*

@Composable
internal fun DawDialogsMisc(
    engine: EngineController,
    ctx: Context,
    scope: CoroutineScope,
    snackbarHost: SnackbarHostState,
    recentStore: RecentProjectsStore,
    openPicker: ActivityResultLauncher<Array<String>>,
    recentProjects: List<RecentProject>,
    onRecentProjectsReload: () -> Unit,
    showRecentDialog: Boolean,
    onDismissRecentDialog: () -> Unit,
    showColorPicker: Int?,
    onDismissColorPicker: () -> Unit,
    showSampleEditor: Int?,
    onDismissSampleEditor: () -> Unit,
) {
    if (showRecentDialog) {
        RecentProjectsDialog(
            projects = recentProjects,
            onPick = { rp ->
                onDismissRecentDialog()
                scope.launch {
                    try {
                        val uri = Uri.parse(rp.uri)
                        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
                        if (pfd == null) {
                            snackbarHost.showSnackbar("Cannot access ${rp.name}")
                            recentStore.remove(rp.uri)
                            onRecentProjectsReload()
                            return@launch
                        }
                        pfd.use {
                            if (engine.loadProjectFd(it.fd)) {
                                recentStore.lastProjectUri = rp.uri
                                snackbarHost.showSnackbar("Loaded ${rp.name}")
                            } else {
                                snackbarHost.showSnackbar("Failed to load ${rp.name}")
                            }
                        }
                    } catch (t: Throwable) {
                        snackbarHost.showSnackbar("Error: ${t.message}")
                        recentStore.remove(rp.uri)
                        onRecentProjectsReload()
                    }
                }
            },
            onOpenNew = { openPicker.launch(arrayOf("*/*")) },
            onDismiss = onDismissRecentDialog,
        )
    }

    if (showSampleEditor != null) {
        val idx = showSampleEditor
        val peaks = engine.trackPeaks.getOrNull(idx) ?: FloatArray(0)
        val chName = engine.tracks.getOrNull(idx) ?: "Channel ${idx + 1}"
        SampleEditorDialog(
            channelIndex = idx,
            channelName = chName,
            peaks = peaks,
            engine = engine,
            onDismiss = onDismissSampleEditor,
        )
    }

    if (showColorPicker != null) {
        val idx = showColorPicker
        ColorPickerDialog(
            channelIndex = idx,
            currentPacked = engine.trackColors.getOrNull(idx) ?: -1,
            onPick = { packed ->
                val r = (packed shr 16) and 0xFF
                val g = (packed shr 8) and 0xFF
                val b = packed and 0xFF
                engine.setChannelColor(idx, r, g, b)
                onDismissColorPicker()
            },
            onClear = { engine.clearChannelColor(idx); onDismissColorPicker() },
            onDismiss = onDismissColorPicker,
        )
    }
}
