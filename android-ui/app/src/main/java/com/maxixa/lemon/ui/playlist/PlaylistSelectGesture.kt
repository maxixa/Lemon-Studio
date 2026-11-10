package com.maxixa.lemon.ui.playlist

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ROW_H = 36f
private const val DRAG_THRESHOLD = 12f
private const val TAG = "LemonPlSelect"

/**
 * Playlist select tool:
 * - tap clip → toggle its selection
 * - drag empty → box-select (additive against prior selection)
 * - drag selected clip (single-selected) → move it
 * - drag one of many selected → multi-move every selected clip
 * - drag unselected clip → auto-select it, then move alone (FL parity)
 * - drag right edge → resize (single clip always)
 * - two fingers → pan
 */
internal fun Modifier.selectGesture(
    state: PlaylistState,
    onCommit: () -> Unit,
    onLoopRangeChange: (Int, Int) -> Unit = { _, _ -> },
): Modifier = pointerInput(state.tool) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val w = size.width.toFloat(); val h = size.height.toFloat()
        if (w <= 0f || h <= 0f) return@awaitEachGesture

        val loopHandle = state.hitTestLoopHandle(down.position.x, down.position.y, w)
        if (loopHandle != 0) {
            handleLoopDrag(state, w, loopHandle, onLoopRangeChange)
            return@awaitEachGesture
        }

        val markerIdx = state.hitTestMarker(down.position.x, down.position.y, w)
        if (markerIdx >= 0) {
            state.selectedMarkerId = state.markers[markerIdx].id
            return@awaitEachGesture
        }

        val hit = state.hitTest(down.position.x, down.position.y, w)
        val hitIdx = hit.first
        val isResize = hit.second
        val downX = down.position.x
        val downY = down.position.y
        Log.d(TAG, "DOWN hit=$hitIdx resize=$isResize")

        var panning = false
        var dragStarted = false
        var boxMode = false
        var tapCandidate = true
        var longPressFired = false
        val downTime = System.currentTimeMillis()
        var priorSelected: Set<Long> = emptySet()
        var dragSnapshot: Map<Long, Pair<Int, Int>>? = null
        var singleOrigPos = 0
        var singleOrigTrack = 0

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
                val pan = ev.calculatePan()
                state.scrollX = (state.scrollX - pan.x / state.pxPerTick(w)).coerceAtLeast(0f)
                state.scrollY = (state.scrollY - (pan.y / ROW_H).roundToInt())
                    .coerceIn(0, (state.trackCount - 1).coerceAtLeast(0))
                ev.changes.forEach { it.consume() }
                continue
            }
            if (panning) continue

            val c = ev.changes.firstOrNull { it.pressed } ?: break
            val dx = c.position.x - downX
            val dy = c.position.y - downY

            if (!longPressFired && !dragStarted && tapCandidate && hitIdx != null
                && hitIdx in state.clips.indices
                && System.currentTimeMillis() - downTime >= 500L
            ) {
                val c0 = state.clips[hitIdx]
                if (c0.sourceKind == 1 && c0.pattern in state.automationClipChannels) {
                    state.automationEditorChannelIdx = c0.pattern
                    longPressFired = true
                    tapCandidate = false
                    break
                }
            }

            if (!dragStarted && (abs(dx) > DRAG_THRESHOLD || abs(dy) > DRAG_THRESHOLD)) {
                dragStarted = true
                tapCandidate = false
                if (hitIdx == null) {
                    boxMode = true
                    priorSelected = state.clips.filter { it.selected }.map { it.id }.toSet()
                    state.updateBoxSelect(priorSelected, Offset(downX, downY), c.position, w)
                } else {
                    singleOrigPos = state.clips[hitIdx].position
                    singleOrigTrack = state.clips[hitIdx].track
                    if (!state.clips[hitIdx].selected) state.autoSelectOnly(hitIdx)
                    if (state.clips.count { it.selected } > 1) {
                        dragSnapshot = state.snapshotSelected()
                    }
                }
            }

            if (boxMode) {
                state.updateBoxSelect(priorSelected, Offset(downX, downY), c.position, w)
                c.consume()
                continue
            }

            if (dragStarted && hitIdx != null) {
                applySelectDrag(
                    state = state,
                    ctx = SelectDragCtx(hitIdx, isResize, singleOrigPos, singleOrigTrack, dragSnapshot),
                    currentX = c.position.x,
                    currentY = c.position.y,
                    downX = downX,
                    downY = downY,
                    w = w,
                )
                c.consume()
            }
        }

        if (boxMode) state.selectionRect = null

        if (tapCandidate) {
            if (hitIdx != null) {
                state.toggleSelAt(hitIdx)
                onCommit()
            }
        } else if (dragStarted && !boxMode) {
            onCommit()
        }
    }
}

/**
 * Select gesture drag handling. Extracted from PlaylistSelectGesture
 * to keep that file under the 150-LOC ceiling.
 */
internal data class SelectDragCtx(
    val hitIdx: Int,
    val isResize: Boolean,
    val singleOrigPos: Int,
    val singleOrigTrack: Int,
    val dragSnapshot: Map<Long, Pair<Int, Int>>?,
)

internal fun applySelectDrag(
    state: PlaylistState,
    ctx: SelectDragCtx,
    currentX: Float,
    currentY: Float,
    downX: Float,
    downY: Float,
    w: Float,
) {
    val dx = currentX - downX
    val dy = currentY - downY
    if (ctx.isResize) {
        val newLen = (state.tickAtX(currentX, w) - ctx.singleOrigPos).coerceAtLeast(48)
        state.clips[ctx.hitIdx] = state.clips[ctx.hitIdx].copy(length = newLen)
    } else if (ctx.dragSnapshot != null && ctx.dragSnapshot.size > 1) {
        val deltaPos = (dx / state.pxPerTick(w)).roundToInt()
        val deltaTrack = (dy / ROW_H).roundToInt()
        state.applyMultiDrag(ctx.dragSnapshot, deltaPos, deltaTrack)
    } else {
        val newPos = (ctx.singleOrigPos + (dx / state.pxPerTick(w)))
            .roundToInt().coerceAtLeast(0)
        val newTrack = (ctx.singleOrigTrack + (dy / ROW_H).roundToInt())
            .coerceIn(0, (state.trackCount - 1).coerceAtLeast(0))
        state.clips[ctx.hitIdx] = state.clips[ctx.hitIdx]
            .copy(position = newPos, track = newTrack)
    }
}
