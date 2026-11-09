package com.maxixa.lemon.state

import android.util.Log
import com.maxixa.lemon.LemonEngineBridge

object ChannelMuteBridge {
    private const val TAG = "Lemon"

    data class MuteState(val muted: Boolean, val soloed: Boolean, val enabled: Boolean)

    fun get(handle: Long, ch: Int): MuteState = try {
        MuteState(
            muted = LemonEngineBridge.engineChannelMuted(handle, ch),
            soloed = LemonEngineBridge.engineChannelSoloed(handle, ch),
            enabled = LemonEngineBridge.engineChannelEnabled(handle, ch),
        )
    } catch (t: Throwable) {
        Log.e(TAG, "get mute state threw", t); MuteState(false, false, true)
    }

    fun setMuted(handle: Long, ch: Int, on: Boolean): Boolean = try {
        LemonEngineBridge.engineSetChannelMuted(handle, ch, on)
    } catch (t: Throwable) {
        Log.e(TAG, "setMuted threw", t); false
    }

    fun setSoloed(handle: Long, ch: Int, on: Boolean): Boolean = try {
        LemonEngineBridge.engineSetChannelSoloed(handle, ch, on)
    } catch (t: Throwable) {
        Log.e(TAG, "setSoloed threw", t); false
    }
}
