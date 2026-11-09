package com.maxixa.lemon.ui.rack

/** Decoders for the step-mask byte wire (one byte per step, 0 = off, 1 = on). */
object StepMask {
    const val STEPS_PER_BAR = 16

    fun decode(bytes: ByteArray): List<Boolean> =
        List(bytes.size) { i -> bytes[i].toInt() != 0 }

    fun barsFor(mask: List<Boolean>): Int =
        if (mask.isEmpty()) 1 else (mask.size / STEPS_PER_BAR).coerceAtLeast(1)
}
