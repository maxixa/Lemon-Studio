package com.maxixa.lemon.ui.playlist

private const val HEADER_W = 90f
private const val FLAG_H = 14f
private const val HIT_W = 10f

/**
 * Return the index of a marker whose flag was tapped at (x, y), or -1.
 * Only taps in the top flag strip (y <= FLAG_H) count, so clip taps on
 * the same row still hit clips.
 */
internal fun PlaylistState.hitTestMarker(x: Float, y: Float, w: Float): Int {
    if (y > FLAG_H) return -1
    if (markers.isEmpty()) return -1
    val ppx = pxPerTick(w)
    if (ppx <= 0f) return -1
    for (i in markers.indices.reversed()) {
        val m = markers[i]
        val mx = HEADER_W + (m.position - scrollX) * ppx
        if (x in (mx - HIT_W)..(mx + HIT_W)) return i
    }
    return -1
}
