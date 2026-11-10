package com.maxixa.lemon.state

import com.maxixa.lemon.LemonEngineBridge

internal fun EngineController.isBus(track: Int): Boolean {
    if (handle == 0L) return false
    return try { LemonEngineBridge.engineMixerIsBus(handle, track) }
        catch (t: Throwable) { android.util.Log.e("Lemon", "isBus threw", t); false }
}

internal fun EngineController.setBus(track: Int, isBus: Boolean): Boolean {
    if (handle == 0L) return false
    return try { LemonEngineBridge.engineMixerSetBus(handle, track, isBus) }
        catch (t: Throwable) { android.util.Log.e("Lemon", "setBus threw", t); false }
}

internal fun EngineController.busList(): List<Int> {
    if (handle == 0L) return emptyList()
    return try {
        val bytes = LemonEngineBridge.engineMixerBusList(handle)
        if (bytes.size < 4) return emptyList()
        val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val count = bb.int
        if (count < 0 || count > 4096) return emptyList()
        val out = ArrayList<Int>(count)
        repeat(count) { if (bb.remaining() >= 2) out.add(bb.short.toInt() and 0xFFFF) }
        out
    } catch (t: Throwable) { android.util.Log.e("Lemon", "busList threw", t); emptyList() }
}
