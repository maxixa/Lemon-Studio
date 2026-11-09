package com.maxixa.lemon.ui.piano

import com.maxixa.lemon.data.NoteView

/** Which per-note field the EventLane edits. */
enum class LaneField(val label: String, val min: Int, val max: Int, val default: Int) {
    Velocity("Vel",  1, 127, 100),
    Pan     ("Pan",  0, 128,  64),
    ModX    ("ModX", 0, 255,   0),
    ModY    ("ModY", 0, 255,   0);

    fun get(n: NoteView): Int = when (this) {
        Velocity -> n.velocity
        Pan      -> n.pan
        ModX     -> n.modX
        ModY     -> n.modY
    }

    fun set(n: NoteView, v: Int): NoteView {
        val c = v.coerceIn(min, max)
        return when (this) {
            Velocity -> n.copy(velocity = c)
            Pan      -> n.copy(pan = c)
            ModX     -> n.copy(modX = c)
            ModY     -> n.copy(modY = c)
        }
    }
}
