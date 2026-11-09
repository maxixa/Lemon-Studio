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
import androidx.compose.ui.input.pointer.pointerInput
import com.maxixa.lemon.ui.theme.*

@Composable
fun Fader(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val valueRef by rememberUpdatedState(value)
    val onChangeRef by rememberUpdatedState(onChange)
    Canvas(modifier.fillMaxSize().pointerInput(Unit) {
        var startVal = 0f
        detectDragGestures(
            onDragStart = { startVal = valueRef },
            onDrag = { change, drag ->
                val delta = -drag.y / size.height
                onChangeRef((startVal + delta).coerceIn(0f, 1f))
                change.consume()
            },
        )
    }) {
        val trackW = size.width * 0.35f
        val cx = size.width / 2f
        val trackH = size.height * 0.85f
        val top = size.height * 0.075f
        drawRect(BgRaised, Offset(cx - trackW / 2, top), Size(trackW, trackH))
        val fillH = trackH * value
        drawRect(AccentOrange, Offset(cx - trackW / 2, top + (trackH - fillH)),
            Size(trackW, fillH))
        val hy = top + (trackH * (1f - value)) - 6f
        drawRect(TextPrimary, Offset(cx - size.width * 0.25f, hy),
            Size(size.width * 0.5f, 12f))
    }
}
