package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.maxixa.lemon.state.*
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val RESIZE_HANDLE_PX = 24f
internal const val DRAG_THRESHOLD = 12f
internal const val LONG_PRESS_MS = 500L
internal const val RULER_HEIGHT_PX = 16f

/**
 * Values captured at touch-down, consumed by per-tool handlers.
 * The state machine stays in the handler; only these fixed inputs
 * are shared.
 */
internal data class GestureSetup(
    val g: Grid,
    val downTick: Int,
    val downKey: Int,
    val noteIdx: Int,
    val resize: Boolean,
    val downPosition: Offset,
    val downTime: Long,
)

/**
 * Piano roll gesture dispatcher. Handles:
 *   - Ruler strip tap/drag → onSeekTick
 *   - Pan tool → 2-finger pan only (no single-touch action)
 *   - Pencil / Eraser → PianoRollDrawHandler
 *   - Select → PianoRollSelectHandler
 *
 * Each handler runs its own event loop until release.
 */
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

        // Ruler seek (early return)
        if (down.position.y < RULER_HEIGHT_PX && down.position.x >= KEY_LEFT_PAD_PX) {
            onSeekTick(g.tickOfX(down.position.x - KEY_LEFT_PAD_PX).toInt().coerceAtLeast(0))
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Main)
                if (ev.changes.count { it.pressed } == 0) break
                val ch = ev.changes.firstOrNull { it.pressed } ?: break
                onSeekTick(g.tickOfX(ch.position.x - KEY_LEFT_PAD_PX).toInt().coerceAtLeast(0))
                ch.consume()
            }
            return@awaitEachGesture
        }

        // Pan tool — 2-finger only, single-touch is a no-op
        if (state.tool == Tool.Pan) {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Main)
                if (ev.changes.count { it.pressed } == 0) break
                if (ev.changes.count { it.pressed } >= 2) {
                    applyMultiTouchPan(state, ev, g)
                }
            }
            return@awaitEachGesture
        }

        val downTick = state.snap(g.tickOfX(down.position.x - KEY_LEFT_PAD_PX))
        val downKey = g.keyOfY(down.position.y).coerceIn(0, 127)

        var noteIdx = -1
        var resize = false
        noteIdx = state.hitTest(down.position.x, down.position.y, g)
        if (noteIdx >= 0 && (state.tool == Tool.Select || state.tool == Tool.Pencil)) {
            val n = state.notes[noteIdx]
            val endX = KEY_LEFT_PAD_PX + g.xOfTick((n.position + n.length).toFloat())
            resize = abs(down.position.x - endX) < RESIZE_HANDLE_PX
        }

        val setup = GestureSetup(
            g = g,
            downTick = downTick,
            downKey = downKey,
            noteIdx = noteIdx,
            resize = resize,
            downPosition = down.position,
            downTime = System.currentTimeMillis(),
        )

        when (state.tool) {
            Tool.Pencil, Tool.Eraser -> handleDrawGesture(state, setup, onCommit)
            Tool.Select -> handleSelectGesture(state, setup, onCommit)
            Tool.Pan -> {} // unreachable, handled above
        }
    }
}

/** Shared 2-finger pan. Callers gate on `pressed >= 2` before invoking. */
internal fun applyMultiTouchPan(
    state: PianoRollState,
    event: PointerEvent,
    g: Grid,
) {
    val pan = event.calculatePan()
    state.scrollX = (state.scrollX - pan.x / g.pxPerTick).coerceAtLeast(0f)
    state.scrollY = (state.scrollY + (pan.y / g.pxPerRow).roundToInt()).coerceIn(0, 127)
    event.changes.forEach { it.consume() }
}
