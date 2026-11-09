package com.maxixa.lemon.ui.mixer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.maxixa.lemon.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PanKnob(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val valueRef by rememberUpdatedState(value)
    val onChangeRef by rememberUpdatedState(onChange)
    Canvas(modifier.fillMaxSize().pointerInput(Unit) {
        var startVal = 0f
        detectDragGestures(
            onDragStart = { startVal = valueRef },
            onDrag = { change, drag ->
                val delta = -drag.y / size.height * 2f
                onChangeRef((startVal + delta).coerceIn(-1f, 1f))
                change.consume()
            },
        )
    }) {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = minOf(cx, cy) * 0.85f
        drawArc(BgRaised, 135f, 270f, false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(4f))
        val angle = 135f + ((value + 1f) / 2f) * 270f
        val rad = angle * PI.toFloat() / 180f
        val ix = cx + cos(rad) * (r * 0.75f)
        val iy = cy + sin(rad) * (r * 0.75f)
        drawLine(AccentCyan, Offset(cx, cy), Offset(ix, iy), 4f, StrokeCap.Round)
    }
}
