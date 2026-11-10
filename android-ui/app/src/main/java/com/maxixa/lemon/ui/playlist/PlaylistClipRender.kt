package com.maxixa.lemon.ui.playlist

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import com.maxixa.lemon.ui.theme.AccentAmber
import com.maxixa.lemon.ui.theme.AccentCyan
import com.maxixa.lemon.ui.theme.TrackColors

private const val HEADER_W = 90f
private const val ROW_H = 36f

/**
 * Per-clip rendering — extracted from PlaylistCanvas to keep that file
 * under the 150-LOC ceiling.
 */
internal fun DrawScope.drawPlaylistClips(
    state: PlaylistState,
    audioClipPeaks: Map<Int, FloatArray>,
    automationPoints: Map<Int, List<Pair<Double, Double>>>,
    pxPerTick: Float,
    textMeasurer: TextMeasurer,
) {
    for (c in state.clips) {
        val x = HEADER_W + ((c.position - state.scrollX) * pxPerTick)
        val w = (c.length * pxPerTick).coerceAtLeast(6f)
        val y = (c.track - state.scrollY) * ROW_H
        val h = ROW_H - 2f
        if (y + h < 0 || y > size.height) continue
        if (x + w < HEADER_W || x > size.width) continue
        val color = when {
            c.selected -> AccentCyan
            c.sourceKind == 1 -> AccentAmber
            else -> TrackColors[(c.pattern - 1).coerceAtLeast(0) % TrackColors.size]
        }
        drawRect(color.copy(alpha = if (c.muted) 0.35f else 0.85f),
            Offset(x, y + 1f), Size(w, h))
        drawRect(color, Offset(x + w - 4f, y + 1f), Size(4f, h))
        if (c.sourceKind == 1 && w > 12f) {
            val autoPts = automationPoints[c.pattern]
            if (autoPts != null && autoPts.isNotEmpty()) {
                val fromBeat = c.windowLo.toDouble()
                val clipBeats = c.length.toDouble() / state.ppq
                drawAutomationCurve(
                    points = autoPts,
                    fromBeat = fromBeat,
                    clipBeats = clipBeats,
                    x = x + 1f, y = y + 1f,
                    w = (w - 6f).coerceAtLeast(0f), h = h,
                )
            } else {
                val peaks = audioClipPeaks[c.pattern]
                if (peaks != null && peaks.isNotEmpty()) {
                    drawAudioWaveform(
                        peaks = peaks,
                        x = x + 1f, y = y + 1f,
                        w = (w - 6f).coerceAtLeast(0f), h = h,
                        color = Color.White.copy(alpha = 0.4f),
                    )
                }
            }
            drawGainIndicator(c, x, y + 1f, w, h, textMeasurer)
        }
        if (c.sourceKind == 0 && w > 8f) {
            drawPatternGrid(x + 1f, y + 1f, (w - 6f).coerceAtLeast(0f), h,
                pxPerTick, state.ppq, state.barTicks(), Color.White.copy(alpha = 0.18f))
        }
        if (c.sourceKind == 1) {
            drawFadeCurves(c, x, y + 1f, w, h, state.bpm, state.ppq)
            if (state.trimMode) {
                drawFadeHandles(c, x, y + 1f, w, h)
                drawTrimHandles(c, x, y + 1f, w, h)
                if (state.fadeDragClipId == c.id) {
                    drawFadeDragOverlay(c, x, y + 1f, w, h,
                        state.bpm, state.ppq, state.fadeDragIsIn)
                }
            }
        }
        if (c.selected) {
            drawRect(Color.White, Offset(x, y + 1f), Size(w, h), style = Stroke(2f))
        }
    }
}

/**
 * Draw a mini waveform inside an audio clip's rect. Peaks are `channel_peaks`
 * for the clip's source channel, already normalized to 0..1 by the engine's
 * `downsample_peaks`. Centered vertically so it reads like an FL audio clip.
 *
 * Skips amp < 0.005 columns — reduces draw-call count on long clips without
 * visible gaps (0.5% of half-height is < 0.25 dp at typical 40 dp row).
 */
internal fun DrawScope.drawAudioWaveform(
    peaks: FloatArray,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    color: Color,
) {
    if (peaks.isEmpty() || w <= 4f || h <= 4f) return
    val pad = 2f
    val innerY = y + pad
    val innerH = h - 2 * pad
    if (innerH <= 0f) return
    val centerY = innerY + innerH / 2f
    val halfH = innerH / 2f
    val barW = w / peaks.size
    val colW = barW.coerceAtLeast(1f)
    for (i in peaks.indices) {
        val amp = peaks[i].coerceIn(0f, 1f)
        if (amp < 0.005f) continue
        val colH = amp * halfH
        val colX = x + i * barW
        if (colX + colW < x || colX > x + w) continue
        drawRect(
            color = color,
            topLeft = Offset(colX, centerY - colH),
            size = Size(colW, colH * 2f),
        )
    }
}
