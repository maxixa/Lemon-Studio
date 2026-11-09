package com.maxixa.lemon.ui.playlist

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass

/**
 * Automation clip trim — window in BEATS (spec: Clip.window for automation
 * source is in beats). Fixes the D5 ship's unit bug where automation trim
 * reused the audio path's milliseconds math on beat-valued windowLo/Hi.
 *
 * For a freshly created automation clip (add_automation_channel), windowLo
 * and windowHi default to 0 — the engine falls back to `from_beat = 0.0`
 * with `clipBeats = length / ppq`. Any drag here sets a real window in
 * beats, and the engine honors it via AutoClip.from_beat.
 *
 *   top-left corner  : fade-in drag (ms; fades on automation preserved)
 *   top-right corner : fade-out drag (ms)
 *   left edge mid    : trim window start (beats)
 *   right edge mid   : trim window end (beats)
 */
internal suspend fun AwaitPointerEventScope.handleAutomationTrim(
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
    val ppq = state.ppq.coerceAtLeast(1)
    val bpm = state.bpm.coerceAtLeast(1f)
    val msPerTick = 60000.0 / (bpm.toDouble() * ppq.toDouble())
    val pxPerMs = (ppx / msPerTick.toFloat()).coerceAtLeast(0.0001f)

    // Automation windows are in beats. If no window, fall back to the whole
    // clip: [0, lengthTicks / ppq].
    val startLo = if (startClip.windowHi > 0f || startClip.windowLo > 0f) startClip.windowLo else 0f
    val startHi = if (startClip.windowHi > 0f) startClip.windowHi
                  else (startClip.length.toFloat() / ppq)
    val clipBeats = (startHi - startLo).coerceAtLeast(0.01f)
    val startFadeIn = startClip.fadeInMs
    val startFadeOut = startClip.fadeOutMs
    val clipMs = clipBeats * ppq * msPerTick.toFloat()

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
        val deltaTicks = (dx / ppx).toInt()
        val deltaBeats = deltaTicks.toFloat() / ppq

        when {
            fadeInHandle -> {
                val deltaMs = dx / pxPerMs
                val newFadeIn = (startFadeIn + deltaMs).coerceIn(0f, clipMs)
                state.clips[clipIdx] = startClip.copy(fadeInMs = newFadeIn)
            }
            fadeOutHandle -> {
                val deltaMs = dx / pxPerMs
                val newFadeOut = (startFadeOut - deltaMs).coerceIn(0f, clipMs)
                state.clips[clipIdx] = startClip.copy(fadeOutMs = newFadeOut)
            }
            trimStart -> {
                val newLo = (startLo + deltaBeats).coerceIn(0f, (startHi - 0.01f).coerceAtLeast(0f))
                val shiftBeats = newLo - startLo
                val shiftTicks = (shiftBeats * ppq).toInt()
                val newPos = (startClip.position + shiftTicks).coerceAtLeast(0)
                val newLen = ((startHi - newLo) * ppq).toInt().coerceAtLeast(24)
                state.clips[clipIdx] = startClip.copy(
                    position = newPos, length = newLen,
                    windowLo = newLo, windowHi = startHi,
                )
            }
            trimEnd -> {
                val newHi = (startHi + deltaBeats).coerceAtLeast(startLo + 0.01f)
                val newLen = ((newHi - startLo) * ppq).toInt().coerceAtLeast(24)
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
