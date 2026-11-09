package com.maxixa.lemon.ui.piano

import androidx.compose.runtime.Composable
import com.maxixa.lemon.state.*

/**
 * Renders every dialog the piano roll can open.
 * Visibility booleans and their setters stay in PianoRollScreen; this
 * composable is a pure renderer that owns no state.
 */
@Composable
fun PianoRollDialogHost(
    state: PianoRollState,
    showZoom: Boolean,
    showTransform: Boolean,
    showGroove: Boolean,
    showChannelPicker: Boolean,
    showPatternPicker: Boolean,
    showPatternLength: Boolean,
    channelLabels: List<String>,
    patternNames: List<String>,
    currentPattern: Int,
    onSetPattern: (Int) -> Unit,
    onSetPatternLength: (Int) -> Unit,
    onCommit: () -> Unit,
    onDismissZoom: () -> Unit,
    onDismissTransform: () -> Unit,
    onDismissGroove: () -> Unit,
    onDismissChannelPicker: () -> Unit,
    onDismissPatternPicker: () -> Unit,
    onDismissPatternLength: () -> Unit,
) {
    if (showZoom) {
        ZoomDialog(state = state, onDismiss = onDismissZoom)
    }

    if (showTransform) {
        TransformDialog(
            selectedCount = state.selectedCount,
            state = state,
            onApply = { transformed ->
                state.replaceAll(transformed)
                onDismissTransform()
                onCommit()
            },
            onDismiss = onDismissTransform,
        )
    }

    if (showGroove) {
        GrooveDialog(
            selectedCount = state.selectedCount,
            state = state,
            onApply = { transformed ->
                state.replaceAll(transformed)
                onDismissGroove()
                onCommit()
            },
            onDismiss = onDismissGroove,
        )
    }

    if (showChannelPicker) {
        PianoRollChannelPicker(
            channelLabels = channelLabels,
            targetChannel = state.targetChannel,
            onPick = { state.targetChannel = it },
            onDismiss = onDismissChannelPicker,
        )
    }

    if (showPatternPicker) {
        PatternPickerDialog(
            patternNames = patternNames,
            currentPattern = currentPattern,
            onPick = { num -> onSetPattern(num) },
            onDismiss = onDismissPatternPicker,
        )
    }

    if (showPatternLength) {
        PatternLengthDialog(
            currentTicks = state.patternLength,
            ppq = state.ppq,
            onApply = { ticks ->
                onSetPatternLength(ticks)
                onDismissPatternLength()
            },
            onDismiss = onDismissPatternLength,
        )
    }

    val ctxIdx = state.contextMenuIndex
    if (ctxIdx in state.notes.indices) {
        val note = state.notes[ctxIdx]
        NotePropertiesDialog(
            note = note,
            snapTicks = state.snapTicks,
            onApply = { updated ->
                state.updateNoteFull(ctxIdx, updated)
                state.closeContextMenu()
                onCommit()
            },
            onDelete = {
                state.deleteNoteAt(ctxIdx)
                onCommit()
            },
            onDuplicate = {
                state.duplicateNoteAt(ctxIdx, state.ppq * 4)
                onCommit()
            },
            onDismiss = { state.closeContextMenu() },
        )
    }
}
