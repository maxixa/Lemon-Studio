package com.maxixa.lemon.ui.piano

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.PianoRollState
import com.maxixa.lemon.ui.theme.BorderSubtle

/** Width of the piano key rail on the left edge of the piano roll. */
internal const val KEY_LEFT_PAD_PX = 40f

private val PianoWhite = Color(0xFF2A2A35)
private val PianoBlack = Color(0xFF17171C)
private val LabelLight = Color(0xFFB0B0BC)
private val LabelDim   = Color(0xFF707078)
private val BLACK_KEYS = intArrayOf(1, 3, 6, 8, 10)
private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/**
 * Draw the left piano key rail with note names.
 *
 * Octave numbers follow the codebase convention (MIDI 60 = C5),
 * so `key / 12` is the octave number, rendered only on C keys.
 * Labels are hidden when rows are too short to fit text.
 */
internal fun DrawScope.drawPianoKeys(
    state: PianoRollState,
    textMeasurer: TextMeasurer,
    rowHeight: Float,
) {
    val showLabels = rowHeight >= 12f
    for (row in 0 until state.vRows) {
        val key = state.scrollY + state.vRows - 1 - row
        val isBlack = key % 12 in BLACK_KEYS
        val top = row * rowHeight
        drawRect(
            color = if (isBlack) PianoBlack else PianoWhite,
            topLeft = Offset(0f, top),
            size = Size(KEY_LEFT_PAD_PX, rowHeight),
        )
        if (showLabels) {
            val pc = key % 12
            val label = if (pc == 0) "${NOTE_NAMES[pc]}${key / 12}" else NOTE_NAMES[pc]
            val layout = textMeasurer.measure(
                label,
                style = TextStyle(
                    color = if (isBlack) LabelDim else LabelLight,
                    fontSize = 8.sp,
                ),
            )
            drawText(
                layout,
                topLeft = Offset(
                    (KEY_LEFT_PAD_PX - layout.size.width) / 2f,
                    top + (rowHeight - layout.size.height) / 2f,
                ),
            )
        }
        drawLine(
            color = BorderSubtle,
            start = Offset(0f, top),
            end   = Offset(KEY_LEFT_PAD_PX, top),
            strokeWidth = 1f,
        )
    }
}
