package com.maxixa.lemon.state

/**
 * Frame ↔ tick conversion, tempo-aware.
 *
 * The engine advances `positionFrames` in raw audio-callback frames.
 * Converting to musical ticks requires BPM — the naive `frames * ppq /
 * sampleRate` formula is correct only at 60 BPM. Reference implementation
 * is os-engine/src/clock.rs::samples_per_tick:
 *
 *     spt = sampleRate * 60.0 / (bpm * ppq)     // samples per tick
 *     tick = frames / spt
 *     frames = tick * spt
 *
 * All arithmetic is done in Double so precision holds over long songs
 * (a 20-minute track at 48 kHz is ~57 million frames — well within
 * Double's exact-integer range).
 */
object TickFrames {

    fun samplesPerTick(ppq: Int, bpm: Float, sampleRate: Int): Double {
        if (ppq <= 0 || bpm <= 0f || sampleRate <= 0) return 0.0
        return sampleRate.toDouble() * 60.0 / (bpm.toDouble() * ppq.toDouble())
    }

    /** Frames → ticks. Returns 0 on invalid inputs. */
    fun positionTicks(frames: Long, ppq: Int, bpm: Float, sampleRate: Int): Int {
        val spt = samplesPerTick(ppq, bpm, sampleRate)
        if (spt <= 0.0) return 0
        return (frames.toDouble() / spt).toInt()
    }

    /** Ticks → frames. Returns 0 on invalid inputs. */
    fun framesForTicks(tick: Int, ppq: Int, bpm: Float, sampleRate: Int): Long {
        if (tick <= 0) return 0L
        val spt = samplesPerTick(ppq, bpm, sampleRate)
        if (spt <= 0.0) return 0L
        return (tick.toDouble() * spt).toLong()
    }
}
