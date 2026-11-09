package com.maxixa.lemon.ui.playlist

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

private val FadeLineColor = Color(0xFFFFFFFF).copy(alpha = 0.55f)
private val HandleColor = Color(0xFF00E5FF).copy(alpha = 0.55f)
private val TrimNubColor = Color(0xFF00E5FF)

/**
 * Draw fade-in/out diagonal lines inside an audio clip's rect.
 * Fade lengths are in milliseconds and scale to clip width via ms-per-pixel.
 */
internal fun DrawScope.drawFadeCurves(
    c: ClipView, x: Float, y: Float, w: Float, h: Float, bpm: Float, ppq: Int,
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
        drawLine(FadeLineColor, Offset(x, y + h), Offset(x + fadeInPx, y), 1.5f)
    }
    if (fadeOutPx > 1f) {
        drawLine(FadeLineColor, Offset(x + w - fadeOutPx, y), Offset(x + w, y + h), 1.5f)
    }
}

/**
 * Small cyan triangles at top-left and top-right corners of a sourceKind==1
 * clip — the fade drag handles. Shown only in trim mode.
 */
internal fun DrawScope.drawFadeHandles(
    c: ClipView, x: Float, y: Float, w: Float, h: Float,
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

/**
 * Vertical cyan nubs at left/right mid-edges — the trim drag handles.
 * Distinct from fade corner triangles so users can tell trim (mid) from
 * fade (top). Shown only in trim mode.
 */
internal fun DrawScope.drawTrimHandles(
    c: ClipView, x: Float, y: Float, w: Float, h: Float,
) {
    if (c.sourceKind != 1) return
    if (w <= 16f || h <= 10f) return
    val nubW = 3f
    val nubH = 10f
    val nubY = y + (h - nubH) / 2f
    drawRect(TrimNubColor, Offset(x, nubY), Size(nubW, nubH))
    drawRect(TrimNubColor, Offset(x + w - nubW, nubY), Size(nubW, nubH))
}

/**
 * Filled cyan overlay on the fade region during a fade drag.
 * `isIn` picks which side (in or out). Reads the fade length from the
 * live-updated `ClipView` (fadeInMs/fadeOutMs already reflect the drag).
 */
internal fun DrawScope.drawFadeDragOverlay(
    c: ClipView, x: Float, y: Float, w: Float, h: Float,
    bpm: Float, ppq: Int, isIn: Boolean,
) {
    if (c.sourceKind != 1) return
    val fadeMs = if (isIn) c.fadeInMs else c.fadeOutMs
    if (fadeMs <= 0f) return
    if (bpm <= 0f || ppq <= 0) return
    if (w <= 4f || h <= 4f) return

    val msPerTick = 60000.0 / (bpm.toDouble() * ppq.toDouble())
    val clipMs = c.length * msPerTick
    if (clipMs <= 0.0) return

    val pxPerMs = (w / clipMs.toFloat()).coerceAtLeast(0f)
    val fadePx = (fadeMs * pxPerMs).coerceIn(0f, w)
    if (fadePx < 1f) return

    val overlay = Color(0xFF00E5FF).copy(alpha = 0.25f)
    if (isIn) {
        val path = Path().apply {
            moveTo(x, y + h)
            lineTo(x + fadePx, y)
            lineTo(x, y)
            close()
        }
        drawPath(path, overlay)
    } else {
        val path = Path().apply {
            moveTo(x + w - fadePx, y)
            lineTo(x + w, y)
            lineTo(x + w, y + h)
            close()
        }
        drawPath(path, overlay)
    }
}
