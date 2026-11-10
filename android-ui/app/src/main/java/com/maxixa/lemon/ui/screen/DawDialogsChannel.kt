package com.maxixa.lemon.ui.screen

import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.ui.settings.ChannelOptionsDialog
import com.maxixa.lemon.ui.settings.InstrumentPickerDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.maxixa.lemon.state.*

@Composable
internal fun DawDialogsChannel(
    engine: EngineController,
    scope: CoroutineScope,
    snackbarHost: SnackbarHostState,
    samplePickerLauncher: ActivityResultLauncher<Array<String>>,
    renameTarget: Pair<Int, String>?,
    onRenameTargetConsumed: () -> Unit,
    onOpenSampleEditor: (Int) -> Unit,
    onOpenColorPicker: (Int) -> Unit,
    onOpenInstrumentPicker: (Int) -> Unit,
    onLoadSampleFor: (Int) -> Unit,
    showInstrumentPicker: Int?,
    onDismissInstrumentPicker: () -> Unit,
) {
    if (renameTarget != null) {
        val (idx, name) = renameTarget
        ChannelOptionsDialog(
            channelIndex = idx,
            currentName = name,
            hasSample = engine.channelSample(idx).isNotEmpty(),
            onOpenSampleEditor = { onOpenSampleEditor(idx); onRenameTargetConsumed() },
            currentStartOffset = engine.channelStartOffset(idx).toFloat(),
            onStartOffsetChange = { offset -> engine.setChannelStartOffset(idx, offset.toDouble()) },
            canDelete = engine.tracks.size > 1,
            currentColorPacked = engine.trackColors.getOrNull(idx) ?: -1,
            onChangeColor = { onOpenColorPicker(idx); onRenameTargetConsumed() },
            currentInstrumentLabel = engine.instrumentCatalog()
                .firstOrNull { it.first == engine.channelInstrument(idx) }?.second ?: "",
            onChangeInstrument = { onOpenInstrumentPicker(idx); onRenameTargetConsumed() },
            canMoveUp = idx > 0,
            canMoveDown = idx < engine.tracks.size - 1,
            onRename = { newName -> engine.renameChannel(idx, newName) },
            onMoveUp = { engine.reorderChannel(idx, idx - 1); onRenameTargetConsumed() },
            onMoveDown = { engine.reorderChannel(idx, idx + 1); onRenameTargetConsumed() },
            onDuplicate = {
                val newIdx = engine.duplicateChannel(idx)
                onRenameTargetConsumed()
                if (newIdx >= 0) scope.launch {
                    snackbarHost.showSnackbar("Duplicated to channel ${newIdx + 1}")
                }
            },
            onLoadSample = { onLoadSampleFor(idx); samplePickerLauncher.launch(arrayOf("*/*")); onRenameTargetConsumed() },
            onClearSample = {
                engine.clearChannelSample(idx)
                scope.launch { snackbarHost.showSnackbar("Sample cleared") }
                onRenameTargetConsumed()
            },
            onDelete = {
                if (engine.deleteChannel(idx)) {
                    scope.launch { snackbarHost.showSnackbar("Channel ${idx + 1} deleted") }
                } else {
                    scope.launch { snackbarHost.showSnackbar("Delete failed") }
                }
                onRenameTargetConsumed()
            },
            onDismiss = onRenameTargetConsumed,
        )
    }

    if (showInstrumentPicker != null) {
        val idx = showInstrumentPicker
        InstrumentPickerDialog(
            channelIndex = idx,
            currentId = engine.channelInstrument(idx),
            catalog = engine.instrumentCatalog(),
            onPick = { id ->
                if (engine.setBuiltinInstrument(idx, id)) {
                    val label = engine.instrumentCatalog().firstOrNull { it.first == id }?.second ?: id
                    scope.launch {
                        snackbarHost.showSnackbar(
                            "$label on channel ${idx + 1} — open Piano roll, tap channel button, pick this channel, then draw notes"
                        )
                    }
                } else {
                    scope.launch { snackbarHost.showSnackbar("Failed to set instrument") }
                }
                onDismissInstrumentPicker()
            },
            onDismiss = onDismissInstrumentPicker,
        )
    }
}
