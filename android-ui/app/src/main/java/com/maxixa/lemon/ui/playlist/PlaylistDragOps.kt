package com.maxixa.lemon.ui.playlist

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass

private const val HEADER_W = 90f
private const val ROW_H = 36f
private const val RESIZE_HANDLE = 20f

/**
 * Gesture helpers for the playlist, kept out of the gesture state machines.
 * Everything here is a pure operation on PlaylistState.
 */

internal fun PlaylistState.hitTest(x: Float, y: Float, w: Float): Pair<Int?, Boolean> {
    if (x < HEADER_W) return null to false
    for (i in clips.indices.reversed()) {
        val c = clips[i]
        val cx = HEADER_W + ((c.position - scrollX) * pxPerTick(w))
        val cw = (c.length * pxPerTick(w)).coerceAtLeast(6f)
        val cy = (c.track - scrollY) * ROW_H
        val ch = ROW_H - 2f
        if (y < cy || y > cy + ch) continue
        if (x >= cx - 3f && x <= cx + cw + 3f) {
            return i to (x >= (cx + cw - RESIZE_HANDLE))
        }
    }
    return null to false
}

internal fun PlaylistState.snapshotSelected(): Map<Long, Pair<Int, Int>> =
    clips.filter { it.selected }.associate { it.id to (it.position to it.track) }

/** Apply drag delta to every clip in the snapshot. Returns count moved. */
internal fun PlaylistState.applyMultiDrag(
    snapshot: Map<Long, Pair<Int, Int>>,
    deltaPos: Int,
    deltaTrack: Int,
): Int {
    var touched = 0
    val maxTrack = (trackCount - 1).coerceAtLeast(0)
    for (i in clips.indices) {
        val orig = snapshot[clips[i].id] ?: continue
        clips[i] = clips[i].copy(
            position = (orig.first + deltaPos).coerceAtLeast(0),
            track = (orig.second + deltaTrack).coerceIn(0, maxTrack),
        )
        touched++
    }
    return touched
}

internal fun PlaylistState.toggleSelAt(i: Int) {
    if (i !in clips.indices) return
    clips[i] = clips[i].copy(selected = !clips[i].selected)
}

/** Deselect every clip, then select only index `i`. FL auto-select semantics. */
internal fun PlaylistState.autoSelectOnly(i: Int) {
    if (i !in clips.indices) return
    for (j in clips.indices) {
        if (j != i && clips[j].selected) clips[j] = clips[j].copy(selected = false)
    }
    clips[i] = clips[i].copy(selected = true)
}

/**
 * Live box-select. Stores the rect for rendering, then re-computes the
 * selected set as (prior selection) ∪ (clips intersecting the rect).
 */
internal fun PlaylistState.updateBoxSelect(
    priorSelected: Set<Long>,
    start: Offset,
    current: Offset,
    w: Float,
) {
    val rect = Rect(
        min(start.x, current.x), min(start.y, current.y),
        max(start.x, current.x), max(start.y, current.y),
    )
    selectionRect = rect
    val ppx = pxPerTick(w)
    for (i in clips.indices) {
        val c = clips[i]
        val should = c.id in priorSelected || clipIntersectsRect(c, rect, ppx)
        if (c.selected != should) clips[i] = c.copy(selected = should)
    }
}

private fun PlaylistState.clipIntersectsRect(c: ClipView, rect: Rect, ppx: Float): Boolean {
    val cx = HEADER_W + ((c.position - scrollX) * ppx)
    val cw = (c.length * ppx).coerceAtLeast(6f)
    val cy = (c.track - scrollY) * ROW_H
    val ch = ROW_H - 2f
    return !(rect.right < cx || rect.left > cx + cw ||
             rect.bottom < cy || rect.top > cy + ch)
}

/**
 * Duplicate every selected clip one `barTicks` to the right, on the same
 * track. Originals are deselected; the copies become the new selection.
 * Returns the number of copies created (0 when nothing was selected).
 *
 * Caller is responsible for `commit()` afterwards.
 */
internal fun PlaylistState.duplicateSelected(barTicks: Int): Int {
    val originals = clips.filter { it.selected }
    if (originals.isEmpty()) return 0
    // Deselect originals
    for (i in clips.indices) {
        if (clips[i].selected) clips[i] = clips[i].copy(selected = false)
    }
    // Append copies, selected
    for (c in originals) {
        clips.add(c.copy(
            id = nextId++,
            position = c.position + barTicks,
            selected = true,
        ))
    }
    return originals.size
}

/**
 * Return 0 = none, 1 = left handle, 2 = right handle.
 * Only when the loop is active and the tap is in the top strip (matches
 * canvas handle nub y-range) so it can't hijack clip drags below y=20.
 */
internal fun PlaylistState.hitTestLoopHandle(x: Float, y: Float, w: Float): Int {
    if (loopStart < 0 || loopEnd <= loopStart) return 0
    if (y > 20f) return 0
    val ppx = pxPerTick(w)
    if (ppx <= 0f) return 0
    val lx0 = 90f + (loopStart - scrollX) * ppx
    val lx1 = 90f + (loopEnd - scrollX) * ppx
    if (x in (lx0 - 10f)..(lx0 + 10f)) return 1
    if (x in (lx1 - 10f)..(lx1 + 10f)) return 2
    return 0
}

/**
 * Handle a loop-handle drag. `loopHandle` is 1 (left) or 2 (right).
 * Updates state.loopStart/loopEnd live during the drag (canvas reflects
 * immediately), then fires `onLoopRangeChange` on release so the screen
 * can push the new range to the engine.
 */
internal suspend fun AwaitPointerEventScope.handleLoopDrag(
    state: PlaylistState,
    w: Float,
    loopHandle: Int,
    onLoopRangeChange: (Int, Int) -> Unit,
) {
    while (true) {
        val ev = awaitPointerEvent(PointerEventPass.Main)
        if (ev.changes.count { it.pressed } == 0) break
        val ch = ev.changes.firstOrNull { it.pressed } ?: break
        val tick = state.tickAtX(ch.position.x, w)
        when (loopHandle) {
            1 -> state.loopStart = tick.coerceIn(0, (state.loopEnd - 1).coerceAtLeast(0))
            2 -> state.loopEnd = tick.coerceAtLeast(state.loopStart + 1)
        }
        ch.consume()
    }
    onLoopRangeChange(state.loopStart, state.loopEnd)
}
