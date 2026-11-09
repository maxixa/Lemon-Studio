package com.maxixa.lemon.ui.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maxixa.lemon.ui.theme.BgDeep



@Composable
fun ZoomIcon(tint: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = h * 0.11f
        drawCircle(tint, w * 0.32f, Offset(w*0.42f, h*0.42f),
            style = Stroke(sw))
        drawLine(tint, Offset(w*0.65f, h*0.65f), Offset(w*0.88f, h*0.88f),
            sw, StrokeCap.Round)
        drawLine(tint, Offset(w*0.24f, h*0.42f), Offset(w*0.60f, h*0.42f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w*0.42f, h*0.24f), Offset(w*0.42f, h*0.60f), sw, StrokeCap.Round)
    }
}

@Composable
fun PencilIcon(tint: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        // Shaft
        drawLine(tint, Offset(w*0.30f, h*0.70f), Offset(w*0.75f, h*0.25f),
            h*0.20f, StrokeCap.Square)
        // Tip (triangle)
        drawPath(Path().apply {
            moveTo(w*0.15f, h*0.85f)
            lineTo(w*0.25f, h*0.65f)
            lineTo(w*0.35f, h*0.75f)
            close()
        }, tint)
    }
}

@Composable
fun EraserIcon(tint: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        drawPath(Path().apply {
            moveTo(w*0.30f, h*0.70f)
            lineTo(w*0.50f, h*0.90f)
            lineTo(w*0.85f, h*0.55f)
            lineTo(w*0.65f, h*0.35f)
            close()
        }, tint)
        // Divider line
        drawLine(BgDeep, Offset(w*0.40f, h*0.60f), Offset(w*0.75f, h*0.25f), h*0.08f)
    }
}

@Composable
fun SelectIcon(tint: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = h * 0.10f
        val x0 = w*0.15f; val y0 = h*0.20f
        val x1 = w*0.85f; val y1 = h*0.80f
        val dash = w * 0.09f
        var x = x0
        while (x < x1) {
            drawLine(tint, Offset(x, y0), Offset((x+dash).coerceAtMost(x1), y0), sw)
            x += dash * 2
        }
        var y = y0
        while (y < y1) {
            drawLine(tint, Offset(x1, y), Offset(x1, (y+dash).coerceAtMost(y1)), sw)
            y += dash * 2
        }
        x = x1
        while (x > x0) {
            drawLine(tint, Offset(x, y1), Offset((x-dash).coerceAtLeast(x0), y1), sw)
            x -= dash * 2
        }
        y = y1
        while (y > y0) {
            drawLine(tint, Offset(x0, y), Offset(x0, (y-dash).coerceAtLeast(y0)), sw)
            y -= dash * 2
        }
    }
}

@Composable
fun PanIcon(tint: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = h * 0.10f
        val c = w * 0.5f; val cy = h * 0.5f
        // Cross
        drawLine(tint, Offset(w*0.20f, cy), Offset(w*0.80f, cy), sw, StrokeCap.Round)
        drawLine(tint, Offset(c, h*0.20f), Offset(c, h*0.80f), sw, StrokeCap.Round)
        // Arrowheads
        val a = w * 0.12f
        // left
        drawLine(tint, Offset(w*0.20f, cy), Offset(w*0.20f+a, cy-a), sw, StrokeCap.Round)
        drawLine(tint, Offset(w*0.20f, cy), Offset(w*0.20f+a, cy+a), sw, StrokeCap.Round)
        // right
        drawLine(tint, Offset(w*0.80f, cy), Offset(w*0.80f-a, cy-a), sw, StrokeCap.Round)
        drawLine(tint, Offset(w*0.80f, cy), Offset(w*0.80f-a, cy+a), sw, StrokeCap.Round)
        // up
        drawLine(tint, Offset(c, h*0.20f), Offset(c-a, h*0.20f+a), sw, StrokeCap.Round)
        drawLine(tint, Offset(c, h*0.20f), Offset(c+a, h*0.20f+a), sw, StrokeCap.Round)
        // down
        drawLine(tint, Offset(c, h*0.80f), Offset(c-a, h*0.80f-a), sw, StrokeCap.Round)
        drawLine(tint, Offset(c, h*0.80f), Offset(c+a, h*0.80f-a), sw, StrokeCap.Round)
    }
}

@Composable
fun ExportIcon(tint: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = h * 0.10f
        // Box outline
        drawLine(tint, Offset(w*0.18f, h*0.55f), Offset(w*0.18f, h*0.85f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w*0.82f, h*0.55f), Offset(w*0.82f, h*0.85f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w*0.18f, h*0.85f), Offset(w*0.82f, h*0.85f), sw, StrokeCap.Round)
        // Up arrow
        drawLine(tint, Offset(w*0.5f, h*0.15f), Offset(w*0.5f, h*0.65f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w*0.5f, h*0.15f), Offset(w*0.35f, h*0.30f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w*0.5f, h*0.15f), Offset(w*0.65f, h*0.30f), sw, StrokeCap.Round)
    }
}

@Composable
fun MetronomeIcon(tint: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = h * 0.09f
        val body = Path().apply {
            moveTo(w * 0.30f, h * 0.15f)
            lineTo(w * 0.70f, h * 0.15f)
            lineTo(w * 0.85f, h * 0.85f)
            lineTo(w * 0.15f, h * 0.85f)
            close()
        }
        drawPath(body, tint, style = Stroke(sw, cap = StrokeCap.Round))
        drawLine(tint, Offset(w * 0.5f, h * 0.85f),
            Offset(w * 0.30f, h * 0.30f), sw, StrokeCap.Round)
        drawCircle(tint, radius = h * 0.06f, center = Offset(w * 0.5f, h * 0.75f))
    }
}

@Composable
fun QuantizeIcon(tint: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = h * 0.09f
        // Three vertical grid lines
        for (i in 0..2) {
            val x = w * (0.20f + i * 0.30f)
            drawLine(tint, Offset(x, h * 0.25f), Offset(x, h * 0.85f),
                sw, StrokeCap.Round)
        }
        // Horizontal snap bar on top
        drawLine(tint, Offset(w * 0.10f, h * 0.15f),
            Offset(w * 0.90f, h * 0.15f), sw, StrokeCap.Round)
        // Small dots at each grid line
        for (i in 0..2) {
            val x = w * (0.20f + i * 0.30f)
            drawCircle(tint, radius = sw * 1.2f, center = Offset(x, h * 0.15f))
        }
    }
}

@Composable
fun DuplicateIcon(tint: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = h * 0.09f
        // Back rect (offset up-left)
        drawRect(tint, Offset(w * 0.15f, h * 0.15f),
            size = androidx.compose.ui.geometry.Size(w * 0.45f, h * 0.45f),
            style = Stroke(sw))
        // Front rect (offset down-right)
        drawRect(tint, Offset(w * 0.40f, h * 0.40f),
            size = androidx.compose.ui.geometry.Size(w * 0.45f, h * 0.45f),
            style = Stroke(sw))
    }
}

@Composable
fun DeleteIcon(tint: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = h * 0.09f
        // Trash can lid
        drawLine(tint, Offset(w * 0.20f, h * 0.25f),
            Offset(w * 0.80f, h * 0.25f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.35f, h * 0.25f),
            Offset(w * 0.35f, h * 0.15f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.65f, h * 0.25f),
            Offset(w * 0.65f, h * 0.15f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.35f, h * 0.15f),
            Offset(w * 0.65f, h * 0.15f), sw, StrokeCap.Round)
        // Trash can body
        drawPath(
            androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.25f, h * 0.32f)
                lineTo(w * 0.30f, h * 0.85f)
                lineTo(w * 0.70f, h * 0.85f)
                lineTo(w * 0.75f, h * 0.32f)
                close()
            },
            color = tint, style = Stroke(sw)
        )
    }
}

@Composable
fun UndoIcon(tint: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = h * 0.11f
        drawArc(tint, startAngle = 40f, sweepAngle = 250f, useCenter = false,
            topLeft = Offset(w * 0.15f, h * 0.30f),
            size = Size(w * 0.70f, h * 0.55f),
            style = Stroke(sw, cap = StrokeCap.Round))
        drawLine(tint, Offset(w * 0.15f, h * 0.50f),
            Offset(w * 0.30f, h * 0.30f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.15f, h * 0.50f),
            Offset(w * 0.35f, h * 0.60f), sw, StrokeCap.Round)
    }
}

@Composable
fun RedoIcon(tint: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = h * 0.11f
        drawArc(tint, startAngle = -110f, sweepAngle = 250f, useCenter = false,
            topLeft = Offset(w * 0.15f, h * 0.30f),
            size = Size(w * 0.70f, h * 0.55f),
            style = Stroke(sw, cap = StrokeCap.Round))
        drawLine(tint, Offset(w * 0.85f, h * 0.50f),
            Offset(w * 0.70f, h * 0.30f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.85f, h * 0.50f),
            Offset(w * 0.65f, h * 0.60f), sw, StrokeCap.Round)
    }
}
