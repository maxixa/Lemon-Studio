package com.maxixa.lemon.ui.playlist

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

private const val HEADER_W = 90f
private const val ROW_H = 36f
private const val RESIZE_HANDLE = 20f
private const val FADE_HANDLE_H = 12f

/**
 * Audio edit tool — active when `state.trimMode == true`. Only audio
 * clips (sourceKind == 1) respond. Pattern clips no-op.
 *
 *   Top-left corner (y < FADE_HANDLE_H from clip top, near left edge):
 *       drag right = longer fade-in
 *   Top-right corner:
 *       drag left = longer fade-out
 *   Left edge (below y=12px):
 *       drag right = trim in (position advances, audio stays put)
 *   Right edge (below y=12px):
 *       drag left = trim out (audio starts at start, ends sooner)
 *
 * All values clamp to sample duration. Live update during drag; commit
 * once on release so one undo reverts the whole gesture.
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
        if (clipIdx == null || clipIdx !in state.clips.indices) {
            return@awaitEachGesture
        }
        val startClip = state.clips[clipIdx]
        if (startClip.sourceKind != 1) return@awaitEachGesture

        val ppx = state.pxPerTick(w)
        if (ppx <= 0f) return@awaitEachGesture

        val clipX = HEADER_W + (startClip.position - state.scrollX) * ppx
        val clipW = (startClip.length * ppx).coerceAtLeast(6f)
        val clipY = (startClip.track - state.scrollY) * ROW_H + 1f

        val isTop = down.position.y in clipY..(clipY + FADE_HANDLE_H)
        val isLeftEdge = down.position.x in (clipX - 8f)..(clipX + RESIZE_HANDLE)
        val isRightEdge = down.position.x in
            (clipX + clipW - RESIZE_HANDLE)..(clipX + clipW + 8f)

        val fadeInHandle = isTop && isLeftEdge
        val fadeOutHandle = isTop && isRightEdge
        val trimStart = !isTop && isLeftEdge
        val trimEnd = !isTop && isRightEdge

        if (!fadeInHandle && !fadeOutHandle && !trimStart && !trimEnd) {
            return@awaitEachGesture
        }

        val bpm = state.bpm.coerceAtLeast(1f)
        val ppq = state.ppq.coerceAtLeast(1)
        val msPerTick = 60000.0 / (bpm.toDouble() * ppq.toDouble())
        val pxPerMs = (ppx / msPerTick.toFloat()).coerceAtLeast(0.0001f)

        val maxMs = state.audioClipDurations[startClip.pattern] ?: Float.MAX_VALUE
        val startLo = if (startClip.windowHi > 0f || startClip.windowLo > 0f)
            startClip.windowLo else 0f
        val startHi = if (startClip.windowHi > 0f) startClip.windowHi else maxMs
        val clipMs = (startHi - startLo).coerceAtLeast(1f)
        val startFadeIn = startClip.fadeInMs
        val startFadeOut = startClip.fadeOutMs
        val startX = down.position.x

        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Main)
            val pressed = ev.changes.count { it.pressed }
            if (pressed == 0) break
            if (pressed >= 2) break

            val ch = ev.changes.firstOrNull { it.pressed } ?: break
            val dx = ch.position.x - startX
            val deltaMs = dx / pxPerMs

            when {
                fadeInHandle -> {
                    val newFadeIn = (startFadeIn + deltaMs).coerceIn(0f, clipMs)
                    state.clips[clipIdx] = startClip.copy(fadeInMs = newFadeIn)
                }
                fadeOutHandle -> {
                    val newFadeOut = (startFadeOut - deltaMs).coerceIn(0f, clipMs)
                    state.clips[clipIdx] = startClip.copy(fadeOutMs = newFadeOut)
                }
                trimStart -> {
                    val newLo = (startLo + deltaMs)
                        .coerceIn(0f, (startHi - 10f).coerceAtLeast(0f))
                    val shiftMs = newLo - startLo
                    val shiftTicks = (shiftMs * msPerTick).toInt()
                    val newPos = (startClip.position + shiftTicks).coerceAtLeast(0)
                    val newLen = ((startHi - newLo) * msPerTick).toInt().coerceAtLeast(24)
                    state.clips[clipIdx] = startClip.copy(
                        position = newPos,
                        length = newLen,
                        windowLo = newLo,
                        windowHi = startHi,
                    )
                }
                trimEnd -> {
                    val newHi = (startHi + deltaMs).coerceIn((startLo + 10f), maxMs)
                    val newLen = ((newHi - startLo) * msPerTick).toInt().coerceAtLeast(24)
                    state.clips[clipIdx] = startClip.copy(
                        length = newLen,
                        windowLo = startLo,
                        windowHi = newHi,
                    )
                }
            }
            ch.consume()
        }

        onCommit()
    }
}
