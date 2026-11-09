package com.maxixa.lemon.state

import android.util.Log
import com.maxixa.lemon.LemonEngineBridge
import com.maxixa.lemon.data.NoteView
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wire format (little-endian):
 *   12-byte header: ppq u32, pattern_length u32, count u32
 *   56-byte body per note, 14 u32:
 *     position, length, channel, key, velocity, pan, fine_pitch, release,
 *     mod_x, mod_y, flags, group, unk14, repeat
 *
 * Must match crates/lemon-engine-ffi/src/piano.rs NOTE = 56.
 */

/** Decoded header + notes, no UI state — safe to use outside PianoRollScreen. */
data class DecodedNotes(
    val ppq: Int,
    val patternLength: Int,
    val notes: List<NoteView>,
)

object PianoRollBridge {
    private const val HEADER = 12
    private const val NOTE = 56
    private const val TAG = "Lemon"

    /**
     * Decode the current pattern's notes from the engine. Returns null if
     * no project is loaded or the wire is too short.
     */
    fun decodeNotes(handle: Long): DecodedNotes? {
        val bytes = try { LemonEngineBridge.engineNotesBytes(handle) }
            catch (t: Throwable) { Log.e(TAG, "notesBytes threw", t); ByteArray(0) }
        if (bytes.size < HEADER) {
            Log.w(TAG, "no notes: ${bytes.size} bytes")
            return null
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val ppq = buf.int
        val plen = buf.int
        val n = buf.int
        val notes = ArrayList<NoteView>(n)
        repeat(n) {
            val pos   = buf.int
            val len   = buf.int
            val ch    = buf.int
            val key   = buf.int
            val vel   = buf.int
            val pan   = buf.int
            val fp    = buf.int
            val rel   = buf.int
            val mx    = buf.int
            val my    = buf.int
            val flags = buf.int
            val grp   = buf.int
            val unk14 = buf.int
            val rep   = buf.int
            notes.add(NoteView(
                id = it.toLong() + 1,
                position = pos, length = len, channel = ch,
                key = key, velocity = vel,
                pan = pan, finePitch = fp, release = rel,
                modX = mx, modY = my, flags = flags,
                group = grp, unk14 = unk14, repeat = rep,
            ))
        }
        return DecodedNotes(ppq = ppq, patternLength = plen, notes = notes)
    }

    /** Load into a mutable PianoRollState. */
    fun load(handle: Long, state: PianoRollState) {
        val decoded = decodeNotes(handle) ?: return
        state.ppq = decoded.ppq
        state.patternLength = decoded.patternLength
        state.notes.clear()
        state.notes.addAll(decoded.notes)
        // Correctness fix: reset nextId past the highest decoded id so
        // subsequent state.add(...) doesn't collide with a loaded note.
        state.nextId = (decoded.notes.maxOfOrNull { it.id } ?: 0L) + 1
        Log.i(TAG, "loaded ${decoded.notes.size} notes (ppq=${decoded.ppq}, length=${decoded.patternLength})")
    }

    fun commit(handle: Long, state: PianoRollState): Boolean {
        val notes = state.commitList()
        val buf = ByteBuffer.allocate(HEADER + notes.size * NOTE).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(state.ppq)
        buf.putInt(state.patternLength)
        buf.putInt(notes.size)
        for (n in notes) {
            buf.putInt(n.position)
            buf.putInt(n.length)
            buf.putInt(n.channel)
            buf.putInt(n.key)
            buf.putInt(n.velocity)
            buf.putInt(n.pan)
            buf.putInt(n.finePitch)
            buf.putInt(n.release)
            buf.putInt(n.modX)
            buf.putInt(n.modY)
            buf.putInt(n.flags)
            buf.putInt(n.group)
            buf.putInt(n.unk14)
            buf.putInt(n.repeat)
        }
        return try { LemonEngineBridge.engineCommitNotes(handle, buf.array()) }
            catch (t: Throwable) { Log.e(TAG, "commitNotes threw", t); false }
    }
}
