package com.maxixa.lemon.ext

import android.util.Log
import com.maxixa.lemon.LemonEngineBridge
import com.maxixa.lemon.ExtHost
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Applies a SongSketch JSON (from ExtHost.runSongGenerator) to the engine.
 *
 * Strategy (Phase 1 — patterns only, no arrangement):
 *  1. Set tempo + time sig
 *  2. Create N sampler channels (one per sketch track)
 *  3. Try to assign builtin instrument by category (best-effort)
 *  4. Set channel colors from track.colorHint
 *  5. Create a NEW pattern, switch to it, set its length to song length
 *  6. Commit all channels' notes to that single pattern
 *  7. Switch to pattern mode (song=false) so loop plays the whole thing
 *
 * After apply(), user plays pattern-mode and hears the full song as a loop.
 *
 * Arrangement (Intro/Verse/Outro sections + clip timeline) is NOT applied
 * here yet — the commit_arrangement wire format needs verification first.
 * Sketch's arrangement is preserved in JSON for a future Phase 2 applier.
 */
object SongApplier {
    private const val TAG = "SongApplier"

    // Wire format from PianoRollBridge.kt:
    //   header = ppq, patternLength, noteCount  (3 × i32)
    //   per note = 14 × i32 = 56 bytes
    private const val NOTE_BYTES = 56
    private const val HEADER_BYTES = 12

    data class Summary(
        val tracks: Int,
        val patternNum: Int,
        val notes: Int,
        val bars: Int,
        val warnings: List<String>,
    ) {
        val ok: Boolean get() = tracks > 0 && patternNum > 0 && notes > 0
    }

    fun apply(handle: Long, sketchJson: String): Summary {
        val warnings = mutableListOf<String>()
        if (handle == 0L) {
            return Summary(0, -1, 0, 0, listOf("handle is null"))
        }

        val sketch = try {
            JSONObject(sketchJson)
        } catch (t: Throwable) {
            Log.e(TAG, "bad sketch JSON", t)
            return Summary(0, -1, 0, 0, listOf("bad sketch JSON: ${t.message}"))
        }

        // ── 1. Tempo + time sig
        val tempo = sketch.optDouble("tempo", 120.0)
        LemonEngineBridge.engineSetTempo(handle, tempo)

        val ts = sketch.optJSONObject("timeSig")
        val num = ts?.optInt("numerator", 4) ?: 4
        val den = ts?.optInt("denominator", 4) ?: 4
        LemonEngineBridge.engineSetTimeSignature(handle, num, den)

        val ppq = sketch.optInt("ppq", 96)

        // ── 2. Create channels
        val tracks = sketch.optJSONArray("tracks") ?: return Summary(0, -1, 0, 0, listOf("no tracks"))
        val channelIndices = mutableListOf<Int>()
        val trackNames = mutableListOf<String>()

        for (i in 0 until tracks.length()) {
            val t = tracks.getJSONObject(i)
            val name = t.optString("name", "Track ${i + 1}")
            val ch = try { LemonEngineBridge.engineAddSamplerChannel(handle) }
                     catch (th: Throwable) { Log.e(TAG, "addSamplerChannel threw", th); -1 }

            if (ch < 0) {
                warnings.add("track $i ($name): channel creation failed")
                continue
            }
            channelIndices.add(ch)
            trackNames.add(name)

            // Instrument — best-effort, unknown exact names
            val inst = t.optJSONObject("instrument")
            val category = inst?.optString("category", "Other") ?: "Other"
            val builtin = builtinFor(category)
            if (builtin != null) {
                val ok = try { LemonEngineBridge.engineSetBuiltinInstrument(handle, ch, builtin) }
                         catch (_: Throwable) { false }
                if (!ok) warnings.add("track $i ($name): setBuiltinInstrument(\"$builtin\") failed")
            }

            // Color
            val color = t.optInt("colorHint", 0)
            if (color != 0) {
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                try { LemonEngineBridge.engineSetChannelColor(handle, ch, r, g, b) }
                catch (_: Throwable) {}
            }

            // Mix: per-channel volume/pan JNI methods aren't in the current
            // surface. Channels default to unity gain / center pan; the
            // generator already encodes loudness in note velocities.
        }

        if (channelIndices.isEmpty()) {
            return Summary(0, -1, 0, 0, warnings + "no channels created")
        }

        // ── 3. Compute total bars from arrangement sections (or track note extents)
        val totalBars = computeTotalBars(sketch)
        if (totalBars == 0) {
            return Summary(channelIndices.size, -1, 0, 0, warnings + "could not determine song length")
        }

        // ── 4. Add pattern, switch to it
        val patternNum = try { LemonEngineBridge.engineAddPattern(handle) }
                         catch (th: Throwable) { Log.e(TAG, "addPattern threw", th); -1 }
        if (patternNum <= 0) {
            return Summary(channelIndices.size, -1, 0, totalBars, warnings + "addPattern failed ($patternNum)")
        }

        try { LemonEngineBridge.engineSetPattern(handle, patternNum) } catch (_: Throwable) {}
        try { LemonEngineBridge.engineSetPatternLengthBars(handle, totalBars) } catch (_: Throwable) {}

        // ── 5. Gather all notes (across all tracks) into one buffer
        data class WireNote(
            val startTick: Int, val lengthTicks: Int, val channel: Int,
            val pitch: Int, val velocity: Int, val flags: Int,
        )
        val allNotes = mutableListOf<WireNote>()

        for ((ti, t) in (0 until tracks.length()).map { i -> i to tracks.getJSONObject(i) }) {
            if (ti >= channelIndices.size) continue // channel creation failed for this track
            val ch = channelIndices[ti]
            val patterns = t.optJSONArray("patterns") ?: continue
            if (patterns.length() == 0) continue
            // Phase 1: only apply patterns[0] per track into a single engine pattern
            val p0 = patterns.getJSONObject(0)
            val notes = p0.optJSONArray("notes") ?: continue
            for (ni in 0 until notes.length()) {
                val n = notes.getJSONObject(ni)
                allNotes.add(WireNote(
                    startTick = n.getInt("startTick"),
                    lengthTicks = n.getInt("lengthTicks"),
                    channel = ch,
                    pitch = n.getInt("pitch"),
                    velocity = n.getInt("velocity"),
                    flags = n.optInt("flags", 0),
                ))
            }
        }

        if (allNotes.isEmpty()) {
            return Summary(channelIndices.size, patternNum, 0, totalBars, warnings + "no notes")
        }

        // ── 6. Serialize notes wire format
        val patternLengthTicks = totalBars * ppq * num / den
        val buf = ByteBuffer.allocate(HEADER_BYTES + allNotes.size * NOTE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(ppq)
        buf.putInt(patternLengthTicks)
        buf.putInt(allNotes.size)
        for (n in allNotes) {
            buf.putInt(n.startTick)
            buf.putInt(n.lengthTicks)
            buf.putInt(n.channel)
            buf.putInt(n.pitch)
            buf.putInt(n.velocity)
            buf.putInt(0)         // pan
            buf.putInt(0)         // finePitch
            buf.putInt(0)         // release
            buf.putInt(0)         // modX
            buf.putInt(0)         // modY
            buf.putInt(n.flags)
            buf.putInt(0)         // group
            buf.putInt(0)         // unk14
            buf.putInt(0)         // repeat
        }

        val commitOk = try { LemonEngineBridge.engineCommitNotes(handle, buf.array()) }
                       catch (t: Throwable) { Log.e(TAG, "commitNotes threw", t); false }

        if (!commitOk) {
            return Summary(channelIndices.size, patternNum, 0, totalBars, warnings + "commitNotes failed")
        }

        // ── 7. Pattern mode (loop the whole thing)
        try { LemonEngineBridge.engineSetPlayMode(handle, false) } catch (_: Throwable) {}

        // Diagnostic: log engine state after apply
        try {
            val pc = LemonEngineBridge.enginePatternCount(handle)
            val cur = LemonEngineBridge.enginePatternCurrent(handle)
            Log.i(TAG, "engine: patternCount=$pc current=$cur (weSet=$patternNum)")
        } catch (t: Throwable) { Log.w(TAG, "pattern state query failed", t) }

        try {
            val catalog = LemonEngineBridge.engineInstrumentCatalog()
            Log.i(TAG, "instrument catalog (first 800 chars): ${catalog.take(800)}")
        } catch (t: Throwable) { Log.w(TAG, "catalog query failed", t) }

        for ((i, ch) in channelIndices.withIndex()) {
            try {
                val inst = LemonEngineBridge.engineChannelInstrument(handle, ch)
                Log.i(TAG, "channel[$i] idx=$ch instrument='$inst'")
            } catch (t: Throwable) { Log.w(TAG, "channel[$i] query failed", t) }
        }

        try {
            val decoded = com.maxixa.lemon.state.PianoRollBridge.decodeNotes(handle)
            if (decoded != null) {
                Log.i(TAG, "verify: current pattern has ${decoded.notes.size} notes (ppq=${decoded.ppq}, len=${decoded.patternLength})")
            } else {
                Log.w(TAG, "verify: decodeNotes returned null")
            }
        } catch (t: Throwable) { Log.w(TAG, "verify decodeNotes failed", t) }

        Log.i(TAG, "applied: ${channelIndices.size} tracks, pattern $patternNum, ${allNotes.size} notes, $totalBars bars")
        return Summary(channelIndices.size, patternNum, allNotes.size, totalBars, warnings)
    }

    private fun computeTotalBars(sketch: JSONObject): Int {
        // Prefer arrangement sections if present
        val arr = sketch.optJSONObject("arrangement")
        if (arr != null) {
            val sections = arr.optJSONArray("sections")
            if (sections != null && sections.length() > 0) {
                var maxEnd = 0
                for (i in 0 until sections.length()) {
                    val s = sections.getJSONObject(i)
                    val end = s.optInt("startBar", 0) + s.optInt("lengthBars", 0)
                    if (end > maxEnd) maxEnd = end
                }
                if (maxEnd > 0) return maxEnd
            }
            val clips = arr.optJSONArray("clips")
            if (clips != null && clips.length() > 0) {
                var maxEnd = 0
                for (i in 0 until clips.length()) {
                    val c = clips.getJSONObject(i)
                    val end = c.optInt("startBar", 0) + c.optInt("lengthBars", 0)
                    if (end > maxEnd) maxEnd = end
                }
                if (maxEnd > 0) return maxEnd
            }
        }
        // Fallback: from note extents + ppq
        val ppq = sketch.optInt("ppq", 96)
        val num = sketch.optJSONObject("timeSig")?.optInt("numerator", 4) ?: 4
        val den = sketch.optJSONObject("timeSig")?.optInt("denominator", 4) ?: 4
        val barTicks = (ppq * 4 * num) / den
        if (barTicks <= 0) return 0
        var maxTick = 0
        val tracks = sketch.optJSONArray("tracks") ?: return 0
        for (i in 0 until tracks.length()) {
            val patterns = tracks.getJSONObject(i).optJSONArray("patterns") ?: continue
            for (j in 0 until patterns.length()) {
                val notes = patterns.getJSONObject(j).optJSONArray("notes") ?: continue
                for (k in 0 until notes.length()) {
                    val n = notes.getJSONObject(k)
                    val end = n.getInt("startTick") + n.getInt("lengthTicks")
                    if (end > maxTick) maxTick = end
                }
            }
        }
        return if (maxTick > 0) ((maxTick + barTicks - 1) / barTicks) else 0
    }

    private fun builtinFor(category: String): String? = when (category.lowercase()) {
        "drums", "percussion" -> "Drums"
        "bass" -> "Bass"
        "lead", "synth" -> "Lead"
        "pad" -> "Pad"
        "keys", "piano" -> "Piano"
        "guitar" -> "Guitar"
        "strings" -> "Strings"
        "brass" -> "Brass"
        "woodwind" -> "Flute"
        "vocal" -> "Choir"
        "fx" -> null  // leave as default sampler
        else -> null
    }
}
