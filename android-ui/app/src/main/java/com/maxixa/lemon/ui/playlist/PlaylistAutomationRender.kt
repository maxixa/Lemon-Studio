package com.maxixa.lemon.ui.playlist

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.maxixa.lemon.state.*

private val AutomationLineColor = Color(0xFF00E5FF).copy(alpha = 0.9f)
private val AutomationFillColor = Color(0xFF00E5FF).copy(alpha = 0.18f)

/**
 * Draw an automation curve inside an automation clip's rect.
 *
 * `points` = (beat, value) pairs from `engine.automationPoints(ch)`.
 * `beat` is relative to the curve's start (matches `AutoPoint.beat`).
 * `fromBeat` = the clip's window start in beats (0 if no window).
 * `clipBeats` = the clip's duration in beats (length / ppq).
 *
 * Only points inside `[fromBeat, fromBeat + clipBeats]` are drawn — this
 * matches engine behavior (`value_at(points, from_beat + elapsed_beats)`).
 * Adjacent points connect with straight lines (single-mode interpolation).
 */
internal fun DrawScope.drawAutomationCurve(
    points: List<Pair<Double, Double>>,
    fromBeat: Double,
    clipBeats: Double,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
) {
    if (points.isEmpty() || clipBeats <= 0.0 || w <= 4f || h <= 4f) return

    val visible = ArrayList<Pair<Float, Float>>(points.size)
    for ((beat, value) in points) {
        val local = beat - fromBeat
        if (local < 0.0 || local > clipBeats) continue
        val px = x + (local / clipBeats).toFloat() * w
        val py = y + (1f - value.toFloat().coerceIn(0f, 1f)) * h
        visible.add(px to py)
    }
    if (visible.isEmpty()) return

    if (visible.size >= 2) {
        val fill = Path().apply {
            moveTo(visible[0].first, y + h)
            for ((px, py) in visible) lineTo(px, py)
            lineTo(visible.last().first, y + h)
            close()
        }
        drawPath(fill, AutomationFillColor)

        val line = Path().apply {
            moveTo(visible[0].first, visible[0].second)
            for (i in 1 until visible.size) {
                lineTo(visible[i].first, visible[i].second)
            }
        }
        drawPath(line, AutomationLineColor, style = Stroke(1.5f))

        for ((px, py) in visible) {
            drawCircle(AutomationLineColor, 2.5f, Offset(px, py))
        }
    } else {
        drawCircle(
            AutomationLineColor, 2.5f,
            Offset(visible[0].first, visible[0].second),
        )
    }
}
