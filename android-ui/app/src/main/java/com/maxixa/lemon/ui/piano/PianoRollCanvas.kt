package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import com.maxixa.lemon.state.*
import com.maxixa.lemon.ui.theme.AccentCyan
import com.maxixa.lemon.ui.theme.AccentOrange
import com.maxixa.lemon.ui.theme.BorderSubtle

private val RowBlack   = Color(0xFF0E0E12)
private val RowWhite   = Color(0xFF1B1B22)
private val OctaveLine = Color(0xFF3A3A4A)
private val BeatLine   = Color(0xFF24242E)
private val SelectionRing = Color(0xFFFFFFFF)
private val PlayheadColor = Color(0xFFFF3B5C)
private val BLACK_KEYS = intArrayOf(1, 3, 6, 8, 10)

@Composable
fun PianoRollCanvas(
    state: PianoRollState,
    playheadTicks: Int,
    onCommit: () -> Unit,
    onSeekTick: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(
        modifier
            .fillMaxSize()
            .pianoRollGestures(state, onCommit, onSeekTick)
    ) {
        val pxPerRow  = size.height / state.vRows
        val gridW     = size.width - KEY_LEFT_PAD_PX
        val pxPerTick = gridW / (state.hBeats * state.ppq)

        // 1. Alternating row backgrounds
        for (row in 0 until state.vRows) {
            val key = state.scrollY + state.vRows - 1 - row
            val isBlack = key % 12 in BLACK_KEYS
            drawRect(
                color = if (isBlack) RowBlack else RowWhite,
                topLeft = Offset(KEY_LEFT_PAD_PX, row * pxPerRow),
                size = Size(gridW, pxPerRow),
            )
        }

        // 2. Vertical beat lines
        val totalBeats = state.hBeats.toInt()
        val barBeats = state.barBeats()
        for (b in 0..totalBeats) {
            val x = KEY_LEFT_PAD_PX + b * gridW / state.hBeats
            drawLine(
                color = if (b % barBeats == 0) OctaveLine else BeatLine,
                start = Offset(x, 0f), end = Offset(x, size.height),
                strokeWidth = if (b % barBeats == 0) 2f else 1f,
            )
        }

        // 2b. Snap sub-grid lines
        val snapTicks = state.snapTicks
        if (snapTicks > 0 && snapTicks * pxPerTick > 6f) {
            val firstSnap = (state.scrollX / snapTicks) * snapTicks
            var t = firstSnap
            val lastTick = state.scrollX + state.hBeats * state.ppq
            while (t <= lastTick) {
                val x = KEY_LEFT_PAD_PX + (t - state.scrollX) * pxPerTick
                if (x >= KEY_LEFT_PAD_PX && x <= size.width) {
                    drawLine(
                        color = BeatLine.copy(alpha = 0.5f),
                        start = Offset(x, 0f), end = Offset(x, size.height),
                        strokeWidth = 1f,
                    )
                }
                t += snapTicks
            }
        }

        // 3. Octave separator lines
        for (row in 0..state.vRows) {
            val key = state.scrollY + state.vRows - row
            if (key % 12 == 0) {
                val y = row * pxPerRow
                drawLine(OctaveLine,
                    start = Offset(KEY_LEFT_PAD_PX, y),
                    end   = Offset(size.width, y),
                    strokeWidth = 2f)
            }
        }

        // 3b. Ruler boundary (tap above this y to seek)
        drawLine(
            color = BorderSubtle.copy(alpha = 0.8f),
            start = Offset(KEY_LEFT_PAD_PX, 16f),
            end = Offset(size.width, 16f),
            strokeWidth = 1f,
        )

        // 4. Notes
        for (n in state.notes) {
            if (!state.showGhosts && n.channel != state.targetChannel) continue
            val x = KEY_LEFT_PAD_PX + ((n.position - state.scrollX) * pxPerTick)
            val w = (n.length * pxPerTick).coerceAtLeast(6f)
            val y = (state.scrollY + state.vRows - 1 - n.key) * pxPerRow
            val h = pxPerRow - 1f
            if (y + h < 0f || y > size.height) continue
            val color = when {
                n.selected -> AccentCyan
                n.channel == state.targetChannel -> AccentOrange
                else -> AccentOrange.copy(alpha = 0.30f)
            }
            drawRect(color, topLeft = Offset(x, y), size = Size(w, h))
            if (n.selected) {
                drawRect(
                    color = SelectionRing,
                    topLeft = Offset(x, y), size = Size(w, h),
                    style = Stroke(width = 2f),
                )
            }
        }

        // 4b. Selection rectangle (live during box-select)
        state.selectionRect?.let { r ->
            drawRect(
                color = AccentCyan.copy(alpha = 0.15f),
                topLeft = Offset(r.left, r.top),
                size = Size(r.width, r.height),
            )
            drawRect(
                color = AccentCyan.copy(alpha = 0.85f),
                topLeft = Offset(r.left, r.top),
                size = Size(r.width, r.height),
                style = Stroke(width = 1.5f),
            )
        }

        // 5. Playhead
        if (playheadTicks >= 0) {
            val phX = KEY_LEFT_PAD_PX + (playheadTicks - state.scrollX) * pxPerTick
            if (phX >= KEY_LEFT_PAD_PX && phX <= size.width) {
                drawLine(PlayheadColor,
                    start = Offset(phX, 0f), end = Offset(phX, size.height),
                    strokeWidth = 2f)
            }
        }

        // 6. Piano keys with note names (rail on the left)
        drawPianoKeys(state, textMeasurer, pxPerRow)
    }
}
