package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.maxixa.lemon.state.*
import com.maxixa.lemon.ui.theme.BgDeep

/**
 * Rendered body of the piano roll: toolbar, action bar, canvas, event lane.
 *
 * Extracted from PianoRollScreen so that file stays focused on state and
 * effects. This composable owns no state — every value comes from `state`
 * or a callback. All dialog rendering lives in PianoRollDialogHost.
 */
@Composable
fun PianoRollBody(
    state: PianoRollState,
    playheadTicks: Int,
    laneField: LaneField,
    onLaneFieldChange: (LaneField) -> Unit,
    commit: () -> Unit,
    patternLabel: String,
    channelLabel: String,
    onBack: () -> Unit,
    onOpenZoom: () -> Unit,
    onOpenTransform: () -> Unit,
    onOpenGroove: () -> Unit,
    onOpenChannelPicker: () -> Unit,
    onOpenPatternPicker: () -> Unit,
    onOpenPatternLength: () -> Unit,
    onOpenQuantize: () -> Unit,
    onSeekTick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(BgDeep)) {
        PianoRollToolbar(
            state = state,
            onBack = onBack,
            onOpenZoom = onOpenZoom,
            patternLabel = patternLabel,
            onPatternTap = onOpenPatternPicker,
            onPatternLongPress = onOpenPatternLength,
            channelLabel = channelLabel,
            onChannelTap = onOpenChannelPicker,
        )

        if (state.selectedCount > 0 || state.hasClipboard) {
            PianoRollActionBar(
                selectedCount = state.selectedCount,
                hasClipboard = state.hasClipboard,
                onQuantize = onOpenQuantize,
                onTransform = onOpenTransform,
                onGroove = onOpenGroove,
                onDuplicate = { state.duplicateSelected(state.ppq * 4); commit() },
                onDelete = { state.deleteSelectedNotes(); commit() },
                onCopy = { state.copySelection() },
                onPaste = { state.pasteAt(playheadTicks); commit() },
                onClear = { state.deselectAll() },
            )
        }

        PianoRollCanvas(
            state = state,
            playheadTicks = playheadTicks,
            onCommit = commit,
            onSeekTick = onSeekTick,
            modifier = Modifier.weight(1f),
        )

        EventLaneSelector(current = laneField, onChange = onLaneFieldChange)
        EventLane(state = state, field = laneField, onCommit = commit)
    }
}
