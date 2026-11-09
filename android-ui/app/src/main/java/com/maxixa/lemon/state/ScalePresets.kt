package com.maxixa.lemon.state

/**
 * Scale presets for the piano roll scale lock. Intervals are semitone offsets
 * from the root pitch class (0..11). 13 common scales covering most of what
 * FL Studio offers (the full 37-scale list lives in os-model::SCALES for FLP
 * import; we duplicate the 13 most common).
 */
internal data class ScalePreset(val label: String, val intervals: IntArray)

internal val SCALE_ROOT_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

internal val SCALE_PRESETS: List<ScalePreset> = listOf(
    ScalePreset("Major",      intArrayOf(0, 2, 4, 5, 7, 9, 11)),
    ScalePreset("Nat Min",    intArrayOf(0, 2, 3, 5, 7, 8, 10)),
    ScalePreset("Harm Min",   intArrayOf(0, 2, 3, 5, 7, 8, 11)),
    ScalePreset("Mel Min",    intArrayOf(0, 2, 3, 5, 7, 9, 11)),
    ScalePreset("Dorian",     intArrayOf(0, 2, 3, 5, 7, 9, 10)),
    ScalePreset("Phrygian",   intArrayOf(0, 1, 3, 5, 7, 8, 10)),
    ScalePreset("Lydian",     intArrayOf(0, 2, 4, 6, 7, 9, 11)),
    ScalePreset("Mixo",       intArrayOf(0, 2, 4, 5, 7, 9, 10)),
    ScalePreset("Locrian",    intArrayOf(0, 1, 3, 5, 6, 8, 10)),
    ScalePreset("Pent Maj",   intArrayOf(0, 2, 4, 7, 9)),
    ScalePreset("Pent Min",   intArrayOf(0, 3, 5, 7, 10)),
    ScalePreset("Blues",      intArrayOf(0, 3, 5, 6, 7, 10)),
    ScalePreset("Chromatic",  intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)),
)

/** All MIDI keys (0..127) in `root` + `intervals`, sorted ascending. */
internal fun buildScaleKeys(root: Int, intervals: IntArray): IntArray {
    val out = ArrayList<Int>(intervals.size * 11)
    for (oct in 0..10) {
        for (i in intervals) {
            val pc = (root + i) % 12
            val k = oct * 12 + pc
            if (k in 0..127) out.add(k)
        }
    }
    out.sort()
    return out.toIntArray()
}

/** Nearest key in `scaleKeys` to `key`. Returns `key` if scaleKeys empty. */
internal fun nearestScaleKey(key: Int, scaleKeys: IntArray): Int {
    if (scaleKeys.isEmpty()) return key
    var best = scaleKeys[0]
    var bestDist = kotlin.math.abs(key - best)
    for (k in scaleKeys) {
        val d = kotlin.math.abs(key - k)
        if (d < bestDist) { bestDist = d; best = k }
    }
    return best
}
