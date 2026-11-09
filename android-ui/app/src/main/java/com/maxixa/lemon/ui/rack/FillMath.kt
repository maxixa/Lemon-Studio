package com.maxixa.lemon.ui.rack

import kotlin.random.Random

/**
 * Pure fill-pattern math. Testable, no Compose, no state.
 * Each returns a List<Boolean> of `steps` length (true = step on).
 */
object FillMath {

    /** Every N: hits at 0, N, 2N, ... Steps below `n` means all-on. */
    fun everyN(steps: Int, n: Int): List<Boolean> {
        if (steps <= 0) return emptyList()
        if (n <= 1) return List(steps) { true }
        return List(steps) { i -> i % n == 0 }
    }

    /**
     * Euclidean: hits distributed as evenly as possible using the
     * "next hit" trick. `pulses` bounded to [0, steps].
     * Classic sanity: (16, 4) = four-on-the-floor; (16, 5) = tresillo [0,4,7,10,13].
     */
    fun euclidean(steps: Int, pulses: Int): List<Boolean> {
        if (steps <= 0) return emptyList()
        if (pulses <= 0) return List(steps) { false }
        if (pulses >= steps) return List(steps) { true }
        return List(steps) { i -> (i * pulses) % steps < pulses }
    }

    /** Random: each step independently on with `densityPct`% chance. */
    fun random(steps: Int, densityPct: Int, rnd: Random = Random): List<Boolean> {
        if (steps <= 0) return emptyList()
        val d = densityPct.coerceIn(0, 100)
        return List(steps) { rnd.nextInt(100) < d }
    }
}
