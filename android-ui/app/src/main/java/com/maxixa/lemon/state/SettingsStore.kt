package com.maxixa.lemon.state

import android.content.Context
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf

class SettingsStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("lemon-settings", Context.MODE_PRIVATE)

    val uiScale: MutableFloatState =
        mutableFloatStateOf(prefs.getFloat("ui_scale", 1.0f).coerceIn(0.7f, 1.5f))

    fun setUiScale(v: Float) {
        val clamped = v.coerceIn(0.7f, 1.5f)
        uiScale.floatValue = clamped
        prefs.edit().putFloat("ui_scale", clamped).apply()
    }
}

/** Provided in MainActivity; read from any composable that needs settings. */
val LocalSettings = compositionLocalOf<SettingsStore> {
    error("LocalSettings not provided — wrap content in CompositionLocalProvider")
}
