package com.maxixa.lemon.state

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.maxixa.lemon.LemonEngineBridge
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Auto-split from EngineController.kt — Mixer bucket.
// Extension functions on EngineController; same package, call-site transparent.

    internal fun EngineController.mixerAddEffect(track: Int, slot: Int, name: String): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineMixerAddEffect(handle, track, slot, name) }
            catch (t: Throwable) { Log.e(tag, "mixerAddEffect threw", t); false }
        Log.i(tag, "mixerAddEffect(t=$track, s=$slot, $name) -> $ok")
        return ok
    }

    internal fun EngineController.mixerRemoveEffect(track: Int, slot: Int): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineMixerRemoveEffect(handle, track, slot) }
            catch (t: Throwable) { Log.e(tag, "mixerRemoveEffect threw", t); false }
    }

    internal fun EngineController.mixerSlotEffect(track: Int, slot: Int): String {
        if (handle == 0L) return ""
        return try { LemonEngineBridge.engineMixerSlotEffect(handle, track, slot) }
            catch (t: Throwable) { Log.e(tag, "mixerSlotEffect threw", t); "" }
    }

    internal fun EngineController.mixerGetSends(track: Int): Map<Int, Float> {
        if (handle == 0L) return emptyMap()
        val bytes = try { LemonEngineBridge.engineMixerGetSendsBytes(handle, track) }
            catch (t: Throwable) { Log.e(tag, "mixerGetSends threw", t); ByteArray(0) }
        if (bytes.size < 10) return emptyMap()
        val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val out = mutableMapOf<Int, Float>()
        while (buf.remaining() >= 10) {
            val dest = buf.short.toInt() and 0xFFFF
            val lv = buf.double.toFloat()
            out[dest] = lv
        }
        return out
    }

    internal fun EngineController.mixerSetSendLevel(track: Int, dest: Int, level: Double): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineMixerSetSendLevel(handle, track, dest, level) }
            catch (t: Throwable) { Log.e(tag, "mixerSetSendLevel threw", t); false }
        Log.i(tag, "mixerSetSendLevel($track -> $dest, $level) -> $ok")
        return ok
    }

    internal fun EngineController.effectCatalog(): List<Pair<String, String>> {
        return try {
            LemonEngineBridge.engineEffectCatalog().split('\n')
                .mapNotNull { line ->
                    val parts = line.split('\t', limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }
        } catch (t: Throwable) { Log.e(tag, "effectCatalog threw", t); emptyList() }
    }

    internal fun EngineController.mixerBypassEffect(track: Int, slot: Int, on: Boolean): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineMixerBypassEffect(handle, track, slot, on) }
            catch (t: Throwable) { Log.e(tag, "mixerBypassEffect threw", t); false }
    }

    internal fun EngineController.mixerIsEffectBypassed(track: Int, slot: Int): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineMixerEffectBypassed(handle, track, slot) }
            catch (_: Throwable) { false }
    }

    internal fun EngineController.effectParamCount(track: Int, slot: Int): Int {
        if (handle == 0L) return 0
        return try { LemonEngineBridge.engineEffectParamCount(handle, track, slot) }
            catch (t: Throwable) { Log.e(tag, "effectParamCount threw", t); 0 }
    }

    internal fun EngineController.effectParamMeta(track: Int, slot: Int, index: Int): EffectParamMeta? {
        if (handle == 0L) return null
        return try {
            val parts = LemonEngineBridge.engineEffectParamMeta(handle, track, slot, index).split('\t')
            if (parts.size < 8) return null
            EffectParamMeta(
                id = parts[0].toIntOrNull() ?: return null,
                name = parts[1],
                unit = parts[2],
                group = parts[3],
                min = parts[4].toDoubleOrNull() ?: 0.0,
                max = parts[5].toDoubleOrNull() ?: 1.0,
                default = parts[6].toDoubleOrNull() ?: 0.0,
                steps = parts[7].toIntOrNull() ?: 0,
            )
        } catch (t: Throwable) { Log.e(tag, "effectParamMeta threw", t); null }
    }

    internal fun EngineController.effectParamValue(track: Int, slot: Int, id: Int): Double {
        if (handle == 0L) return 0.0
        return try { LemonEngineBridge.engineEffectParamValue(handle, track, slot, id) }
            catch (_: Throwable) { 0.0 }
    }

    internal fun EngineController.effectParamText(track: Int, slot: Int, id: Int, value: Double): String {
        if (handle == 0L) return ""
        return try { LemonEngineBridge.engineEffectParamText(handle, track, slot, id, value) }
            catch (_: Throwable) { "" }
    }

    internal fun EngineController.effectSetParam(track: Int, slot: Int, id: Int, value: Double): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineEffectSetParam(handle, track, slot, id, value) }
            catch (t: Throwable) { Log.e(tag, "setParam threw", t); false }
    }

    internal fun EngineController.mixerSwapEffects(track: Int, a: Int, b: Int): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineMixerSwapEffects(handle, track, a, b) }
            catch (t: Throwable) { Log.e(tag, "swapEffects threw", t); false }
    }
