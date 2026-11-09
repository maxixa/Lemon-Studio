package com.maxixa.lemon.state

import com.maxixa.lemon.data.NoteView

/**
 * Selection / clipboard / bulk-edit operations on PianoRollState.
 * Extension functions so the class file stays focused on state and primitives.
 */

fun PianoRollState.commitList(): List<NoteView> = notes.toList()

fun PianoRollState.replaceAll(newNotes: List<NoteView>) {
    val existing = notes.mapTo(HashSet()) { it.id }
    val out = ArrayList<NoteView>(newNotes.size)
    for (n in newNotes) {
        out.add(if (n.id in existing) n else n.copy(id = nextId++))
    }
    notes.clear()
    notes.addAll(out)
}

fun PianoRollState.copySelection(): Int {
    val sel = notes.filter { it.selected }
    if (sel.isEmpty()) return 0
    clipboard = sel.map { it.copy() }
    return sel.size
}

fun PianoRollState.pasteAt(atTick: Int): Int {
    if (clipboard.isEmpty()) return 0
    val minPos = clipboard.minOf { it.position }
    val offset = atTick - minPos
    for (i in notes.indices) {
        if (notes[i].selected) notes[i] = notes[i].copy(selected = false)
    }
    var count = 0
    for (n in clipboard) {
        notes.add(n.copy(
            id = nextId++,
            position = (n.position + offset).coerceAtLeast(0),
            selected = true,
        ))
        count++
    }
    return count
}

fun PianoRollState.quantizeSelected(snapTicks: Int): Int =
    quantizeSelected(snapTicks, 100)

/**
 * Quantize with strength 0..100. Strength 100 = full snap to grid.
 * Strength 0 = no-op. Intermediate values move each selected note
 * to `orig + (snapped - orig) * strength / 100`, rounded.
 * Snap math uses round-half-up (added `+ snapTicks/2` vs. truncated
 * floor in the pre-PR8 body — a subtle fix for the "wrong direction
 * at .5" case, preserved here for consistency.
 */
fun PianoRollState.quantizeSelected(snapTicks: Int, strength: Int): Int {
    if (snapTicks <= 0 || strength <= 0) return 0
    if (strength > 100) return quantizeSelected(snapTicks, 100)
    var n = 0
    for (i in notes.indices) {
        val note = notes[i]
        if (!note.selected) continue
        val target = ((note.position.toFloat() + snapTicks / 2f) / snapTicks)
            .toInt() * snapTicks
        val delta = target - note.position
        val shifted = if (strength == 100) target
                      else (note.position + (delta * strength) / 100)
            .coerceAtLeast(0)
        if (shifted != note.position) {
            notes[i] = note.copy(position = shifted)
            n++
        }
    }
    return n
}

fun PianoRollState.duplicateSelected(barTicks: Int): Int {
    val originals = notes.filter { it.selected }
    if (originals.isEmpty()) return 0
    for (i in notes.indices) {
        if (notes[i].selected) notes[i] = notes[i].copy(selected = false)
    }
    for (note in originals) {
        notes.add(note.copy(
            id = nextId++,
            position = note.position + barTicks,
            selected = true,
        ))
    }
    return originals.size
}

fun PianoRollState.deleteSelectedNotes(): Int {
    val before = notes.size
    notes.removeAll { it.selected }
    return before - notes.size
}


fun PianoRollState.deselectAll() {
    for (i in notes.indices) {
        if (notes[i].selected) notes[i] = notes[i].copy(selected = false)
    }
}

fun PianoRollState.selectAll(channel: Int = -1) {
    for (i in notes.indices) {
        val n = notes[i]
        val want = channel < 0 || n.channel == channel
        if (n.selected != want) notes[i] = n.copy(selected = want)
    }
}

/**
 * Quantize with strength + swing + optional note-end snapping.
 *
 * `swingPercent` (0..100) delays notes landing on odd grid indices by
 * `snapTicks * swingPercent / 200` ticks. 0% = straight (same as
 * quantizeSelected), 50% = subtle shuffle, 66% = triplet/Dilla feel,
 * 100% = notes at odd grid indices land exactly halfway to the next
 * grid line (full shuffle).
 *
 * `quantizeEnds` snaps `note.position + note.length` to the nearest grid
 * multiple and adjusts length to fit, preserving at least 1 tick.
 *
 * `quantizeSelected(snapTicks, strength)` is equivalent to
 * `quantizeWithSwing(snapTicks, strength, 0, false)`.
 */
fun PianoRollState.quantizeWithSwing(
    snapTicks: Int,
    strength: Int,
    swingPercent: Int,
    quantizeEnds: Boolean,
): Int {
    if (snapTicks <= 0) return 0
    val s = strength.coerceIn(0, 100)
    if (s == 0) return 0
    val swing = swingPercent.coerceIn(0, 100)
    val swingOffset = (snapTicks.toLong() * swing / 200L).toInt()
    var n = 0
    for (i in notes.indices) {
        val note = notes[i]
        if (!note.selected) continue
        val gridIdx = ((note.position.toFloat() + snapTicks / 2f) / snapTicks).toInt()
        val baseTarget = gridIdx * snapTicks
        val target = if (swingOffset > 0 && gridIdx % 2 == 1) baseTarget + swingOffset else baseTarget
        val delta = target - note.position
        val shifted = if (s == 100) target
                      else (note.position + (delta * s) / 100).coerceAtLeast(0)
        val newLength = if (quantizeEnds) {
            val endPos = note.position + note.length
            val snappedEnd = ((endPos.toFloat() + snapTicks / 2f) / snapTicks).toInt() * snapTicks
            (snappedEnd - shifted).coerceAtLeast(1)
        } else {
            note.length
        }
        if (shifted != note.position || newLength != note.length) {
            notes[i] = note.copy(position = shifted, length = newLength)
            n++
        }
    }
    return n
}
