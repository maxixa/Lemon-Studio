package com.maxixa.lemon.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.ui.theme.*
import com.maxixa.lemon.state.*

private val LoopStartColor = Color(0xFF00E5A0)
private val LoopEndColor = Color(0xFFFF3B5C)
private val WaveColor = Color(0xFF8E8E99)

@Composable
fun WaveformLoopSection(
    channelIndex: Int,
    peaks: FloatArray,
    engine: EngineController,
    totalFrames: Float,
) {
    var useLoop by remember { mutableStateOf(engine.channelUseLoop(channelIndex)) }
    var loopStart by remember {
        mutableStateOf(engine.channelLoopStart(channelIndex).let { if (it < 0) 0f else it / totalFrames })
    }
    var loopEnd by remember {
        mutableStateOf(engine.channelLoopEnd(channelIndex).let { if (it < 0) 1f else it / totalFrames })
    }

    Box(
        Modifier.fillMaxWidth().height(120.dp)
            .background(BgDeep, RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                var dragging = -1
                detectDragGestures(
                    onDragStart = { pos ->
                        val w = size.width.toFloat()
                        val ds = kotlin.math.abs(pos.x - loopStart * w)
                        val de = kotlin.math.abs(pos.x - loopEnd * w)
                        dragging = if (ds < de) 0 else 1
                    },
                    onDrag = { change, _ ->
                        val w = size.width.toFloat()
                        val n = (change.position.x / w).coerceIn(0f, 1f)
                        if (dragging == 0) loopStart = n.coerceIn(0f, loopEnd - 0.01f)
                        else if (dragging == 1) loopEnd = n.coerceIn(loopStart + 0.01f, 1f)
                        change.consume()
                    },
                    onDragEnd = {
                        engine.setChannelLoopPoints(
                            channelIndex,
                            (loopStart * totalFrames).toLong(),
                            (loopEnd * totalFrames).toLong(),
                        )
                        useLoop = engine.channelUseLoop(channelIndex)
                    },
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val barW = w / peaks.size
            for (i in peaks.indices) {
                val amp = peaks[i].coerceIn(0f, 1f)
                if (amp < 0.005f) continue
                val barH = amp * h * 0.85f
                drawRect(WaveColor,
                    Offset(i * barW, (h - barH) / 2f),
                    Size(barW.coerceAtLeast(1f), barH))
            }
            if (useLoop) {
                drawRect(LoopStartColor.copy(alpha = 0.08f),
                    Offset(loopStart * w, 0f),
                    Size((loopEnd - loopStart) * w, h))
            }
            drawLine(LoopStartColor, Offset(loopStart * w, 0f), Offset(loopStart * w, h), 3f)
            drawLine(LoopEndColor, Offset(loopEnd * w, 0f), Offset(loopEnd * w, h), 3f)
            drawRect(BorderSubtle, Offset(0f, 0f), Size(w, h), style = Stroke(1f))
        }
    }

    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Loop: %.1f%% – %.1f%%".format(loopStart * 100, loopEnd * 100),
            color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Text("Tap-drag handles", color = TextMuted, fontSize = 9.sp)
    }

    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth().height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp, 22.dp)
                .background(if (useLoop) AccentOrange else BgRaised, RoundedCornerShape(11.dp))
                .clickable {
                    useLoop = !useLoop
                    engine.setChannelUseLoop(channelIndex, useLoop)
                },
            contentAlignment = if (useLoop) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.padding(horizontal = 2.dp).size(18.dp)
                .background(if (useLoop) BgDeep else TextMuted, RoundedCornerShape(9.dp)))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (useLoop) "Loop active (sustained notes repeat)"
            else "Loop off (one-shot)",
            color = TextPrimary, fontSize = 11.sp,
        )
    }

    if (engine.channelLoopStart(channelIndex) >= 0) {
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(30.dp)
                .background(BgRaised, RoundedCornerShape(6.dp))
                .clickable {
                    engine.clearChannelLoopPoints(channelIndex)
                    loopStart = 0f; loopEnd = 1f
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("Clear loop points", color = AccentRed, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold)
        }
    }
}
