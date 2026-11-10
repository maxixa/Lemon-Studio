package com.maxixa.lemon.ui.playlist

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val MarkerColor = Color(0xFFE0B050)

private const val FLAG_W = 16f
private const val FLAG_H = 14f

/**
 * Draw markers as amber vertical lines + numbered flags at the top of the
 * canvas. Called after clips so flags stay visible above everything.
 */
internal fun DrawScope.drawMarkers(
    markers: List<MarkerView>,
    scrollX: Float,
    pxPerTick: Float,
    headerW: Float,
    textMeasurer: TextMeasurer,
) {
    if (markers.isEmpty()) return
    for ((i, m) in markers.withIndex()) {
        val mx = headerW + (m.position - scrollX) * pxPerTick
        if (mx < headerW - FLAG_W || mx > size.width + FLAG_W) continue

        // Vertical line
        drawRect(
            color = MarkerColor.copy(alpha = 0.55f),
            topLeft = Offset(mx - 0.5f, 0f),
            size = Size(1.5f, size.height),
        )

        // Flag block
        val flagLeft = (mx - FLAG_W / 2f)
            .coerceAtLeast(headerW)
            .coerceAtMost((size.width - FLAG_W).coerceAtLeast(headerW))
        drawRect(
            color = MarkerColor,
            topLeft = Offset(flagLeft, 0f),
            size = Size(FLAG_W, FLAG_H),
        )

        // Index number
        val layout = textMeasurer.measure(
            "${i + 1}",
            style = TextStyle(
                color = Color(0xFF15151A),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        drawText(
            layout,
            topLeft = Offset(
                flagLeft + (FLAG_W - layout.size.width) / 2f,
                (FLAG_H - layout.size.height) / 2f,
            ),
        )
    }
}

private const val HEADER_W = 90f
private const val HIT_W = 10f

/**
 * Return the index of a marker whose flag was tapped at (x, y), or -1.
 * Only taps in the top flag strip (y <= FLAG_H) count, so clip taps on
 * the same row still hit clips.
 */
internal fun PlaylistState.hitTestMarker(x: Float, y: Float, w: Float): Int {
    if (y > FLAG_H) return -1
    if (markers.isEmpty()) return -1
    val ppx = pxPerTick(w)
    if (ppx <= 0f) return -1
    for (i in markers.indices.reversed()) {
        val m = markers[i]
        val mx = HEADER_W + (m.position - scrollX) * ppx
        if (x in (mx - HIT_W)..(mx + HIT_W)) return i
    }
    return -1
}
