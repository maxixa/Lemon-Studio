package com.maxixa.lemon.ui.playlist

/** A time marker as the playlist holds it locally. */
data class MarkerView(
    val id: Long,
    val position: Int,       // ticks
    val kind: Int,           // 0 = plain; preserved from wire
    val name: String,        // ≤ 32 UTF-8 bytes on the wire
    val numerator: Int,      // time-signature numerator, 4 by default
    val denominator: Int,    // steps per beat, 4 by default
    val scale: Int,          // key marker pitch mask, 0 by default
)
