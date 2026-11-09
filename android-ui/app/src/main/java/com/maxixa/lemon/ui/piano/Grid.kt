package com.maxixa.lemon.ui.piano

import androidx.compose.ui.geometry.Size

/** Maps ticks/pitches ↔ pixels for a given viewport. */
class Grid(
    private val size: Size,
    val ppq: Int,
    val hBeats: Float,
    val vRows: Int,
    val scrollX: Float,
    val scrollY: Int,
) {
    val ticksPerScreen = hBeats * ppq
    val pxPerTick = size.width / ticksPerScreen
    val pxPerRow  = size.height / vRows

    fun xOfTick(tick: Float) = (tick - scrollX) * pxPerTick
    fun tickOfX(x: Float) = scrollX + x / pxPerTick

    fun yOfKey(key: Int): Float = ((scrollY + vRows - 1) - key) * pxPerRow
    fun keyOfY(y: Float): Int = (scrollY + vRows - 1) - (y / pxPerRow).toInt()

    fun snapTick(tick: Float, snap: Int): Int =
        (kotlin.math.round(tick / snap) * snap).toInt()
}
