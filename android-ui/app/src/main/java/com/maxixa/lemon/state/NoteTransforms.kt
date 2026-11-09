package com.maxixa.lemon.state

import com.maxixa.lemon.data.NoteView
import kotlin.random.Random

/**
 * Pure musical transforms on the piano roll note list.
 *
 * Each function takes the full list and returns a new list; unselected
 * notes pass through unchanged. No mutation, no Compose, no state.
 * Callers apply via PianoRollState.replaceAll() and then commit.
 */
object NoteTransforms {

    /** Add random ±strength to velocity and ±strength/3 to fine pitch. */
    fun randomize(notes: List<NoteView>, strength: Int, rnd: Random = Random): List<NoteView> {
        if (strength <= 0) return notes
        val ds = (strength / 3).coerceAtLeast(1)
        return notes.map { n ->
            if (!n.selected) n else n.copy(
                velocity = (n.velocity + rnd.nextInt(-strength, strength + 1)).coerceIn(1, 127),
                finePitch = (n.finePitch + rnd.nextInt(-ds, ds + 1)).coerceIn(0, 255),
            )
        }
    }

    /** Multiply velocity by percent/100, clamped to 1..=127. */
    fun scaleLevels(notes: List<NoteView>, percent: Int): List<NoteView> {
        val f = percent / 100f
        return notes.map { n ->
            if (!n.selected) n else n.copy(
                velocity = (n.velocity * f).toInt().coerceIn(1, 127),
            )
        }
    }

    /** Clamp velocity into [min..=max]. Argument order doesn't matter. */
    fun limit(notes: List<NoteView>, min: Int, max: Int): List<NoteView> {
        val lo = minOf(min, max)
        val hi = maxOf(min, max)
        return notes.map { n ->
            if (!n.selected) n else n.copy(
                velocity = n.velocity.coerceIn(lo, hi),
            )
        }
    }

    /**
     * Mirror selected notes.
     * byPitch=false: mirror positions horizontally around the selection span.
     * byPitch=true:  mirror pitches vertically around the selection center.
     */
    fun flip(notes: List<NoteView>, byPitch: Boolean): List<NoteView> {
        val sel = notes.filter { it.selected }
        if (sel.isEmpty()) return notes
        return if (byPitch) {
            val pivot = sel.minOf { it.key } + sel.maxOf { it.key }
            notes.map { n ->
                if (!n.selected) n else n.copy(key = (pivot - n.key).coerceIn(0, 127))
            }
        } else {
            val pivot = sel.minOf { it.position } + sel.maxOf { it.position + it.length }
            notes.map { n ->
                if (!n.selected) n else n.copy(
                    position = (pivot - n.position - n.length).coerceAtLeast(0),
                )
            }
        }
    }

    /**
     * Split each selected note into `parts` pieces with a gap percentage.
     * Piece 0 keeps the original id; others get id = -1L for replaceAll()
     * to reassign. Selection is preserved on every piece.
     */
    fun chop(notes: List<NoteView>, parts: Int, gapPercent: Int): List<NoteView> {
        if (parts < 2) return notes
        val out = ArrayList<NoteView>(notes.size)
        for (n in notes) {
            if (!n.selected || n.length < parts) { out.add(n); continue }
            val partLen = n.length / parts
            val gap = (partLen * gapPercent / 100).coerceIn(0, partLen - 1)
            val kept = partLen - gap
            for (i in 0 until parts) {
                out.add(n.copy(
                    id = if (i == 0) n.id else -1L,
                    position = n.position + i * partLen,
                    length = kept,
                ))
            }
        }
        return out
    }
}
