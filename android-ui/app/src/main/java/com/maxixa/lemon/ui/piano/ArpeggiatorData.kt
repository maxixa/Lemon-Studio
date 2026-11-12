package com.maxixa.lemon.ui.piano

import com.maxixa.lemon.data.NoteView

// NOTE: distinct from `com.maxixa.lemon.state.ArpPattern` (used by GrooveDialog's
// "Arpeggiate" groove, which reorders existing notes). This enum drives the full
// chord→arp grid generator (see ArpeggiatorDialog). Two tools, two enums.
internal enum class ArpSeq(val label: String) {
    Up("Up"),
    Down("Down"),
    UpDown("UpDn"),
    Random("Rand"),
    AsPlayed("Play"),
}

internal data class ArpParams(
    val pattern: ArpSeq = ArpSeq.Up,
    val subdivision: Int = 16,
    val octaves: Int = 1,
    val gatePercent: Int = 80,
    val velocity: Int = 100,
)

private fun stepTicks(sub: Int, ppq: Int): Int = when (sub) {
    4   -> ppq
    8   -> ppq / 2
    16  -> ppq / 4
    32  -> ppq / 8
    -8  -> ppq * 2 / 3
    -16 -> ppq / 3
    else -> ppq / 4
}.coerceAtLeast(1)

internal fun generateArpNotes(
    selected: List<NoteView>,
    ppq: Int,
    params: ArpParams,
): List<NoteView> {
    if (selected.isEmpty()) return emptyList()

    val step = stepTicks(params.subdivision, ppq)
    val gate = (step * params.gatePercent / 100).coerceAtLeast(1)
    val minPos = selected.minOf { it.position }
    val maxEnd = selected.maxOf { it.position + it.length }
    val span = (maxEnd - minPos).coerceAtLeast(step)
    val steps = (span / step).coerceAtLeast(1)

    val basePitches = selected.map { it.key }.distinct().sorted()
    val pool = ArrayList<Int>(basePitches.size * params.octaves)
    for (oct in 0 until params.octaves) {
        for (p in basePitches) {
            val k = p + oct * 12
            if (k in 0..127) pool.add(k)
        }
    }
    if (pool.isEmpty()) return emptyList()

    val seq: List<Int> = when (params.pattern) {
        ArpSeq.Up -> pool
        ArpSeq.Down -> pool.reversed()
        ArpSeq.UpDown -> if (pool.size > 2)
            pool + pool.drop(1).dropLast(1).reversed()
        else pool
        ArpSeq.Random -> pool.shuffled()
        ArpSeq.AsPlayed -> selected.sortedBy { it.position }.map { it.key }
    }
    if (seq.isEmpty()) return emptyList()

    val vel = params.velocity.coerceIn(1, 127)
    val ch = selected.first().channel
    val out = ArrayList<NoteView>(steps)
    for (i in 0 until steps) {
        out.add(NoteView(
            id = 0L,
            position = minPos + i * step,
            length = gate,
            channel = ch,
            key = seq[i % seq.size],
            velocity = vel,
            pan = 64,
            finePitch = 120,
            release = 0,
            modX = 0,
            modY = 0,
            flags = 0x4000,
            group = 0,
            unk14 = 0,
            repeat = 0,
            selected = true,
        ))
    }
    return out
}
