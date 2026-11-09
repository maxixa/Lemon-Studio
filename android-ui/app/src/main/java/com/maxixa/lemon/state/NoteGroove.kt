package com.maxixa.lemon.state

import com.maxixa.lemon.data.NoteView

/** Arpeggiator movement. */
enum class ArpPattern(val label: String) {
    Up("Up"),
    Down("Down"),
    UpDown("Up/Down"),
}

/**
 * Pure groove/harmony transforms — rhythm offsets + chord analysis.
 * Same contract as NoteTransforms: pure functions, unselected notes
 * pass through unchanged, applied via PianoRollState.replaceAll().
 */
object NoteGroove {

    private fun groupByPosition(sel: List<NoteView>, tolerance: Int): List<List<NoteView>> {
        if (sel.isEmpty()) return emptyList()
        val out = mutableListOf<MutableList<NoteView>>()
        var cur = mutableListOf<NoteView>()
        var curBase = 0
        for (n in sel.sortedBy { it.position }) {
            if (cur.isEmpty() || n.position - curBase <= tolerance) {
                if (cur.isEmpty()) curBase = n.position
                cur.add(n)
            } else {
                out.add(cur); cur = mutableListOf(n); curBase = n.position
            }
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }

    fun strum(notes: List<NoteView>, stepTicks: Int, ascending: Boolean, tolerance: Int): List<NoteView> {
        if (stepTicks <= 0) return notes
        val sel = notes.filter { it.selected }
        if (sel.size < 2) return notes
        val newPos = mutableMapOf<Long, Int>()
        for (g in groupByPosition(sel, tolerance)) {
            if (g.size < 2) continue
            val ordered = if (ascending) g.sortedBy { it.key } else g.sortedByDescending { it.key }
            ordered.forEachIndexed { i, n -> newPos[n.id] = n.position + i * stepTicks }
        }
        return notes.map { n -> if (n.id in newPos) n.copy(position = newPos.getValue(n.id)) else n }
    }

    fun arpeggiate(notes: List<NoteView>, stepTicks: Int, pattern: ArpPattern, tolerance: Int): List<NoteView> {
        if (stepTicks <= 0) return notes
        val sel = notes.filter { it.selected }
        if (sel.size < 2) return notes
        val newPos = mutableMapOf<Long, Int>()
        for (g in groupByPosition(sel, tolerance)) {
            if (g.size < 2) continue
            val byKey = g.sortedBy { it.key }
            val ordered = when (pattern) {
                ArpPattern.Up -> byKey
                ArpPattern.Down -> byKey.reversed()
                ArpPattern.UpDown ->
                    if (byKey.size <= 2) byKey
                    else byKey + byKey.drop(1).dropLast(1).reversed()
            }
            val base = g.minOf { it.position }
            ordered.forEachIndexed { i, n -> newPos[n.id] = base + i * stepTicks }
        }
        return notes.map { n -> if (n.id in newPos) n.copy(position = newPos.getValue(n.id)) else n }
    }

    fun flam(notes: List<NoteView>, offsetTicks: Int, copies: Int, velDrop: Int): List<NoteView> {
        if (copies < 1) return notes
        val out = ArrayList<NoteView>(notes.size * (copies + 1))
        for (n in notes) {
            out.add(n)
            if (!n.selected) continue
            for (i in 1..copies) {
                out.add(n.copy(
                    id = -1L,
                    position = (n.position + i * offsetTicks).coerceAtLeast(0),
                    velocity = (n.velocity - i * velDrop).coerceIn(1, 127),
                    selected = true,
                ))
            }
        }
        return out
    }

    private data class Template(val name: String, val offsets: Set<Int>)

    private val TEMPLATES = listOf(
        Template("",     setOf(0, 4, 7)),
        Template("m",    setOf(0, 3, 7)),
        Template("dim",  setOf(0, 3, 6)),
        Template("aug",  setOf(0, 4, 8)),
        Template("sus2", setOf(0, 2, 7)),
        Template("sus4", setOf(0, 5, 7)),
        Template("7",    setOf(0, 4, 7, 10)),
        Template("maj7", setOf(0, 4, 7, 11)),
        Template("m7",   setOf(0, 3, 7, 10)),
        Template("dim7", setOf(0, 3, 6, 9)),
        Template("6",    setOf(0, 4, 7, 9)),
        Template("m6",   setOf(0, 3, 7, 9)),
        Template("add9", setOf(0, 2, 4, 7)),
    )

    private val NAMES = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")

    fun detectChord(notes: List<NoteView>): String? {
        val sel = notes.filter { it.selected }
        if (sel.isEmpty()) return null
        val bass = sel.minOf { it.key } % 12
        val pcs = sel.map { it.key % 12 }.toSet()
        for (tpl in TEMPLATES) {
            val target = tpl.offsets.mapTo(HashSet()) { (bass + it) % 12 }
            if (pcs == target) return "${NAMES[bass]}${tpl.name}"
        }
        for (root in 0..11) {
            for (tpl in TEMPLATES) {
                val target = tpl.offsets.mapTo(HashSet()) { (root + it) % 12 }
                if (pcs == target) {
                    val suffix = if (root != bass) "/${NAMES[bass]}" else ""
                    return "${NAMES[root]}${tpl.name}$suffix"
                }
            }
        }
        return "Unknown (${pcs.sorted().joinToString(" ") { NAMES[it] }})"
    }
}
