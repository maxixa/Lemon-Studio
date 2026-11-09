package com.maxixa.lemon.ui.playlist

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass

/**
 * Audio clip trim — window in MILLISECONDS (spec: Clip.window for audio
 * source is in ms). Extracted from PlaylistTrimGesture during the audio
 * clip trim ship; behavior unchanged from D5's audio path.
 *
 *   top-left corner  : fade-in drag (ms)
 *   top-right corner : fade-out drag (ms)
 *   left edge mid    : trim window start, position advances, length shrinks
 *   right edge mid   : trim window end, length shrinks
 */
internal suspend fun AwaitPointerEventScope.handleAudioTrim(
    state: PlaylistState,
    clipIdx: Int,
    startClip: ClipView,
    isTop: Boolean,
    isLeftEdge: Boolean,
    isRightEdge: Boolean,
    startX: Float,
    w: Float,
    onCommit: () -> Unit,
) {
    val ppx = state.pxPerTick(w)
    val bpm = state.bpm.coerceAtLeast(1f)
    val ppq = state.ppq.coerceAtLeast(1)
    val msPerTick = 60000.0 / (bpm.toDouble() * ppq.toDouble())
    val pxPerMs = (ppx / msPerTick.toFloat()).coerceAtLeast(0.0001f)

    // Fallback: use the clip's own length in ms if the sample duration
    // is unknown (FLP import with missing sample path). Prevents fade
    // from being dragged arbitrarily long when the duration map is empty.
    val maxMs = state.audioClipDurations[startClip.pattern]
        ?: (startClip.length.toFloat() * msPerTick.toFloat()).coerceAtLeast(100f)
    val startLo = if (startClip.windowHi > 0f || startClip.windowLo > 0f) startClip.windowLo else 0f
    val startHi = if (startClip.windowHi > 0f) startClip.windowHi else maxMs
    val clipMs = (startHi - startLo).coerceAtLeast(1f)
    val startFadeIn = startClip.fadeInMs
    val startFadeOut = startClip.fadeOutMs

    val fadeInHandle = isTop && isLeftEdge
    val fadeOutHandle = isTop && isRightEdge
    val trimStart = !isTop && isLeftEdge
    val trimEnd = !isTop && isRightEdge
    if (!fadeInHandle && !fadeOutHandle && !trimStart && !trimEnd) return

    val isFadeDrag = fadeInHandle || fadeOutHandle
    if (isFadeDrag) {
        state.fadeDragClipId = startClip.id
        state.fadeDragIsIn = fadeInHandle
    }

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
                val newLo = (startLo + deltaMs).coerceIn(0f, (startHi - 10f).coerceAtLeast(0f))
                val shiftMs = newLo - startLo
                val shiftTicks = (shiftMs * msPerTick).toInt()
                val newPos = (startClip.position + shiftTicks).coerceAtLeast(0)
                val newLen = ((startHi - newLo) * msPerTick).toInt().coerceAtLeast(24)
                state.clips[clipIdx] = startClip.copy(
                    position = newPos, length = newLen,
                    windowLo = newLo, windowHi = startHi,
                )
            }
            trimEnd -> {
                val newHi = (startHi + deltaMs).coerceIn((startLo + 10f), maxMs)
                val newLen = ((newHi - startLo) * msPerTick).toInt().coerceAtLeast(24)
                state.clips[clipIdx] = startClip.copy(
                    length = newLen, windowLo = startLo, windowHi = newHi,
                )
            }
        }
        ch.consume()
    }
    if (isFadeDrag) state.fadeDragClipId = null
    onCommit()
}
