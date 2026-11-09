package com.maxixa.lemon.ui.playlist

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ROW_H = 36f
private const val DRAG_THRESHOLD = 12f
private const val TAG = "LemonPlPencil"

/**
 * Playlist pencil tool — byte-for-byte behavior of the pre-PL1 gesture:
 * drag empty space creates a clip; drag a clip moves it; drag its right
 * edge resizes; two fingers pan the viewport.
 */
internal fun Modifier.pencilGesture(
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
        Log.d(TAG, "DOWN x=${down.position.x.toInt()} y=${down.position.y.toInt()} hit=$hit")

        var panning = false
        var dragStarted = false
        var tapCandidate = true
        var longPressFired = false
        val downTime = System.currentTimeMillis()
        var clipIdx = hit.first
        val resize = hit.second
        val startClip = clipIdx?.let { state.clips[it] }
        val startX = down.position.x
        val startY = down.position.y

        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Main)
            val pressed = ev.changes.count { it.pressed }
            if (pressed == 0) break

            if (pressed >= 2) {
                if (!panning) { Log.d(TAG, "PAN"); panning = true; tapCandidate = false }
                val pan = ev.calculatePan()
                state.scrollX = (state.scrollX - pan.x / state.pxPerTick(w)).coerceAtLeast(0f)
                state.scrollY = (state.scrollY - (pan.y / ROW_H).roundToInt())
                    .coerceIn(0, (state.trackCount - 1).coerceAtLeast(0))
                ev.changes.forEach { it.consume() }
                continue
            }
            if (panning) continue

            val c = ev.changes.firstOrNull { it.pressed } ?: break
            val dx = c.position.x - startX
            val dy = c.position.y - startY

            if (!longPressFired && !dragStarted && tapCandidate && clipIdx != null
                && clipIdx in state.clips.indices
                && System.currentTimeMillis() - downTime >= 500L
            ) {
                val c0 = state.clips[clipIdx]
                if (c0.sourceKind == 1 && c0.pattern in state.automationClipChannels) {
                    state.automationEditorChannelIdx = c0.pattern
                    longPressFired = true
                    tapCandidate = false
                    break
                }
            }

            if (!dragStarted && (abs(dx) > DRAG_THRESHOLD || abs(dy) > DRAG_THRESHOLD)) {
                dragStarted = true; tapCandidate = false
                if (clipIdx == null) {
                    val startTick = state.tickAtX(startX, w)
                    val track = state.trackAtY(startY, h)
                    clipIdx = state.addClip(track, startTick, 384, state.defaultPattern.coerceAtLeast(1))
                    Log.d(TAG, "DRAW_NEW clip=$clipIdx")
                }
            }

            if (dragStarted && clipIdx != null) {
                if (startClip != null) {
                    val cur = state.clips[clipIdx]
                    if (resize) {
                        val newLen = (state.tickAtX(c.position.x, w) - startClip.position)
                            .coerceAtLeast(48)
                        state.clips[clipIdx] = cur.copy(length = newLen)
                    } else {
                        val newPos = (startClip.position + (c.position.x - startX) / state.pxPerTick(w))
                            .roundToInt().coerceAtLeast(0)
                        val newTrack = (startClip.track + dy / ROW_H).roundToInt()
                            .coerceIn(0, (state.trackCount - 1).coerceAtLeast(0))
                        state.clips[clipIdx] = cur.copy(position = newPos, track = newTrack)
                    }
                } else {
                    val cur = state.clips[clipIdx]
                    val newLen = (state.tickAtX(c.position.x, w) - cur.position).coerceAtLeast(48)
                    state.clips[clipIdx] = cur.copy(length = newLen)
                }
                c.consume()
            }
        }

        if (tapCandidate) {
            Log.d(TAG, "TAP clipIdx=$clipIdx")
            for (i in state.clips.indices) {
                val want = (i == clipIdx) && !state.clips[i].selected
                if (state.clips[i].selected != want) {
                    state.clips[i] = state.clips[i].copy(selected = want)
                }
            }
            if (clipIdx != null) onCommit()
        } else if (dragStarted && clipIdx != null) {
            Log.d(TAG, "COMMIT")
            onCommit()
        }
    }
}
