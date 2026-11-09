package com.maxixa.lemon.ui.rack

import androidx.compose.runtime.Composable
import com.maxixa.lemon.ui.piano.PatternPickerDialog
import com.maxixa.lemon.ui.settings.PatternRenameDialog

/**
 * All Step Sequencer dialogs, extracted from StepSequencerScreen to keep
 * the screen file focused on layout + callbacks.
 *
 * Screen owns visibility state; this is a pure renderer with dismiss +
 * apply callbacks. Same pattern as PianoRollDialogHost.
 */
@Composable
fun StepSequencerDialogs(
    patternNames: List<String>,
    patternNum: Int,
    lengthBars: Int,
    swing: Int,
    showPicker: Boolean,
    showRename: Boolean,
    showLengthPicker: Boolean,
    showSwingPicker: Boolean,
    showFillPicker: Boolean,
    trackNames: List<String>,
    maskSize: Int,
    onPickPattern: (Int) -> Unit,
    onRenamePattern: (Int, String) -> Unit,
    onLengthPick: (Int) -> Unit,
    onSwingApply: (Int) -> Unit,
    onDismissPicker: () -> Unit,
    onDismissRename: () -> Unit,
    onDismissLengthPicker: () -> Unit,
    onDismissSwingPicker: () -> Unit,
    onFillApply: (Int, List<Boolean>) -> Unit,
    onDismissFillPicker: () -> Unit,
) {
    if (showPicker) {
        PatternPickerDialog(
            patternNames = patternNames,
            currentPattern = patternNum,
            onPick = { num -> onPickPattern(num); onDismissPicker() },
            onDismiss = onDismissPicker,
        )
    }

    if (showRename) {
        PatternRenameDialog(
            patternNum = patternNum,
            currentName = patternNames.getOrNull(patternNum - 1) ?: "",
            onRename = { newName -> onRenamePattern(patternNum, newName); onDismissRename() },
            onDismiss = onDismissRename,
        )
    }

    if (showLengthPicker) {
        PatternLengthDialog(
            currentBars = lengthBars,
            onPick = { bars -> onLengthPick(bars); onDismissLengthPicker() },
            onDismiss = onDismissLengthPicker,
        )
    }

    if (showSwingPicker) {
        SwingDialog(
            currentSwing = swing,
            onApply = { internalSwing -> onSwingApply(internalSwing); onDismissSwingPicker() },
            onDismiss = onDismissSwingPicker,
        )
    }

    if (showFillPicker) {
        FillDialog(
            trackNames = trackNames,
            maskSize = maskSize,
            onApply = { ch, mask -> onFillApply(ch, mask); onDismissFillPicker() },
            onDismiss = onDismissFillPicker,
        )
    }
}
