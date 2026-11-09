package com.maxixa.lemon.ui.playlist

/**
 * One clip on the playlist.
 *
 * `pattern` is the pattern number for pattern-source clips (0 = none),
 * or the channel index for `sourceKind = 1` (audio/automation clips
 * from FLP imports). The canvas uses it for color only.
 *
 * `windowLo`/`windowHi` are the raw wire `window` values (f32 lossy
 * of f64). Zero on both means no window. Preserved through round-trip
 * even though no UI exposes them yet — prevents data loss on FLP
 * import → edit → save.
 *
 * `fadeInMs`/`fadeOutMs` (with tension) are audio-clip manual fades.
 * Zero = no manual fade (auto-crossfade may still apply at playback
 * from clip overlap). Preserved through round-trip even though no UI
 * exposes them yet.
 */
data class ClipView(
    val id: Long,
    val track: Int,
    val position: Int,
    val length: Int,
    val pattern: Int,
    val muted: Boolean = false,
    val selected: Boolean = false,
    val sourceKind: Int = 0,     // 0 = pattern, 1 = channel (audio/automation)
    val windowLo: Float = 0f,
    val windowHi: Float = 0f,
    val fadeInMs: Float = 0f,
    val fadeInTension: Float = 0f,
    val fadeOutMs: Float = 0f,
    val fadeOutTension: Float = 0f,
    val gain: Float = 1f,
)
