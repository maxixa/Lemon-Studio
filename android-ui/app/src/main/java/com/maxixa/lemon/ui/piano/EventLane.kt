package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.maxixa.lemon.state.*
import com.maxixa.lemon.ui.theme.*

@Composable
fun EventLane(
    state: PianoRollState,
    field: LaneField,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(72.dp).background(BgDeep)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state.hBeats, state.ppq, state.scrollX, field) {
                    detectTapGestures { tap ->
                        val visibleTicks = state.hBeats * state.ppq
                        if (size.width == 0 || visibleTicks <= 0f) return@detectTapGestures
                        val xTicks = state.scrollX + (tap.x / size.width) * visibleTicks
                        val idx = state.notes.indexOfFirst {
                            xTicks >= it.position &&
                                xTicks <= it.position + it.length.coerceAtLeast(1)
                        }
                        if (idx >= 0) {
                            val v = yToValue(tap.y, size.height.toFloat(), field)
                            state.updateNoteFull(idx, field.set(state.notes[idx], v))
                            onCommit()
                        }
                    }
                }
                .pointerInput(state.hBeats, state.ppq, state.scrollX, field) {
                    var dragIdx = -1
                    detectDragGestures(
                        onDragStart = { start ->
                            val visibleTicks = state.hBeats * state.ppq
                            if (size.width == 0 || visibleTicks <= 0f) return@detectDragGestures
                            val xTicks = state.scrollX + (start.x / size.width) * visibleTicks
                            dragIdx = state.notes.indexOfFirst {
                                xTicks >= it.position &&
                                    xTicks <= it.position + it.length.coerceAtLeast(1)
                            }
                        },
                        onDrag = { change, _ ->
                            if (dragIdx in state.notes.indices) {
                                val v = yToValue(change.position.y, size.height.toFloat(), field)
                                state.updateNoteFull(dragIdx, field.set(state.notes[dragIdx], v))
                            }
                        },
                        onDragEnd = { if (dragIdx >= 0) onCommit(); dragIdx = -1 },
                        onDragCancel = { dragIdx = -1 },
                    )
                },
        ) {
            val visibleTicks = (state.hBeats * state.ppq).coerceAtLeast(1f)
            val startTick = state.scrollX
            val endTick = startTick + visibleTicks
            for (n in state.notes) {
                if (n.position + n.length < startTick || n.position > endTick) continue
                val x0 = ((n.position - startTick) / visibleTicks) * size.width
                val x1 = ((n.position + n.length - startTick) / visibleTicks) * size.width
                val v = field.get(n)
                val norm = (v - field.min).toFloat() / (field.max - field.min).coerceAtLeast(1)
                val y = size.height * (1f - norm)
                drawRect(
                    color = if (n.selected) AccentCyan else AccentOrange,
                    topLeft = Offset(x0, y),
                    size = Size((x1 - x0).coerceAtLeast(2f), size.height - y),
                )
            }
        }
    }
}

private fun yToValue(y: Float, height: Float, field: LaneField): Int {
    if (height <= 0f) return field.default
    val norm = (1f - (y / height)).coerceIn(0f, 1f)
    return (field.min + norm * (field.max - field.min))
        .toInt().coerceIn(field.min, field.max)
}
