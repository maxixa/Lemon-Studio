package com.maxixa.lemon.ui.playlist

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

private const val HEADER_W = 90f
private const val ROW_H = 36f
private const val RESIZE_HANDLE = 20f
private const val FADE_HANDLE_H = 12f
private const val GAIN_KNOB_R = 6f
private const val GAIN_KNOB_TOP_PAD = 2f
private const val GAIN_HIT_R = 14f
private const val GAIN_DRAG_PX_PER_UNIT = 100f

/**
 * Audio edit tool — active when `state.trimMode == true`.
 *
 *   Top-center gain dot: vertical drag = clip gain (0..4, unity = 1)
 *   Top-left/right:      horizontal drag = fade length
 *   Mid-left/right edge: horizontal drag = window trim
 *   Dispatch to audio or automation math by `state.automationClipChannels`
 */
internal fun Modifier.trimGesture(
    state: PlaylistState,
    onCommit: () -> Unit,
): Modifier = pointerInput(state.trimMode) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val w = size.width.toFloat(); val h = size.height.toFloat()
        if (w <= 0f || h <= 0f) return@awaitEachGesture
        if (!state.trimMode) return@awaitEachGesture

        val hit = state.hitTest(down.position.x, down.position.y, w)
        val clipIdx = hit.first
        if (clipIdx == null || clipIdx !in state.clips.indices) return@awaitEachGesture
        val startClip = state.clips[clipIdx]
        if (startClip.sourceKind != 1) return@awaitEachGesture

        val ppx = state.pxPerTick(w)
        if (ppx <= 0f) return@awaitEachGesture

        val clipX = HEADER_W + (startClip.position - state.scrollX) * ppx
        val clipW = (startClip.length * ppx).coerceAtLeast(6f)
        val clipY = (startClip.track - state.scrollY) * ROW_H + 1f

        // Gain knob: top-center of wide clips
        if (clipW >= 40f) {
            val kx = clipX + clipW / 2f
            val ky = clipY + GAIN_KNOB_TOP_PAD + GAIN_KNOB_R
            if (abs(down.position.x - kx) <= GAIN_HIT_R &&
                abs(down.position.y - ky) <= GAIN_HIT_R) {
                handleGainDrag(state, clipIdx, startClip, down.position.y, onCommit)
                return@awaitEachGesture
            }
        }

        val isTop = down.position.y in clipY..(clipY + FADE_HANDLE_H)
        val isLeftEdge = down.position.x in (clipX - 8f)..(clipX + RESIZE_HANDLE)
        val isRightEdge = down.position.x in
            (clipX + clipW - RESIZE_HANDLE)..(clipX + clipW + 8f)

        val isAutomation = startClip.pattern in state.automationClipChannels

        if (isAutomation) {
            handleAutomationTrim(state, clipIdx, startClip, isTop, isLeftEdge, isRightEdge,
                down.position.x, w, onCommit)
        } else {
            handleAudioTrim(state, clipIdx, startClip, isTop, isLeftEdge, isRightEdge,
                down.position.x, w, onCommit)
        }
    }
}

/**
 * Clip gain drag — vertical, 100px per 1.0 gain unit, clamped 0..4.
 * Sets `state.gainDragClipId` for the duration so the render can highlight.
 */
private suspend fun AwaitPointerEventScope.handleGainDrag(
    state: PlaylistState,
    clipIdx: Int,
    startClip: ClipView,
    startY: Float,
    onCommit: () -> Unit,
) {
    state.gainDragClipId = startClip.id
    val startGain = startClip.gain
    while (true) {
        val ev = awaitPointerEvent(PointerEventPass.Main)
        val pressed = ev.changes.count { it.pressed }
        if (pressed == 0) break
        if (pressed >= 2) break
        val ch = ev.changes.firstOrNull { it.pressed } ?: break
        val dy = ch.position.y - startY
        val deltaGain = -dy / GAIN_DRAG_PX_PER_UNIT
        val newGain = (startGain + deltaGain).coerceIn(0f, 4f)
        state.clips[clipIdx] = startClip.copy(gain = newGain)
        ch.consume()
    }
    state.gainDragClipId = null
    onCommit()
}
