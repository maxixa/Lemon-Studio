package com.maxixa.lemon.ui.playlist

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

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
