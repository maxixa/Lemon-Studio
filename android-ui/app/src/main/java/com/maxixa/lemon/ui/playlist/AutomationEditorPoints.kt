package com.maxixa.lemon.ui.playlist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.maxixa.lemon.ui.theme.BgDeep
import kotlin.math.abs

private val EditorCurve = Color(0xFF00E5FF)
private val EditorPoint = Color(0xFF00E5FF)
private val EditorGrid = Color(0xFF2A2A35)
private const val PointHitR = 22f

@Composable
internal fun AutomationEditorPoints(
    initialPoints: List<Pair<Double, Double>>,
    clipBeats: Double,
    onPointsChange: (List<Pair<Double, Double>>) -> Unit,
) {
    val safeBeats = clipBeats.coerceAtLeast(1.0)
    var pts by remember {
        mutableStateOf(
            if (initialPoints.isEmpty()) listOf(0.0 to 0.5)
            else initialPoints.sortedBy { it.first }
        )
    }
    var dragIdx by remember { mutableStateOf(-1) }

    fun beatAt(x: Float, w: Float): Double = (x / w).toDouble() * safeBeats
    fun valueAt(y: Float, h: Float): Double = (1.0 - (y / h)).coerceIn(0.0, 1.0)

    Box(Modifier.fillMaxWidth().height(260.dp)
        .clip(RoundedCornerShape(6.dp)).background(BgDeep)) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(pts, safeBeats) {
                    detectTapGestures(
                        onTap = { pos ->
                            val hit = pts.indexOfFirst { (pb, pv) ->
                                abs(pos.x - (pb / safeBeats * size.width.toFloat())) < PointHitR
                                    && abs(pos.y - ((1.0 - pv) * size.height.toFloat())) < PointHitR
                            }
                            if (hit < 0) {
                                val b = beatAt(pos.x, size.width.toFloat()).coerceIn(0.0, safeBeats)
                                val v = valueAt(pos.y, size.height.toFloat())
                                pts = (pts + (b to v)).sortedBy { it.first }
                                onPointsChange(pts)
                            }
                        },
                        onLongPress = { pos ->
                            val hit = pts.indexOfFirst { (pb, pv) ->
                                abs(pos.x - (pb / safeBeats * size.width.toFloat())) < PointHitR
                                    && abs(pos.y - ((1.0 - pv) * size.height.toFloat())) < PointHitR
                            }
                            if (hit >= 0 && pts.size > 1) {
                                pts = pts.filterIndexed { i, _ -> i != hit }
                                onPointsChange(pts)
                            }
                        },
                    )
                }
                .pointerInput(pts, safeBeats) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            dragIdx = pts.indexOfFirst { (pb, pv) ->
                                abs(pos.x - (pb / safeBeats * size.width.toFloat())) < PointHitR
                                    && abs(pos.y - ((1.0 - pv) * size.height.toFloat())) < PointHitR
                            }
                        },
                        onDrag = { change, _ ->
                            if (dragIdx in pts.indices) {
                                val b = beatAt(change.position.x, size.width.toFloat()).coerceIn(0.0, safeBeats)
                                val v = valueAt(change.position.y, size.height.toFloat())
                                pts = pts.toMutableList().also { it[dragIdx] = b to v }
                                    .sortedBy { it.first }
                                dragIdx = pts.indexOfFirst { it.first == b }
                                onPointsChange(pts)
                            }
                            change.consume()
                        },
                        onDragEnd = { dragIdx = -1 },
                        onDragCancel = { dragIdx = -1 },
                    )
                },
        ) {
            for (q in 1..3) {
                val y = size.height * q / 4f
                drawLine(EditorGrid, Offset(0f, y), Offset(size.width, y), 1f)
            }
            if (pts.size >= 2) {
                val path = Path().apply {
                    moveTo(0f, (1.0 - pts[0].second).toFloat() * size.height)
                    for ((b, v) in pts) lineTo(
                        (b / safeBeats).toFloat() * size.width,
                        (1.0 - v).toFloat() * size.height,
                    )
                    lineTo(size.width, (1.0 - pts.last().second).toFloat() * size.height)
                }
                drawPath(path, EditorCurve, style = Stroke(2f))
            }
            for ((b, v) in pts) {
                drawCircle(EditorPoint, 6f,
                    Offset((b / safeBeats).toFloat() * size.width,
                           (1.0 - v).toFloat() * size.height))
            }
        }
    }
}
