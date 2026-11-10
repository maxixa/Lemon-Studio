package com.maxixa.lemon.state

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.maxixa.lemon.LemonEngineBridge
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Auto-split from EngineController.kt — Transport bucket.
// Extension functions on EngineController; same package, call-site transparent.

    internal fun EngineController.tickPosition() {
        if (handle == 0L) return
        positionFrames = try { LemonEngineBridge.enginePosition(handle) }
            catch (_: Throwable) { 0L }
    }

    internal fun EngineController.setTempo(v: Float): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineSetTempo(handle, v.toDouble()) }
            catch (t: Throwable) { Log.e(tag, "setTempo threw", t); false }
        if (ok) bpm = v
        Log.i(tag, "setTempo($v) -> $ok")
        return ok
    }

    internal fun EngineController.setMetronome(on: Boolean): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineSetMetronome(handle, on) }
            catch (t: Throwable) { Log.e(tag, "setMetronome threw", t); false }
        if (ok) metronomeOn = on
        return ok
    }

    /** Default PPQ used for bar math in dialogs (96 as the engine's demo). */

    internal fun EngineController.setMetronomeSound(index: Int): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineSetMetronomeSound(handle, index) }
            catch (t: Throwable) { Log.e(tag, "setMetronomeSound threw", t); false }
        if (ok) metronomeSound = index
        return ok
    }

    internal fun EngineController.setMetronomeVolume(db: Int): Boolean {
        if (handle == 0L) return false
        val ok = try { LemonEngineBridge.engineSetMetronomeVolume(handle, db) }
            catch (t: Throwable) { Log.e(tag, "setMetronomeVolume threw", t); false }
        if (ok) metronomeVolumeDb = db.coerceIn(-24, 12)
        return ok
    }

    /** Called once on load to sync with engine state. */

    internal fun EngineController.refreshMetronomeSettings() {
        if (handle == 0L) return
        try {
            metronomeSound = LemonEngineBridge.engineMetronomeSound(handle)
            metronomeVolumeDb = LemonEngineBridge.engineMetronomeVolume(handle)
        } catch (t: Throwable) { Log.e(tag, "refreshMetronome threw", t) }
    }

    internal fun EngineController.togglePlay() {
        if (handle == 0L) { Log.e(tag, "togglePlay: no handle"); return }
        if (isPlaying) {
            LemonEngineBridge.engineStopAudio(handle)
            isPlaying = false
        } else {
            val ok = try { LemonEngineBridge.engineStartAudio(handle) }
                catch (t: Throwable) { Log.e(tag, "engineStartAudio threw", t); false }
            isPlaying = ok
            Log.i(tag, "startAudio -> $ok")
        }
    }

    internal fun EngineController.stop() {
        if (handle == 0L) return
        LemonEngineBridge.engineStopAudio(handle)
        isPlaying = false
        isRecording = false
        positionFrames = 0L
    }

    internal fun EngineController.toggleRecord() { isRecording = !isRecording }

    internal fun EngineController.seek(frames: Long) {
        if (handle == 0L) return
        try { LemonEngineBridge.engineSeek(handle, frames) }
        catch (t: Throwable) { Log.e(tag, "seek threw", t) }
    }
