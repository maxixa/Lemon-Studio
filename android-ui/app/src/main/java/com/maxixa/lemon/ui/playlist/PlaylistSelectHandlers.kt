package com.maxixa.lemon.ui.playlist

import kotlin.math.roundToInt

private const val ROW_H = 36f

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
