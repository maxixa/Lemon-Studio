package com.maxixa.lemon.ui.playlist

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.log10

private val KnobOn = Color(0xFF00E5FF)
private val KnobOff = Color(0xFF707078).copy(alpha = 0.55f)
private val LabelColor = Color(0xFFFFFFFF)

private const val KNOB_R = 6f
private const val KNOB_TOP_PAD = 2f

/**
 * Clip gain indicator — small dot at top-center, cyan when gain != 1.0,
 * grey when unity. dB label below while gain deviates from unity.
 * Dragging handled in PlaylistTrimGesture.kt.
 */
internal fun DrawScope.drawGainIndicator(
    c: ClipView, x: Float, y: Float, w: Float, h: Float,
    textMeasurer: TextMeasurer,
) {
    if (c.sourceKind != 1) return
    if (w < 40f || h < 20f) return

    val cx = x + w / 2f
    val cy = y + KNOB_TOP_PAD + KNOB_R
    val unity = abs(c.gain - 1f) < 0.005f
    drawCircle(if (unity) KnobOff else KnobOn, KNOB_R, Offset(cx, cy))

    if (!unity) {
        val db = 20.0 * log10(c.gain.coerceAtLeast(0.001f).toDouble())
        val label = if (db >= 0) "+%.1f".format(db) else "%.1f".format(db)
        val layout = textMeasurer.measure(
            label,
            style = TextStyle(
                color = LabelColor, fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            ),
        )
        drawText(
            layout,
            topLeft = Offset(cx - layout.size.width / 2f, y + h - layout.size.height - 2f),
        )
    }
}
