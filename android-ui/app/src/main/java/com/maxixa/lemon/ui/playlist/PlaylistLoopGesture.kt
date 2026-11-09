package com.maxixa.lemon.ui.playlist

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass

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
