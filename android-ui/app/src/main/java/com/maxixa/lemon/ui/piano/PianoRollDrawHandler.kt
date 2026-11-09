package com.maxixa.lemon.ui.piano

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import com.maxixa.lemon.data.NoteView
import com.maxixa.lemon.state.PianoRollState
import com.maxixa.lemon.state.Tool
import kotlin.math.abs

/**
 * Pencil and Eraser gesture handler. Both tools share a drag/pan/long-press
 * loop and only differ in tap behavior:
 *
 *   Pencil tap on empty → add note, commit
 *   Pencil tap on note  → no-op
 *   Pencil drag on empty → add note, then resize live
 *   Eraser tap on note  → delete note, commit
 *   Eraser tap on empty → no-op
 *   Eraser drag on note → moves/selects like pencil (existing behavior)
 *
 * Long-press on a note opens the context menu (properties dialog) for both.
 */
internal suspend fun AwaitPointerEventScope.handleDrawGesture(
    state: PianoRollState,
    setup: GestureSetup,
    onCommit: () -> Unit,
) {
    val g = setup.g
    var panning = false
    var dragStarted = false
    var tapCandidate = true
    var longPressFired = false
    var curNoteIdx = setup.noteIdx
    var dragStartNote: NoteView? = if (curNoteIdx >= 0) state.notes[curNoteIdx] else null
    var dragSnapshot: Map<Long, Pair<Int, Int>>? = null
    var addedDuringDrag = false

    while (true) {
        val ev = awaitPointerEvent(PointerEventPass.Main)
        val pressed = ev.changes.count { it.pressed }
        if (pressed == 0) break

        if (pressed >= 2) {
            if (!panning) { panning = true; tapCandidate = false }
            applyMultiTouchPan(state, ev, g)
            continue
        }
        if (panning) continue

        if (!longPressFired && !dragStarted && curNoteIdx >= 0
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

        if (!dragStarted && (abs(dx) > DRAG_THRESHOLD || abs(dy) > DRAG_THRESHOLD)) {
            dragStarted = true
            tapCandidate = false
            if (curNoteIdx < 0 && state.tool == Tool.Pencil) {
                curNoteIdx = state.add(
                    setup.downTick, state.defaultNoteLength,
                    state.snapKey(setup.downKey), state.targetChannel,
                )
                dragStartNote = state.notes[curNoteIdx]
                addedDuringDrag = true
            } else if (curNoteIdx >= 0 && !setup.resize) {
                if (!state.notes[curNoteIdx].selected) state.autoSelectOnly(curNoteIdx)
                if (state.selectedCount > 1) dragSnapshot = state.snapshotSelected()
            }
        }

        if (dragStarted && curNoteIdx >= 0) {
            val dn = dragStartNote ?: state.notes[curNoteIdx]
            val curTick = state.snap(g.tickOfX(change.position.x - KEY_LEFT_PAD_PX))
            if (setup.resize || addedDuringDrag) {
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

    if (tapCandidate && !panning && !longPressFired) {
        when (state.tool) {
            Tool.Pencil -> if (curNoteIdx < 0) {
                state.add(
                    setup.downTick, state.defaultNoteLength,
                    state.snapKey(setup.downKey), state.targetChannel,
                )
                onCommit()
            }
            Tool.Eraser -> if (curNoteIdx >= 0) {
                state.notes.removeAt(curNoteIdx)
                onCommit()
            }
            else -> {}
        }
    } else if (dragStarted && curNoteIdx >= 0) {
        onCommit()
    }
}
