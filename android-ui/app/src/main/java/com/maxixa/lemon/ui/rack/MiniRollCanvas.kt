package com.maxixa.lemon.ui.rack

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.maxixa.lemon.data.NoteView

private val OctaveLine = Color(0xFF2A2A35)

/**
 * Read-only miniature piano roll for one channel. Shows every note on the
 * channel scaled to the row's 40dp height. Step notes (length=0) are
 * invisible by design — they already live in the step grid.
 */
@Composable
fun MiniRollCanvas(
    notes: List<NoteView>,
    patternLength: Int,
    ppq: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (notes.isEmpty()) return@Canvas
        val minKey = notes.minOf { it.key }
        val maxKey = notes.maxOf { it.key }
        val keyRange = (maxKey - minKey + 1).coerceAtLeast(1)
        val totalTicks = patternLength.coerceAtLeast(ppq * 4).coerceAtLeast(1)

        // Octave separator lines
        for (k in minKey..maxKey) {
            if (k % 12 == 0) {
                val y = size.height * (1f - (k - minKey).toFloat() / keyRange)
                drawLine(
                    color = OctaveLine,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }
        }

        // Notes
        val rowH = (size.height / keyRange).coerceAtLeast(2f)
        for (n in notes) {
            if (n.length <= 0) continue  // skip step notes
            val x = (n.position.toFloat() / totalTicks) * size.width
            val w = (n.length.toFloat() / totalTicks) * size.width
            val y = size.height * (1f - (n.key - minKey).toFloat() / keyRange)
            drawRect(
                color = accent,
                topLeft = Offset(x, y),
                size = Size(w.coerceAtLeast(2f), rowH),
            )
        }
    }
}
