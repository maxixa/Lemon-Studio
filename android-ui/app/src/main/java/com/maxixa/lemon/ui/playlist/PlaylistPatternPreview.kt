package com.maxixa.lemon.ui.playlist

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Placeholder grid inside a pattern-source clip.
 *
 * Draws beat lines (every quarter note) + bar lines (brighter) so the clip
 * visibly has rhythmic structure — currently pattern clips render as an empty
 * coloured block, which is indistinguishable from a broken clip.
 *
 * Real note rectangles require a Rust ship (`engineAllPatternNotes` JNI —
 * currently only `engineNotesBytes` = current pattern is exposed). This file
 * is the intended home for that follow-up: `drawPatternNotes()` will sit
 * alongside `drawPatternGrid()` once the wire exists.
 */
internal fun DrawScope.drawPatternGrid(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    pxPerTick: Float,
    ppq: Int,
    barTicks: Int,
    color: Color,
) {
    if (w <= 4f || h <= 4f) return
    if (pxPerTick <= 0f || ppq <= 0 || barTicks <= 0) return

    val pxPerBeat = ppq * pxPerTick
    val pxPerBar  = barTicks * pxPerTick

    // Beat lines — visible if spacing ≥ 3dp, capped to 512 lines
    if (pxPerBeat >= 3f) {
        val n = (w / pxPerBeat).toInt().coerceAtMost(512)
        for (i in 1 until n) {
            val bx = x + i * pxPerBeat
            if (bx > x + w) break
            drawLine(
                color = color.copy(alpha = 0.10f),
                start = Offset(bx, y),
                end   = Offset(bx, y + h),
                strokeWidth = 1f,
            )
        }
    }

    // Bar lines — brighter, visible if spacing ≥ 4dp
    if (pxPerBar >= 4f) {
        val n = (w / pxPerBar).toInt().coerceAtMost(256)
        for (i in 1..n) {
            val bx = x + i * pxPerBar
            if (bx > x + w) break
            drawLine(
                color = color.copy(alpha = 0.30f),
                start = Offset(bx, y),
                end   = Offset(bx, y + h),
                strokeWidth = 1.5f,
            )
        }
    }
}
