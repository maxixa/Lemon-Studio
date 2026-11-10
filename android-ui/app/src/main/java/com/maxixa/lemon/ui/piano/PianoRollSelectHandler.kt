package com.maxixa.lemon.ui.piano

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import com.maxixa.lemon.data.NoteView
import com.maxixa.lemon.state.PianoRollState
import com.maxixa.lemon.state.Tool
import kotlin.math.abs
import com.maxixa.lemon.state.*

/**
 * Select tool handler. Tap on note toggles selection; tap on empty is a no-op.
 * Drag on empty draws a box-select rectangle; drag on a selected note moves
 * the whole selection; drag on an unselected note auto-selects it first.
 * Long-press on a note opens the properties dialog.
 */
internal suspend fun AwaitPointerEventScope.handleSelectGesture(
    state: PianoRollState,
    setup: GestureSetup,
    onCommit: () -> Unit,
) {
    val g = setup.g
    var panning = false
    var dragStarted = false
    var boxMode = false
    var tapCandidate = true
    var longPressFired = false
    var curNoteIdx = setup.noteIdx
    var dragStartNote: NoteView? = if (curNoteIdx >= 0) state.notes[curNoteIdx] else null
    var dragSnapshot: Map<Long, Pair<Int, Int>>? = null
    var boxPriorSelected: Set<Long> = emptySet()

    while (true) {
        val ev = awaitPointerEvent(PointerEventPass.Main)
        val pressed = ev.changes.count { it.pressed }
        if (pressed == 0) break

        if (pressed >= 2) {
            if (!panning) {
                panning = true
                tapCandidate = false
                if (boxMode) { state.selectionRect = null; boxMode = false }
            }
            applyMultiTouchPan(state, ev, g)
            continue
        }
        if (panning) continue

        if (!longPressFired && !dragStarted && !boxMode && curNoteIdx >= 0
            && System.currentTimeMillis() - setup.downTime >= LONG_PRESS_MS
        ) {
            state.openContextMenu(curNoteIdx)
            longPressFired = true
            tapCandidate = false
            ev.changes.forEach { it.consume() }
            continue
        }

        val change = ev.changes.firstOrNull { it.pressed } ?: break
        val dx = change.position.x - setup.downPosition.x
        val dy = change.position.y - setup.downPosition.y

        // Box-select: Select tool + empty start + drag past threshold
        if (!dragStarted && curNoteIdx < 0
            && (abs(dx) > DRAG_THRESHOLD || abs(dy) > DRAG_THRESHOLD)
        ) {
            boxMode = true
            dragStarted = true
            tapCandidate = false
            boxPriorSelected = state.notes.filter { it.selected }.map { it.id }.toSet()
            state.updateBoxSelect(g, boxPriorSelected, setup.downPosition, change.position)
            change.consume()
            continue
        }

        if (boxMode) {
            state.updateBoxSelect(g, boxPriorSelected, setup.downPosition, change.position)
            change.consume()
            continue
        }

        // Drag-start on note
        if (!dragStarted && (abs(dx) > DRAG_THRESHOLD || abs(dy) > DRAG_THRESHOLD)) {
            dragStarted = true
            tapCandidate = false
            if (curNoteIdx >= 0 && !setup.resize) {
                if (!state.notes[curNoteIdx].selected) state.autoSelectOnly(curNoteIdx)
                if (state.selectedCount > 1) dragSnapshot = state.snapshotSelected()
            }
        }

        // Drag-update
        if (dragStarted && curNoteIdx >= 0 && !boxMode) {
            val dn = dragStartNote ?: state.notes[curNoteIdx]
            val curTick = state.snap(g.tickOfX(change.position.x - KEY_LEFT_PAD_PX))
            if (setup.resize) {
                state.notes[curNoteIdx] = dn.copy(
                    length = (curTick - dn.position).coerceAtLeast(state.minNoteLength),
                )
            } else {
                val curKey = g.keyOfY(change.position.y).coerceIn(0, 127)
                val snap = dragSnapshot
                if (snap != null && snap.size > 1) {
                    state.applyMultiNoteDrag(snap, curTick - setup.downTick, curKey - setup.downKey)
                } else {
                    state.notes[curNoteIdx] = dn.copy(
                        position = (dn.position + (curTick - setup.downTick)).coerceAtLeast(0),
                        key = state.snapKey((dn.key + (curKey - setup.downKey)).coerceIn(0, 127)),
                    )
                }
            }
            change.consume()
        }
    }

    if (boxMode) state.selectionRect = null

    if (tapCandidate && !panning && !longPressFired) {
        if (curNoteIdx >= 0) state.toggleSelAt(curNoteIdx)
    } else if (dragStarted && !boxMode && curNoteIdx >= 0) {
        onCommit()
    }
}
