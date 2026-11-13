package com.maxixa.lemon.state

import com.maxixa.lemon.LemonEngineBridge

internal fun EngineController.setPreferredBufferFrames(frames: Int): Boolean {
    if (handle == 0L) return false
    return try { LemonEngineBridge.engineSetPreferredBufferFrames(handle, frames) }
        catch (t: Throwable) { android.util.Log.e("Lemon", "setPreferredBufferFrames threw", t); false }
}

internal fun EngineController.restartAudio() {
    if (handle == 0L) return
    try { LemonEngineBridge.engineStopAudio(handle) } catch (_: Throwable) {}
    try { LemonEngineBridge.engineStartAudio(handle) } catch (_: Throwable) {}
}

internal fun EngineController.setOutputGain(gain: Float): Boolean {
    if (handle == 0L) return false
    return try { LemonEngineBridge.engineSetOutputGain(handle, gain.toDouble()) }
        catch (t: Throwable) { android.util.Log.e("Lemon", "setOutputGain threw", t); false }
}
