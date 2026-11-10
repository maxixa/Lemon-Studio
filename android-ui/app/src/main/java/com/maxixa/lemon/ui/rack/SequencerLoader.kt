package com.maxixa.lemon.ui.rack

import com.maxixa.lemon.LemonEngineBridge
import com.maxixa.lemon.data.NoteView
import com.maxixa.lemon.state.PianoRollBridge
import com.maxixa.lemon.state.*

/**
 * One-shot load of everything StepSequencerScreen needs to render.
 * Extracted so the screen stays focused on layout.
 */
data class SequencerLoad(
    val masks: Map<Int, List<Boolean>>,
    val lengthBars: Int,
    val swing: Int,
    val notes: List<NoteView>,
    val ppq: Int,
    val patternLength: Int,
)

data class SequencerLabels(
    val patternLabel: String,
    val lengthLabel: String,
    val swingLabel: String,
    val maskSize: Int,
)

fun loadSequencerState(handle: Long, trackNames: List<String>): SequencerLoad {
    val masks = mutableMapOf<Int, List<Boolean>>()
    for (i in trackNames.indices) {
        val bytes = try { LemonEngineBridge.engineStepMaskBytes(handle, i) }
                    catch (_: Throwable) { ByteArray(0) }
        masks[i] = StepMask.decode(bytes)
    }
    val lengthBars = try { LemonEngineBridge.enginePatternLengthBars(handle) }
                     catch (_: Throwable) { -1 }
    val swing = try { LemonEngineBridge.engineProjectSwing(handle) }
                catch (_: Throwable) { 0 }
    val decoded = try { PianoRollBridge.decodeNotes(handle) }
                  catch (_: Throwable) { null }
    return SequencerLoad(
        masks = masks,
        lengthBars = lengthBars,
        swing = swing,
        notes = decoded?.notes ?: emptyList(),
        ppq = decoded?.ppq ?: 96,
        patternLength = decoded?.patternLength ?: 384,
    )
}

fun buildSequencerLabels(
    patternNames: List<String>,
    patternNum: Int,
    lengthBars: Int,
    swing: Int,
    maskSize: Int,
): SequencerLabels {
    val patternLabel = patternNames.getOrNull(patternNum - 1) ?: "Pattern $patternNum"
    val lengthLabel = when {
        lengthBars < 0 -> "—"
        lengthBars == 0 -> "Auto"
        else -> "${lengthBars}b"
    }
    val swingPct = if (swing <= 0) 0 else ((swing * 100 + 64) / 128).coerceIn(0, 100)
    val swingLabel = if (swingPct == 0) "Swing · Off" else "Swing · $swingPct%"
    return SequencerLabels(patternLabel, lengthLabel, swingLabel, maskSize)
}
