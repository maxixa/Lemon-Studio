package com.maxixa.lemon.ui.piano

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.maxixa.lemon.data.NoteView
import com.maxixa.lemon.state.PianoRollState
import kotlin.math.max
import kotlin.math.min

/**
 * Gesture helpers for the piano roll.
 *
 * Kept out of PianoRollGestures.kt so the main state machine stays readable.
 * Everything here is a pure operation on PianoRollState — no pointer logic.
 */

internal fun PianoRollState.snapshotSelected(): Map<Long, Pair<Int, Int>> =
    notes.filter { it.selected }.associate { it.id to (it.position to it.key) }

/** Apply the drag delta to every note whose id appears in `snapshot`. */
internal fun PianoRollState.applyMultiNoteDrag(
    snapshot: Map<Long, Pair<Int, Int>>,
    deltaTick: Int,
    deltaKey: Int,
): Int {
    var touched = 0
    for (i in notes.indices) {
        val orig = snapshot[notes[i].id] ?: continue
        notes[i] = notes[i].copy(
            position = (orig.first + deltaTick).coerceAtLeast(0),
            key = (orig.second + deltaKey).coerceIn(0, 127),
        )
        touched++
    }
    return touched
}

/** Deselect everything, then select only the note at `idx`. FL auto-select-on-drag. */
internal fun PianoRollState.autoSelectOnly(idx: Int) {
    if (idx !in notes.indices) return
    for (i in notes.indices) {
        if (i != idx && notes[i].selected) notes[i] = notes[i].copy(selected = false)
    }
    notes[idx] = notes[idx].copy(selected = true)
}

/**
 * Live box-select update. Computes the axis-aligned rect between `down` and
 * `current` (both canvas pixel coords), stores it in `selectionRect` for
 * rendering, then applies the intersect-select logic.
 */
internal fun PianoRollState.updateBoxSelect(
    g: Grid,
    priorSelected: Set<Long>,
    down: Offset,
    current: Offset,
) {
    val rect = Rect(
        min(down.x, current.x), min(down.y, current.y),
        max(down.x, current.x), max(down.y, current.y),
    )
    selectionRect = rect
    applyBoxSelection(g, rect, priorSelected)
}

private fun PianoRollState.applyBoxSelection(
    g: Grid,
    rect: Rect,
    priorSelected: Set<Long>,
) {
    for (i in notes.indices) {
        val n = notes[i]
        val should = n.id in priorSelected || noteIntersectsRect(n, g, rect)
        if (n.selected != should) notes[i] = n.copy(selected = should)
    }
}

private fun noteIntersectsRect(n: NoteView, g: Grid, rect: Rect): Boolean {
    val nx = KEY_LEFT_PAD_PX + g.xOfTick(n.position.toFloat())
    val nw = (n.length * g.pxPerTick).coerceAtLeast(6f)
    val ny = g.yOfKey(n.key)
    val nh = g.pxPerRow
    return !(rect.right < nx || rect.left > nx + nw ||
             rect.bottom < ny || rect.top > ny + nh)
}

internal fun PianoRollState.toggleSelAt(i: Int) {
    if (i !in notes.indices) return
    notes[i] = notes[i].copy(selected = !notes[i].selected)
}

internal fun PianoRollState.hitTest(x: Float, y: Float, g: Grid): Int {
    for (i in notes.indices.reversed()) {
        val n = notes[i]
        if (!showGhosts && n.channel != targetChannel) continue
        val nx = KEY_LEFT_PAD_PX + g.xOfTick(n.position.toFloat())
        val nw = (n.length * g.pxPerTick).coerceAtLeast(8f)
        val ny = g.yOfKey(n.key)
        val nh = g.pxPerRow
        if (x in (nx - 4f)..(nx + nw + 4f) && y in ny..(ny + nh)) return i
    }
    return -1
}
