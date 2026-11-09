package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.maxixa.lemon.data.NoteView
import com.maxixa.lemon.state.*
import kotlin.math.abs
import kotlin.math.roundToInt

private const val RESIZE_HANDLE_PX = 24f
private const val DRAG_THRESHOLD = 12f
private const val LONG_PRESS_MS = 500L
private const val RULER_HEIGHT_PX = 16f

fun Modifier.pianoRollGestures(
    state: PianoRollState,
    onCommit: () -> Unit,
    onSeekTick: (Int) -> Unit = {},
): Modifier = pointerInput(state.tool) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        if (w <= 0f || h <= 0f) return@awaitEachGesture

        val g = Grid(Size(w, h), state.ppq, state.hBeats, state.vRows, state.scrollX, state.scrollY)

        // ─── Ruler tap-to-seek (top strip over the note area) ────────
        if (down.position.y < RULER_HEIGHT_PX && down.position.x >= KEY_LEFT_PAD_PX) {
            var current = down
            onSeekTick(g.tickOfX(current.position.x - KEY_LEFT_PAD_PX)
                .toInt().coerceAtLeast(0))
            current.consume()
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Main)
                if (ev.changes.count { it.pressed } == 0) break
                val ch = ev.changes.firstOrNull { it.pressed } ?: break
                onSeekTick(g.tickOfX(ch.position.x - KEY_LEFT_PAD_PX)
                    .toInt().coerceAtLeast(0))
                ch.consume()
            }
            return@awaitEachGesture
        }

        val downTick = state.snap(g.tickOfX(down.position.x - KEY_LEFT_PAD_PX))
        val downKey = g.keyOfY(down.position.y).coerceIn(0, 127)

        var noteIdx = -1
        var resize = false
        if (state.tool == Tool.Select || state.tool == Tool.Pencil || state.tool == Tool.Eraser) {
            noteIdx = state.hitTest(down.position.x, down.position.y, g)
            if (noteIdx >= 0 && (state.tool == Tool.Select || state.tool == Tool.Pencil)) {
                val n = state.notes[noteIdx]
                val endX = KEY_LEFT_PAD_PX + g.xOfTick((n.position + n.length).toFloat())
                resize = abs(down.position.x - endX) < RESIZE_HANDLE_PX
            }
        }

        var panning = false
        var dragStarted = false
        var boxMode = false
        var tapCandidate = true
        var longPressFired = false
        val downTime = System.currentTimeMillis()
        var dragStartNote: NoteView? = if (noteIdx >= 0) state.notes[noteIdx] else null
        var dragSnapshot: Map<Long, Pair<Int, Int>>? = null
        var addedDuringDrag = false
        var boxPriorSelected: Set<Long> = emptySet()

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val pressed = event.changes.count { it.pressed }
            if (pressed == 0) break

            if (pressed >= 2) {
                if (!panning) {
                    panning = true
                    tapCandidate = false
                    if (boxMode) { state.selectionRect = null; boxMode = false }
                }
                val pan = event.calculatePan()
                state.scrollX = (state.scrollX - pan.x / g.pxPerTick).coerceAtLeast(0f)
                state.scrollY = (state.scrollY + (pan.y / g.pxPerRow).roundToInt()).coerceIn(0, 127)
                event.changes.forEach { it.consume() }
                continue
            }
            if (panning) continue

            if (!longPressFired && !dragStarted && !boxMode && noteIdx >= 0
                && System.currentTimeMillis() - downTime >= LONG_PRESS_MS
            ) {
                state.openContextMenu(noteIdx)
                longPressFired = true
                tapCandidate = false
                event.changes.forEach { it.consume() }
                continue
            }

            val change = event.changes.firstOrNull { it.pressed } ?: break
            val dx = change.position.x - down.position.x
            val dy = change.position.y - down.position.y

            if (!dragStarted && state.tool == Tool.Select && noteIdx < 0
                && (abs(dx) > DRAG_THRESHOLD || abs(dy) > DRAG_THRESHOLD)
            ) {
                boxMode = true
                dragStarted = true
                tapCandidate = false
                boxPriorSelected = state.notes.filter { it.selected }.map { it.id }.toSet()
                state.updateBoxSelect(g, boxPriorSelected, down.position, change.position)
                change.consume()
                continue
            }

            if (boxMode) {
                state.updateBoxSelect(g, boxPriorSelected, down.position, change.position)
                change.consume()
                continue
            }

            if (!dragStarted && (abs(dx) > DRAG_THRESHOLD || abs(dy) > DRAG_THRESHOLD)) {
                dragStarted = true
                tapCandidate = false
                if (noteIdx < 0 && state.tool == Tool.Pencil) {
                    noteIdx = state.add(downTick, state.defaultNoteLength, state.snapKey(downKey), state.targetChannel)
                    dragStartNote = state.notes[noteIdx]
                    addedDuringDrag = true
                } else if (noteIdx >= 0 && !resize) {
                    if (!state.notes[noteIdx].selected) state.autoSelectOnly(noteIdx)
                    if (state.selectedCount > 1) dragSnapshot = state.snapshotSelected()
                }
            }

            if (dragStarted && noteIdx >= 0 && !boxMode) {
                val dn = dragStartNote ?: state.notes[noteIdx]
                val curTick = state.snap(g.tickOfX(change.position.x - KEY_LEFT_PAD_PX))
                if (resize || addedDuringDrag) {
                    state.notes[noteIdx] = dn.copy(
                        length = (curTick - dn.position).coerceAtLeast(state.minNoteLength),
                    )
                } else {
                    val curKey = g.keyOfY(change.position.y).coerceIn(0, 127)
                    val snap = dragSnapshot
                    if (snap != null && snap.size > 1) {
                        state.applyMultiNoteDrag(snap, curTick - downTick, curKey - downKey)
                    } else {
                        state.notes[noteIdx] = dn.copy(
                            position = (dn.position + (curTick - downTick)).coerceAtLeast(0),
                            key = state.snapKey((dn.key + (curKey - downKey)).coerceIn(0, 127)),
                        )
                    }
                }
                change.consume()
            }
        }

        if (boxMode) state.selectionRect = null

        if (tapCandidate && !panning && !longPressFired) {
            when (state.tool) {
                Tool.Pencil -> if (noteIdx < 0) {
                    state.add(downTick, state.defaultNoteLength, state.snapKey(downKey), state.targetChannel)
                    onCommit()
                }
                Tool.Eraser -> if (noteIdx >= 0) {
                    state.notes.removeAt(noteIdx)
                    onCommit()
                }
                Tool.Select -> if (noteIdx >= 0) state.toggleSelAt(noteIdx)
                Tool.Pan -> {}
            }
        } else if (dragStarted && !boxMode && noteIdx >= 0) {
            onCommit()
        }
    }
}
