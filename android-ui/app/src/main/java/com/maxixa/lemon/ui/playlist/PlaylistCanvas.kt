package com.maxixa.lemon.ui.playlist

import com.maxixa.lemon.data.NoteView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import com.maxixa.lemon.ui.theme.*

private const val HEADER_W = 90f
private const val ROW_H = 36f
private val PlayheadColor = Color(0xFFFF3B5C)

@Composable
fun PlaylistCanvas(
    state: PlaylistState,
    playheadTicks: Int,
    onCommit: () -> Unit,
    onLoopRangeChange: (Int, Int) -> Unit = { _, _ -> },
    audioClipPeaks: Map<Int, FloatArray> = emptyMap(),
    automationPoints: Map<Int, List<Pair<Double, Double>>> = emptyMap(),
    modifier: Modifier = Modifier,
    patternNotes: Map<Int, List<NoteView>>,
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxSize().playlistGestures(state, onCommit, onLoopRangeChange)) {
        val pxPerTick = state.pxPerTick(size.width)
        val ticksPerBar = state.barTicks().toFloat()

        // Row backgrounds
        for (row in 0 until state.trackCount) {
            val y = (row - state.scrollY) * ROW_H
            if (y + ROW_H < 0 || y > size.height) continue
            val shade = if ((row + state.scrollY) % 2 == 0) Color(0xFF14141A) else Color(0xFF181820)
            drawRect(shade, Offset(HEADER_W, y), Size(size.width - HEADER_W, ROW_H))
            drawRect(BgElevated, Offset(0f, y), Size(HEADER_W, ROW_H))
            drawLine(BorderSubtle, Offset(0f, y + ROW_H), Offset(HEADER_W, y + ROW_H), 1f)
        }

        // Bar grid
        val bars = ((size.width - HEADER_W) / (ticksPerBar * pxPerTick)).toInt() + 2
        for (i in 0..bars) {
            val tick = state.scrollX + i * ticksPerBar
            val x = HEADER_W + (tick - state.scrollX) * pxPerTick
            if (x < HEADER_W || x > size.width) continue
            drawLine(
                if (i == 0) Color(0xFF3A3A4A) else Color(0xFF26262F),
                Offset(x, 0f), Offset(x, size.height), if (i == 0) 2f else 1f,
            )
        }

        // Loop region shading (drawn before clips so clips sit on top)
        if (state.loopStart >= 0 && state.loopEnd > state.loopStart) {
            val lx0 = HEADER_W + (state.loopStart - state.scrollX) * pxPerTick
            val lx1 = HEADER_W + (state.loopEnd - state.scrollX) * pxPerTick
            val cx0 = lx0.coerceIn(HEADER_W, size.width)
            val cx1 = lx1.coerceIn(HEADER_W, size.width)
            if (cx1 > cx0) {
                drawRect(
                    color = AccentCyan.copy(alpha = 0.08f),
                    topLeft = Offset(cx0, 0f),
                    size = Size(cx1 - cx0, size.height),
                )
            }
        }

        // Clips
        drawPlaylistClips(state, audioClipPeaks, automationPoints, patternNotes, pxPerTick, textMeasurer)

        // Markers (flags + lines) drawn after clips so flags stay visible
        drawMarkers(state.markers, state.scrollX, pxPerTick, HEADER_W, textMeasurer)

        // Loop region handles (after clips so they're always visible)
        if (state.loopStart >= 0 && state.loopEnd > state.loopStart) {
            val lx0 = HEADER_W + (state.loopStart - state.scrollX) * pxPerTick
            val lx1 = HEADER_W + (state.loopEnd - state.scrollX) * pxPerTick
            val hc = AccentCyan
            if (lx0 in HEADER_W..size.width) {
                drawRect(hc, Offset(lx0 - 1f, 0f), Size(2f, size.height))
                val p = Path().apply {
                    moveTo(lx0 - 5f, 0f)
                    lineTo(lx0 + 5f, 0f)
                    lineTo(lx0, 8f)
                    close()
                }
                drawPath(p, hc)
            }
            if (lx1 in HEADER_W..size.width) {
                drawRect(hc, Offset(lx1 - 1f, 0f), Size(2f, size.height))
                val p = Path().apply {
                    moveTo(lx1 - 5f, 0f)
                    lineTo(lx1 + 5f, 0f)
                    lineTo(lx1, 8f)
                    close()
                }
                drawPath(p, hc)
            }
        }

        // Selection rectangle (live during box-select)
        state.selectionRect?.let { r ->
            drawRect(
                color = AccentCyan.copy(alpha = 0.15f),
                topLeft = Offset(r.left, r.top),
                size = Size(r.width, r.height),
            )
            drawRect(
                color = AccentCyan.copy(alpha = 0.85f),
                topLeft = Offset(r.left, r.top),
                size = Size(r.width, r.height),
                style = Stroke(1.5f),
            )
        }

        // Playhead
        if (playheadTicks >= 0) {
            val phX = HEADER_W + (playheadTicks - state.scrollX) * pxPerTick
            if (phX >= HEADER_W && phX <= size.width) {
                drawLine(PlayheadColor,
                    Offset(phX, 0f), Offset(phX, size.height), 2f)
            }
        }
    }
}
