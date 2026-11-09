package com.maxixa.lemon.ui.playlist

import android.util.Log
import com.maxixa.lemon.LemonEngineBridge
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Playlist wire (v4). Must match crates/lemon-engine-ffi/src/playlist.rs.
 *
 * Header 32B: magic, version, track_count, pattern_count, clip_count,
 *             marker_count, current_arrangement, bar_num
 * Clip   52B: track, position, length, source_kind, source_ref, muted,
 *             window_lo, window_hi, fade_in_ms, fade_in_tension,
 *             fade_out_ms, fade_out_tension, gain
 * Marker 52B: position, kind, numerator, denominator, scale, name[32]
 *
 * `source_kind` and `window_lo`/`window_hi` are preserved verbatim: read
 * on load, written back on commit. No UI exposes them yet — but preserving
 * them prevents data loss when a project with audio/automation clips or
 * clip windows is opened, edited, and saved.
 */
object PlaylistBridge {
    private const val HEADER = 32
    private const val CLIP = 52
    private const val MARKER = 52
    private const val MARKER_NAME_BYTES = 32
    private const val MAGIC: Int = 0x4C454D32.toInt()  // "LEM2"
    private const val VERSION = 4
    private const val TAG = "Lemon"

    data class Loaded(
        val trackCount: Int,
        val patternCount: Int,
        val currentArrangement: Int,
        val barNum: Int,
        val clips: List<ClipView>,
        val markers: List<MarkerView>,
    )

    fun load(handle: Long): Loaded = loadFromWire(handle)

    fun commit(
        handle: Long,
        trackCount: Int,
        currentArrangement: Int,
        barNum: Int,
        clips: List<ClipView>,
        markers: List<MarkerView>,
        silent: Boolean = false,
    ): Boolean {
        val buf = ByteBuffer.allocate(HEADER + clips.size * CLIP + markers.size * MARKER)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(MAGIC)
        buf.putInt(VERSION)
        buf.putInt(trackCount.coerceAtLeast(4))
        buf.putInt(0)  // pattern_count — ignored by engine
        buf.putInt(clips.size)
        buf.putInt(markers.size)
        buf.putInt(currentArrangement)
        buf.putInt(barNum)

        for (c in clips) {
            buf.putInt(c.track)
            buf.putInt(c.position)
            buf.putInt(c.length)
            buf.putInt(c.sourceKind)
            buf.putInt(c.pattern)
            buf.putInt(if (c.muted) 1 else 0)
            buf.putFloat(c.windowLo)
            buf.putFloat(c.windowHi)
            buf.putFloat(c.fadeInMs)
            buf.putFloat(c.fadeInTension)
            buf.putFloat(c.fadeOutMs)
            buf.putFloat(c.fadeOutTension)
            buf.putFloat(c.gain)
        }

        for (m in markers) {
            buf.putInt(m.position)
            buf.putInt(m.kind)
            buf.putInt(m.numerator)
            buf.putInt(m.denominator)
            buf.putInt(m.scale)
            buf.put(encodeName(m.name))
        }

        return try {
            val bytes = buf.array()
            if (silent) LemonEngineBridge.engineCommitArrangementSilent(handle, bytes)
            else LemonEngineBridge.engineCommitArrangement(handle, bytes)
        } catch (t: Throwable) { Log.e(TAG, "commitArr failed", t); false }
    }


    private fun encodeName(name: String): ByteArray {
        var s = name
        while (s.toByteArray(Charsets.UTF_8).size > MARKER_NAME_BYTES) s = s.dropLast(1)
        val out = ByteArray(MARKER_NAME_BYTES)
        s.toByteArray(Charsets.UTF_8).copyInto(out)
        return out
    }
}
