package com.maxixa.lemon.state

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.maxixa.lemon.LemonEngineBridge
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Auto-split from EngineController.kt — Playlist bucket.
// Extension functions on EngineController; same package, call-site transparent.

    internal fun EngineController.channelSample(ch: Int): String {
        if (handle == 0L) return ""
        return try { LemonEngineBridge.engineChannelSample(handle, ch) }
            catch (t: Throwable) { Log.e(tag, "channelSample threw", t); "" }
    }

    internal fun EngineController.channelInstrument(ch: Int): String {
        if (handle == 0L) return ""
        return try { LemonEngineBridge.engineChannelInstrument(handle, ch) }
            catch (t: Throwable) { Log.e(tag, "channelInstrument threw", t); "" }
    }

    internal fun EngineController.instrumentCatalog(): List<Pair<String, String>> {
        return try {
            LemonEngineBridge.engineInstrumentCatalog().split('\n')
                .mapNotNull { line ->
                    val parts = line.split('\t', limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }
        } catch (t: Throwable) { Log.e(tag, "catalog threw", t); emptyList() }
    }

    internal fun EngineController.channelStartOffset(ch: Int): Double {
        if (handle == 0L) return 0.0
        return try { LemonEngineBridge.engineChannelStartOffset(handle, ch) }
            catch (_: Throwable) { 0.0 }
    }

    internal fun EngineController.channelPitch(ch: Int): Int {
        if (handle == 0L) return 0
        return try { LemonEngineBridge.engineChannelPitch(handle, ch) }
            catch (_: Throwable) { 0 }
    }

    internal fun EngineController.channelLoopStart(ch: Int): Long {
        if (handle == 0L) return -1L
        return try { LemonEngineBridge.engineChannelLoopStart(handle, ch) }
            catch (_: Throwable) { -1L }
    }

    internal fun EngineController.channelLoopEnd(ch: Int): Long {
        if (handle == 0L) return -1L
        return try { LemonEngineBridge.engineChannelLoopEnd(handle, ch) }
            catch (_: Throwable) { -1L }
    }

    internal fun EngineController.channelUseLoop(ch: Int): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineChannelUseLoop(handle, ch) }
            catch (_: Throwable) { false }
    }

    internal fun EngineController.channelPeaks(ch: Int): FloatArray {
        if (handle == 0L) return FloatArray(0)
        val bytes = try { LemonEngineBridge.engineChannelPeaks(handle, ch) }
            catch (t: Throwable) { Log.e(tag, "channelPeaks threw", t); ByteArray(0) }
        if (bytes.size < 4) return FloatArray(0)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(bytes.size / 4)
        for (i in out.indices) out[i] = buf.float
        return out
    }

    internal fun EngineController.channelSampleFrames(ch: Int): Long {
        if (handle == 0L) return 0L
        return try { LemonEngineBridge.engineChannelSampleFrames(handle, ch) }
            catch (_: Throwable) { 0L }
    }

    internal fun EngineController.channelStretchMode(ch: Int): Int {
        if (handle == 0L) return 0
        return try { LemonEngineBridge.engineChannelStretchMode(handle, ch) }
            catch (_: Throwable) { 0 }
    }

    internal fun EngineController.channelStretchMultiplier(ch: Int): Double {
        if (handle == 0L) return 1.0
        return try { LemonEngineBridge.engineChannelStretchMultiplier(handle, ch) }
            catch (_: Throwable) { 1.0 }
    }

    internal fun EngineController.channelStretchPitch(ch: Int): Int {
        if (handle == 0L) return 0
        return try { LemonEngineBridge.engineChannelStretchPitch(handle, ch) }
            catch (_: Throwable) { 0 }
    }

    internal fun EngineController.channelFxFlag(ch: Int, flag: Int): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineChannelFxFlag(handle, ch, flag) }
            catch (_: Throwable) { false }
    }

    internal fun EngineController.channelFxTrimStart(ch: Int): Double {
        if (handle == 0L) return 0.0
        return try { LemonEngineBridge.engineChannelFxTrimStart(handle, ch) }
            catch (_: Throwable) { 0.0 }
    }

    internal fun EngineController.channelFxTrimLength(ch: Int): Double {
        if (handle == 0L) return 1.0
        return try { LemonEngineBridge.engineChannelFxTrimLength(handle, ch) }
            catch (_: Throwable) { 1.0 }
    }

    internal fun EngineController.channelFxNormalize(ch: Int): Boolean {
        if (handle == 0L) return false
        return try { LemonEngineBridge.engineChannelFxNormalize(handle, ch) }
            catch (_: Throwable) { false }
    }

    internal fun EngineController.automationPoints(ch: Int): List<Pair<Double, Double>> {
        if (handle == 0L) return emptyList()
        val bytes = try { LemonEngineBridge.engineAutomationPoints(handle, ch) }
            catch (t: Throwable) { Log.e(tag, "automationPoints threw", t); ByteArray(0) }
        if (bytes.isEmpty()) return emptyList()
        val n = bytes.size / 16
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = ArrayList<Pair<Double, Double>>(n)
        for (i in 0 until n) {
            val beat = buf.double
            val value = buf.double
            out.add(beat to value)
        }
        return out
    }
