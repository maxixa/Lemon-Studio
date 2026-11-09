package com.maxixa.lemon.ui.rack

import androidx.compose.runtime.*
import com.maxixa.lemon.LemonEngineBridge
import com.maxixa.lemon.data.NoteView

/**
 * Step sequencer orchestrator — state, effects, dialog visibility.
 * The rendered body lives in StepSequencerBody; dialogs in
 * StepSequencerDialogs. Callbacks that mutate masks live here because
 * this is the file that owns the SnapshotStateMap.
 */
@Composable
fun StepSequencerScreen(
    handle: Long,
    trackNames: List<String>,
    patternNum: Int,
    patternNames: List<String>,
    revision: Long = 0L,
    onPickPattern: (Int) -> Unit,
    onAddPattern: () -> Unit,
    onDuplicatePattern: (Int) -> Unit,
    onRenamePattern: (Int, String) -> Unit,
    onBack: () -> Unit,
) {
    val masks = remember { mutableStateMapOf<Int, List<Boolean>>() }
    var lengthBars by remember { mutableStateOf(0) }
    var swing by remember { mutableStateOf(0) }
    var notes by remember { mutableStateOf<List<NoteView>>(emptyList()) }
    var ppq by remember { mutableStateOf(96) }
    var patternLength by remember { mutableStateOf(384) }
    var showPicker by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showLengthPicker by remember { mutableStateOf(false) }
    var showSwingPicker by remember { mutableStateOf(false) }
    var showFillPicker by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    var maskSize by remember { mutableStateOf(StepMask.STEPS_PER_BAR) }

    LaunchedEffect(handle, trackNames.size, patternNum, revision, reloadTick) {
        val data = loadSequencerState(handle, trackNames)
        masks.clear()
        data.masks.forEach { (k, v) -> masks[k] = v }
        lengthBars = data.lengthBars
        swing = data.swing
        notes = data.notes
        ppq = data.ppq
        patternLength = data.patternLength
        maskSize = data.masks.values.maxOfOrNull { it.size } ?: StepMask.STEPS_PER_BAR
    }

    val labels = buildSequencerLabels(patternNames, patternNum, lengthBars, swing, maskSize)

    StepSequencerBody(
        trackNames = trackNames,
        masks = masks,
        maskSize = maskSize,
        allNotes = notes,
        ppq = ppq,
        patternLength = patternLength,
        labels = labels,
        onBack = onBack,
        onOpenPatternPicker = { showPicker = true },
        onOpenLengthPicker = { showLengthPicker = true },
        onOpenSwingPicker = { showSwingPicker = true },
        onOpenFillPicker = { showFillPicker = true },
        onAddPattern = onAddPattern,
        onDuplicatePattern = { onDuplicatePattern(patternNum) },
        onRenamePattern = { showRename = true },
        onToggle = { channelIdx, step ->
            val current = masks[channelIdx]
            if (current != null && step in current.indices) {
                val nowOn = try {
                    LemonEngineBridge.engineToggleStep(handle, channelIdx, step)
                } catch (_: Throwable) { current[step] }
                masks[channelIdx] = current.toMutableList().also { it[step] = nowOn }
            }
        },
        onRotate = { channelIdx, delta ->
            val ok = try { LemonEngineBridge.engineRotateSteps(handle, channelIdx, delta) }
                     catch (_: Throwable) { false }
            if (ok) {
                val bytes = try { LemonEngineBridge.engineStepMaskBytes(handle, channelIdx) }
                            catch (_: Throwable) { ByteArray(0) }
                masks[channelIdx] = StepMask.decode(bytes)
            }
        },
    )

    StepSequencerDialogs(
        patternNames = patternNames,
        patternNum = patternNum,
        lengthBars = lengthBars,
        swing = swing,
        showPicker = showPicker,
        showRename = showRename,
        showLengthPicker = showLengthPicker,
        showSwingPicker = showSwingPicker,
        showFillPicker = showFillPicker,
        trackNames = trackNames,
        maskSize = labels.maskSize,
        onPickPattern = onPickPattern,
        onRenamePattern = onRenamePattern,
        onLengthPick = { bars ->
            val ok = try { LemonEngineBridge.engineSetPatternLengthBars(handle, bars) }
                     catch (_: Throwable) { false }
            if (ok) reloadTick++
        },
        onSwingApply = { internalSwing ->
            val ok = try { LemonEngineBridge.engineSetProjectSwing(handle, internalSwing) }
                     catch (_: Throwable) { false }
            if (ok) reloadTick++
        },
        onDismissPicker = { showPicker = false },
        onDismissRename = { showRename = false },
        onDismissLengthPicker = { showLengthPicker = false },
        onDismissSwingPicker = { showSwingPicker = false },
        onFillApply = { channel, fillMask ->
            val bytes = ByteArray(fillMask.size) { if (fillMask[it]) 1 else 0 }
            val ok = try { LemonEngineBridge.engineApplyStepMask(handle, channel, bytes) }
                     catch (_: Throwable) { false }
            if (ok) reloadTick++
        },
        onDismissFillPicker = { showFillPicker = false },
    )
}
