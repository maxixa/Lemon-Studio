package com.maxixa.lemon.ui.playlist

import androidx.compose.runtime.*
import com.maxixa.lemon.state.EngineController

/**
 * Three arrangement dialogs (picker, rename, delete confirm) + their
 * internal target state. Extracted from PlaylistDialogHost to keep
 * that file under 150 LOC.
 */
@Composable
internal fun ArrangementDialogHost(
    state: PlaylistState,
    engine: EngineController,
    arrangements: List<Pair<Int, String>>,
    onReloadTick: () -> Unit,
) {
    var renameTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var deleteTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }

    if (state.showArrangementPicker) {
        ArrangementPickerDialog(
            arrangements = arrangements,
            currentNum = engine.currentArrangement,
            onPick = { num -> if (engine.setArrangement(num)) onReloadTick() },
            onCreate = {
                val n = engine.addArrangement()
                if (n > 0) onReloadTick()
            },
            onRename = { num, currentName ->
                renameTarget = num to currentName
                state.showArrangementPicker = false
            },
            onDelete = { num ->
                val nm = arrangements.firstOrNull { it.first == num }?.second
                    ?: "Arrangement $num"
                deleteTarget = num to nm
                state.showArrangementPicker = false
            },
            onDismiss = { state.showArrangementPicker = false },
        )
    }

    val rt = renameTarget
    if (rt != null) {
        ArrangementRenameDialog(
            arrangementNum = rt.first,
            currentName = rt.second,
            onRename = { newName ->
                if (engine.renameArrangement(rt.first, newName)) onReloadTick()
            },
            onDismiss = { renameTarget = null },
        )
    }

    val dt = deleteTarget
    if (dt != null) {
        ArrangementDeleteConfirmDialog(
            arrangementNum = dt.first,
            arrangementName = dt.second,
            onConfirm = {
                if (engine.deleteArrangement(dt.first)) onReloadTick()
            },
            onDismiss = { deleteTarget = null },
        )
    }
}
