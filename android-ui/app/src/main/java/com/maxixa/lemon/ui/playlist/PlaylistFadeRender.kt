package com.maxixa.lemon.ui.playlist

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

private val FadeLineColor = Color(0xFFFFFFFF).copy(alpha = 0.55f)
private val HandleColor = Color(0xFF00E5FF).copy(alpha = 0.55f)

/**
 * Draw fade-in/out diagonal lines inside an audio clip's rect.
 * Fade lengths are in milliseconds (from ClipView.fadeInMs/fadeOutMs) and
 * scale to the clip's on-screen width via the clip's ms-per-pixel.
 *
 * Only draws for audio clips (sourceKind == 1). Pattern clips skip.
 */
internal fun DrawScope.drawFadeCurves(
    c: ClipView,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    bpm: Float,
    ppq: Int,
) {
    if (c.sourceKind != 1) return
    if (c.fadeInMs <= 0f && c.fadeOutMs <= 0f) return
    if (bpm <= 0f || ppq <= 0) return
    if (w <= 4f || h <= 4f) return

    val msPerTick = 60000.0 / (bpm.toDouble() * ppq.toDouble())
    val clipMs = c.length * msPerTick
    if (clipMs <= 0.0) return

    val pxPerMs = (w / clipMs.toFloat()).coerceAtLeast(0f)
    val fadeInPx = (c.fadeInMs * pxPerMs).coerceIn(0f, w)
    val fadeOutPx = (c.fadeOutMs * pxPerMs).coerceIn(0f, w)

    if (fadeInPx > 1f) {
        drawLine(
            color = FadeLineColor,
            start = Offset(x, y + h),
            end = Offset(x + fadeInPx, y),
            strokeWidth = 1.5f,
        )
    }
    if (fadeOutPx > 1f) {
        drawLine(
            color = FadeLineColor,
            start = Offset(x + w - fadeOutPx, y),
            end = Offset(x + w, y + h),
            strokeWidth = 1.5f,
        )
    }
}

/**
 * Small cyan triangles at top-left and top-right corners of an audio clip,
 * shown only in Trim mode to hint that the top corners are fade drag handles.
 */
internal fun DrawScope.drawFadeHandles(
    c: ClipView,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
) {
    if (c.sourceKind != 1) return
    if (w <= 16f || h <= 8f) return
    val sz = 6f
    val pLeft = Path().apply {
        moveTo(x, y)
        lineTo(x + sz, y)
        lineTo(x, y + sz)
        close()
    }
    drawPath(pLeft, HandleColor)
    val pRight = Path().apply {
        moveTo(x + w, y)
        lineTo(x + w - sz, y)
        lineTo(x + w, y + sz)
        close()
    }
    drawPath(pRight, HandleColor)
}
