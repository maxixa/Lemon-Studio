package com.maxixa.lemon.ui.playlist

import android.util.Log
import com.maxixa.lemon.LemonEngineBridge
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val HEADER = 32
private const val CLIP = 48
private const val MARKER = 52
private const val MARKER_NAME_BYTES = 32
private const val MAGIC: Int = 0x4C454D32.toInt()  // "LEM2"
private const val VERSION = 3
private const val TAG = "Lemon"

/**
 * Read side of the playlist wire (v3). Extracted from PlaylistBridge
 * to keep that file under the 150-LOC ceiling.
 */
internal fun loadFromWire(handle: Long): PlaylistBridge.Loaded {
    val bytes = try { LemonEngineBridge.engineArrangementBytes(handle) }
        catch (t: Throwable) { Log.e(TAG, "arrBytes threw", t); ByteArray(0) }
    val patCount = try { LemonEngineBridge.enginePatternCount(handle) }
        catch (_: Throwable) { 0 }
    if (bytes.size < HEADER) {
        Log.w(TAG, "no arrangement: ${bytes.size} bytes")
        return PlaylistBridge.Loaded(0, patCount, 0, 0, emptyList(), emptyList())
    }
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val magic = buf.int
    if (magic != MAGIC) {
        Log.e(TAG, "bad magic 0x%08X (expect 0x%08X)".format(magic, MAGIC))
        return PlaylistBridge.Loaded(0, patCount, 0, 0, emptyList(), emptyList())
    }
    val version = buf.int
    if (version != VERSION) {
        Log.e(TAG, "bad wire version $version (expect $VERSION)")
        return PlaylistBridge.Loaded(0, patCount, 0, 0, emptyList(), emptyList())
    }
    val trackCount = buf.int
    val patternCount = buf.int
    val clipCount = buf.int
    val markerCount = buf.int
    val currentArrangement = buf.int
    val barNum = buf.int

    val clips = ArrayList<ClipView>(clipCount)
    repeat(clipCount) { i ->
        val track = buf.int
        val pos = buf.int
        val len = buf.int
        val kind = buf.int
        val ref = buf.int
        val muted = buf.int != 0
        val wlo = buf.float
        val whi = buf.float
        val fadeInMs = buf.float
        val fadeInTension = buf.float
        val fadeOutMs = buf.float
        val fadeOutTension = buf.float
        clips.add(ClipView(
            id = i.toLong(),
            track = track,
            position = pos,
            length = len,
            pattern = ref,
            muted = muted,
            sourceKind = kind,
            windowLo = wlo,
            windowHi = whi,
            fadeInMs = fadeInMs,
            fadeInTension = fadeInTension,
            fadeOutMs = fadeOutMs,
            fadeOutTension = fadeOutTension,
        ))
    }

    val markers = ArrayList<MarkerView>(markerCount)
    repeat(markerCount) { i ->
        val pos = buf.int
        val kind = buf.int
        val num = buf.int
        val den = buf.int
        val scale = buf.int
        val nameBytes = ByteArray(MARKER_NAME_BYTES)
        buf.get(nameBytes)
        markers.add(MarkerView(
            id = (i + 1).toLong(),
            position = pos,
            kind = kind,
            name = decodeName(nameBytes),
            numerator = num,
            denominator = den,
            scale = scale,
        ))
    }

    Log.i(TAG, "playlist v3 loaded: tracks=$trackCount patterns=$patternCount " +
               "clips=$clipCount markers=$markerCount arr=$currentArrangement")
    return PlaylistBridge.Loaded(trackCount, patternCount, currentArrangement, barNum, clips, markers)
}

private fun decodeName(buf: ByteArray): String {
    var end = buf.size
    while (end > 0 && buf[end - 1] == 0.toByte()) end--
    return String(buf, 0, end, Charsets.UTF_8)
}
