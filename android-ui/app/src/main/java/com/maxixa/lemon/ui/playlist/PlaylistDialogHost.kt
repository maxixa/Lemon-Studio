package com.maxixa.lemon.ui.playlist

import androidx.compose.runtime.*
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.ui.settings.LoopRangeDialog
import com.maxixa.lemon.state.EngineController.AutomationLfo
import com.maxixa.lemon.state.*

/**
 * All six playlist dialogs, rendered here instead of in PlaylistScreen.
 * Local state for `renameTarget` / `deleteTarget` lives here — Screen
 * only owns `showAdd` and `showLoopDialog` (external triggers).
 */
@Composable
fun PlaylistDialogHost(
    state: PlaylistState,
    engine: EngineController,
    patternNames: List<String>,
    arrangements: List<Pair<Int, String>>,
    automationPoints: Map<Int, List<Pair<Double, Double>>>,
    showAdd: Boolean,
    onDismissAdd: () -> Unit,
    onAddClip: (Int) -> Unit,
    showAddAutomation: Boolean,
    onDismissAddAutomation: () -> Unit,
    onCreateAutomation: (target: Int, value: Double) -> Unit,
    showLoopDialog: Boolean,
    onDismissLoop: () -> Unit,
    commit: () -> Unit,
    onReloadTick: () -> Unit,
) {

    if (showAdd) {
        AddClipDialog(
            patternNames = patternNames,
            onPick = onAddClip,
            onDismiss = onDismissAdd,
        )
    }

    if (showAddAutomation) {
        AddAutomationDialog(
            channelLabels = engine.tracks,
            onConfirm = onCreateAutomation,
            onDismiss = onDismissAddAutomation,
        )
    }

    val selId = state.selectedMarkerId
    if (selId != null) {
        val selMarker = state.markers.firstOrNull { it.id == selId }
        if (selMarker != null) {
            MarkerDialog(
                marker = selMarker,
                ppq = state.ppq,
                onSave = { newName, newPos ->
                    val idx = state.markers.indexOfFirst { it.id == selId }
                    if (idx >= 0) {
                        state.markers[idx] = state.markers[idx]
                            .copy(name = newName, position = newPos)
                        commit()
                    }
                    state.selectedMarkerId = null
                },
                onDelete = {
                    state.deleteMarker(selId)
                    state.selectedMarkerId = null
                    commit()
                },
                onDismiss = { state.selectedMarkerId = null },
            )
        }
    }

    ArrangementDialogHost(state, engine, arrangements, onReloadTick)

    if (showLoopDialog) {
        LoopRangeDialog(
            ppq = engine.previewPpq(),
            initialStart = engine.loopRangeStart,
            initialEnd = engine.loopRangeEnd,
            onSet = { s, e ->
                engine.setLoopRange(s, e)
                onDismissLoop()
            },
            onClear = {
                engine.clearLoopRange()
                onDismissLoop()
            },
            onDismiss = onDismissLoop,
        )
    }


    val editorCh = state.automationEditorChannelIdx
    if (editorCh != null) {
        val clip = state.clips.firstOrNull { it.sourceKind == 1 && it.pattern == editorCh }
        if (clip != null) {
            val clipBeats = clip.length.toDouble() / state.ppq.toDouble().coerceAtLeast(1.0)
            val initPts = automationPoints[editorCh] ?: listOf(0.0 to 0.5)
            val initLfo = engine.getAutomationLfo(editorCh) ?: AutomationLfo()
            AutomationEditorDialog(
                channelName = engine.tracks.getOrNull(editorCh) ?: "Channel ${editorCh + 1}",
                initialPoints = initPts,
                initialLfo = initLfo,
                clipBeats = clipBeats,
                onApply = { pts, lfo ->
                    if (engine.setAutomationFull(editorCh, pts, lfo)) onReloadTick()
                    state.automationEditorChannelIdx = null
                },
                onDismiss = { state.automationEditorChannelIdx = null },
            )
        } else {
            state.automationEditorChannelIdx = null
        }
    }
}
