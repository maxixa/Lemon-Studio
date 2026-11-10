package com.maxixa.lemon.ui.playlist

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.maxixa.lemon.data.NoteView

/**
 * Placeholder grid inside a pattern-source clip.
 *
 * Draws beat lines (every quarter note) + bar lines (brighter) so the clip
 * visibly has rhythmic structure — currently pattern clips render as an empty
 * coloured block, which is indistinguishable from a broken clip.
 *
 * Real note rectangles require a Rust ship (`engineAllPatternNotes` JNI —
 * currently only `engineNotesBytes` = current pattern is exposed). This file
 * is the intended home for that follow-up: `drawPatternNotes()` will sit
 * alongside `drawPatternGrid()` once the wire exists.
 */
internal fun DrawScope.drawPatternGrid(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    pxPerTick: Float,
    ppq: Int,
    barTicks: Int,
    color: Color,
) {
    if (w <= 4f || h <= 4f) return
    if (pxPerTick <= 0f || ppq <= 0 || barTicks <= 0) return

    val pxPerBeat = ppq * pxPerTick
    val pxPerBar  = barTicks * pxPerTick

    // Beat lines — visible if spacing ≥ 3dp, capped to 512 lines
    if (pxPerBeat >= 3f) {
        val n = (w / pxPerBeat).toInt().coerceAtMost(512)
        for (i in 1 until n) {
            val bx = x + i * pxPerBeat
            if (bx > x + w) break
            drawLine(
                color = color.copy(alpha = 0.10f),
                start = Offset(bx, y),
                end   = Offset(bx, y + h),
                strokeWidth = 1f,
            )
        }
    }

    // Bar lines — brighter, visible if spacing ≥ 4dp
    if (pxPerBar >= 4f) {
        val n = (w / pxPerBar).toInt().coerceAtMost(256)
        for (i in 1..n) {
            val bx = x + i * pxPerBar
            if (bx > x + w) break
            drawLine(
                color = color.copy(alpha = 0.30f),
                start = Offset(bx, y),
                end   = Offset(bx, y + h),
                strokeWidth = 1.5f,
            )
        }
    }
}

/**
 * Decode the flat blob from `engineAllPatternNotes`:
 *   u32 num_patterns
 *   per pattern:  u32 pattern_number, u32 block_len, block_len bytes
 *   block = piano wire: 12-byte header (ppq u16, pad u16, plen u32, count u32) + N×56
 *
 * Returned map is pattern_number -> notes.
 */
internal fun decodePatternNotes(bytes: ByteArray): Map<Int, List<NoteView>> {
    if (bytes.size < 4) return emptyMap()
    val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    val numPatterns = bb.int
    if (numPatterns <= 0 || numPatterns > 4096) return emptyMap()
    val result = LinkedHashMap<Int, List<NoteView>>(numPatterns)
    var i = 0
    while (i < numPatterns && bb.remaining() >= 8) {
        val patternNumber = bb.int
        val blockLen = bb.int
        if (blockLen <= 0 || bb.remaining() < blockLen) break
        val block = ByteArray(blockLen)
        bb.get(block)
        result[patternNumber] = decodePianoBlock(block)
        i++
    }
    return result
}

private fun decodePianoBlock(bytes: ByteArray): List<NoteView> {
    val HEADER = 12; val NOTE = 56
    if (bytes.size < HEADER) return emptyList()
    val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    bb.short; bb.short; bb.int  // skip ppq + pad + plen
    val count = bb.int
    if (count <= 0 || count > 65536) return emptyList()
    if (bytes.size < HEADER + count * NOTE) return emptyList()
    val out = ArrayList<NoteView>(count)
    val noteBuf = ByteArray(NOTE)
    repeat(count) {
        bb.get(noteBuf, 0, NOTE)
        val nb = java.nio.ByteBuffer.wrap(noteBuf).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val pos = nb.int; val len = nb.int; val ch = nb.int; val key = nb.int
        val vel = nb.int; val pan = nb.int; val fine = nb.int; val rel = nb.int
        val modX = nb.int; val modY = nb.int; val flags = nb.int; val grp = nb.int
        val unk14 = nb.int; val rpt = nb.int
        out.add(NoteView(0L, pos, len, ch, key, vel, pan, fine, rel, modX, modY, flags, grp, unk14, rpt))
    }
    return out
}

