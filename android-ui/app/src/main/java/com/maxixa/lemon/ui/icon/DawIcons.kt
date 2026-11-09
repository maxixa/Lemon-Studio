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
fun PlayIcon(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val p = Path().apply {
            moveTo(w * 0.28f, h * 0.15f); lineTo(w * 0.82f, h * 0.5f)
            lineTo(w * 0.28f, h * 0.85f); close()
        }
        drawPath(p, tint)
    }
}

@Composable
fun StopIcon(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        drawRect(tint, Offset(w * 0.2f, h * 0.2f), Size(w * 0.6f, h * 0.6f))
    }
}

@Composable
fun RecordIcon(tint: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        // DrawScope.center (Offset) — not Size.center (removed in newer Compose)
        drawCircle(tint, this.size.minDimension * 0.35f, center)
    }
}

@Composable
fun RewindIcon(tint: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        drawPath(Path().apply {
            moveTo(w * 0.55f, h * 0.2f); lineTo(w * 0.2f, h * 0.5f)
            lineTo(w * 0.55f, h * 0.8f); close()
        }, tint)
        drawPath(Path().apply {
            moveTo(w * 0.85f, h * 0.2f); lineTo(w * 0.5f, h * 0.5f)
            lineTo(w * 0.85f, h * 0.8f); close()
        }, tint)
    }
}

@Composable
fun ForwardIcon(tint: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        drawPath(Path().apply {
            moveTo(w * 0.15f, h * 0.2f); lineTo(w * 0.5f, h * 0.5f)
            lineTo(w * 0.15f, h * 0.8f); close()
        }, tint)
        drawPath(Path().apply {
            moveTo(w * 0.45f, h * 0.2f); lineTo(w * 0.8f, h * 0.5f)
            lineTo(w * 0.45f, h * 0.8f); close()
        }, tint)
    }
}

@Composable
fun LoopIcon(tint: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = h * 0.1f
        drawArc(
            color = tint, startAngle = -30f, sweepAngle = 300f, useCenter = false,
            topLeft = Offset(w * 0.15f, h * 0.15f),
            size = Size(w * 0.7f, h * 0.7f),
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
        drawPath(Path().apply {
            moveTo(w * 0.85f, h * 0.35f); lineTo(w * 0.7f, h * 0.4f)
            lineTo(w * 0.82f, h * 0.5f); close()
        }, tint)
    }
}

@Composable
fun PianoIcon(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = h * 0.06f
        val x0 = w * 0.05f; val y0 = h * 0.15f
        val ww = w * 0.9f; val hh = h * 0.7f
        drawRect(tint, Offset(x0, y0), Size(ww, hh), style = Stroke(sw))
        val keyW = ww / 5f
        for (i in 1..4) drawLine(tint, Offset(x0 + keyW * i, y0),
            Offset(x0 + keyW * i, y0 + hh), sw)
        for (i in 0..3) drawRect(tint,
            Offset(x0 + keyW * i + keyW * 0.6f, y0),
            Size(keyW * 0.5f, hh * 0.5f))
    }
}

@Composable
fun MixerIcon(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = w * 0.12f
        for (i in 0..2) {
            val x = w * (0.25f + i * 0.25f)
            drawLine(tint, Offset(x, h * 0.15f), Offset(x, h * 0.85f), sw)
            drawCircle(tint, sw, Offset(x, h * (0.4f + i * 0.15f)))
        }
    }
}

@Composable
fun FxIcon(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val cx = w / 2f; val cy = h / 2f
        val o = w * 0.42f; val i = w * 0.12f
        drawPath(Path().apply {
            moveTo(cx, cy - o)
            lineTo(cx + i, cy - i); lineTo(cx + o, cy)
            lineTo(cx + i, cy + i); lineTo(cx, cy + o)
            lineTo(cx - i, cy + i); lineTo(cx - o, cy)
            lineTo(cx - i, cy - i); close()
        }, tint)
    }
}

// ── Replaces Icons.Default.Menu (3 horizontal lines) ───────────────
@Composable
fun MenuIcon(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = h * 0.1f
        for (i in 0..2) {
            val y = h * (0.28f + i * 0.22f)
            drawLine(tint, Offset(w * 0.18f, y), Offset(w * 0.82f, y),
                sw, cap = StrokeCap.Round)
        }
    }
}

// ── Replaces Icons.Default.MoreVert (3 vertical dots) ──────────────
@Composable
fun MoreVertIcon(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val r = w * 0.08f
        for (i in 0..2) {
            drawCircle(tint, r, Offset(w * 0.5f, h * (0.25f + i * 0.25f)))
        }
    }
}

// ── Replaces Icons.Default.Add (plus sign) ─────────────────────────
@Composable
fun AddIcon(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = h * 0.1f
        drawLine(tint, Offset(w * 0.5f, h * 0.2f),
            Offset(w * 0.5f, h * 0.8f), sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.2f, h * 0.5f),
            Offset(w * 0.8f, h * 0.5f), sw, cap = StrokeCap.Round)
    }
}

@Composable
fun FolderIcon(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = h * 0.08f
        val p = Path().apply {
            moveTo(w * 0.15f, h * 0.28f)
            lineTo(w * 0.42f, h * 0.28f)
            lineTo(w * 0.48f, h * 0.38f)
            lineTo(w * 0.85f, h * 0.38f)
            lineTo(w * 0.85f, h * 0.78f)
            lineTo(w * 0.15f, h * 0.78f)
            close()
        }
        drawPath(p, tint, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

@Composable
fun FullscreenIcon(tint: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = h * 0.11f
        val c = w * 0.32f
        drawLine(tint, Offset(w*0.15f, h*0.15f), Offset(w*0.15f+c, h*0.15f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w*0.15f, h*0.15f), Offset(w*0.15f, h*0.15f+c), sw, StrokeCap.Round)
        drawLine(tint, Offset(w*0.85f, h*0.15f), Offset(w*0.85f-c, h*0.15f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w*0.85f, h*0.15f), Offset(w*0.85f, h*0.15f+c), sw, StrokeCap.Round)
        drawLine(tint, Offset(w*0.15f, h*0.85f), Offset(w*0.15f+c, h*0.85f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w*0.15f, h*0.85f), Offset(w*0.15f, h*0.85f-c), sw, StrokeCap.Round)
        drawLine(tint, Offset(w*0.85f, h*0.85f), Offset(w*0.85f-c, h*0.85f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w*0.85f, h*0.85f), Offset(w*0.85f, h*0.85f-c), sw, StrokeCap.Round)
    }
}

@Composable
fun ZenIcon(tint: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = h * 0.11f
        drawLine(tint, Offset(w*0.15f, h*0.22f), Offset(w*0.85f, h*0.22f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w*0.15f, h*0.78f), Offset(w*0.85f, h*0.78f), sw, StrokeCap.Round)
        drawCircle(tint, radius = h * 0.13f, center = Offset(w*0.5f, h*0.5f))
    }
}

@Composable
fun BackIcon(tint: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = h * 0.12f
        drawLine(tint, Offset(w*0.3f, h*0.5f), Offset(w*0.85f, h*0.5f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w*0.3f, h*0.5f), Offset(w*0.55f, h*0.25f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w*0.3f, h*0.5f), Offset(w*0.55f, h*0.75f), sw, StrokeCap.Round)
    }
}
