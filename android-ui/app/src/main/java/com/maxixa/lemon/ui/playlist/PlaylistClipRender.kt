package com.maxixa.lemon.ui.playlist

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.maxixa.lemon.ui.theme.AccentAmber
import com.maxixa.lemon.ui.theme.AccentCyan
import com.maxixa.lemon.ui.theme.TrackColors

private const val HEADER_W = 90f
private const val ROW_H = 36f

/**
 * Per-clip rendering — extracted from PlaylistCanvas to keep that file
 * under the 150-LOC ceiling. Loop body is verbatim from the source
 * file (brace-counted extraction).
 */
internal fun DrawScope.drawPlaylistClips(
    state: PlaylistState,
    audioClipPeaks: Map<Int, FloatArray>,
    automationPoints: Map<Int, List<Pair<Double, Double>>>,
    pxPerTick: Float,
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
                    x = x + 1f,
                    y = y + 1f,
                    w = (w - 6f).coerceAtLeast(0f),
                    h = h,
                )
            } else {
                val peaks = audioClipPeaks[c.pattern]
                if (peaks != null && peaks.isNotEmpty()) {
                    drawAudioWaveform(
                        peaks = peaks,
                        x = x + 1f,
                        y = y + 1f,
                        w = (w - 6f).coerceAtLeast(0f),
                        h = h,
                        color = Color.White.copy(alpha = 0.4f),
                    )
                }
            }
        }
        if (c.sourceKind == 1) {
            drawFadeCurves(c, x, y + 1f, w, h, state.bpm, state.ppq)
            if (state.trimMode) drawFadeHandles(c, x, y + 1f, w, h)
        }
        if (c.selected) {
            drawRect(Color.White, Offset(x, y + 1f), Size(w, h), style = Stroke(2f))
        }

    }
}
