package com.maxixa.lemon.ui.mixer

import android.util.Log
import com.maxixa.lemon.LemonEngineBridge
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MixerBridge {
    private const val TAG = "Lemon"

    fun load(handle: Long): List<MixerTrackView> {
        val bytes = try { LemonEngineBridge.engineMixerBytes(handle) }
            catch (t: Throwable) { Log.e(TAG, "mixerBytes threw", t); ByteArray(0) }
        if (bytes.size < 4) { Log.w(TAG, "no mixer"); return emptyList() }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val n = buf.int
        val list = mutableListOf<MixerTrackView>()
        for (i in 0 until n) {
            val nameLen = buf.int
            val nameBytes = ByteArray(nameLen); buf.get(nameBytes)
            val name = String(nameBytes, Charsets.UTF_8)
            val vol = buf.double.toFloat()
            val pan = buf.double.toFloat()
            list.add(MixerTrackView(i, name, vol, pan))
        }
        Log.i(TAG, "mixer loaded: $n tracks")
        return list
    }

    fun setVolume(handle: Long, track: Int, v: Float): Boolean =
        try { LemonEngineBridge.engineMixerSetVolume(handle, track, v.toDouble()) }
        catch (t: Throwable) { Log.e(TAG, "setVol failed", t); false }

    fun setPan(handle: Long, track: Int, v: Float): Boolean =
        try { LemonEngineBridge.engineMixerSetPan(handle, track, v.toDouble()) }
        catch (t: Throwable) { Log.e(TAG, "setPan failed", t); false }
}
