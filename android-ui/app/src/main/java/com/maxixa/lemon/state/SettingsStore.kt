package com.maxixa.lemon.state

import android.content.Context
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf

class SettingsStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("lemon-settings", Context.MODE_PRIVATE)

    val uiScale: MutableFloatState =
        mutableFloatStateOf(prefs.getFloat("ui_scale", 1.0f).coerceIn(0.7f, 1.5f))

    fun setUiScale(v: Float) {
        val clamped = v.coerceIn(0.7f, 1.5f)
        uiScale.floatValue = clamped
        prefs.edit().putFloat("ui_scale", clamped).apply()
    }

    // ─── Piano roll scale lock ──────────────────────────────────

    var scaleLockEnabled: Boolean
        get() = prefs.getBoolean("scale_lock_enabled", false)
        set(v) { prefs.edit().putBoolean("scale_lock_enabled", v).apply() }

    var scaleRoot: Int
        get() = prefs.getInt("scale_root", 0)
        set(v) { prefs.edit().putInt("scale_root", v).apply() }

    var scaleType: Int
        get() = prefs.getInt("scale_type", 0)
        set(v) { prefs.edit().putInt("scale_type", v).apply() }

    // ─── Playlist ───────────────────────────────────────────────

    var barsVisible: Float
        get() = prefs.getFloat("bars_visible", 4f).coerceIn(1f, 32f)
        set(v) {
            val c = v.coerceIn(1f, 32f)
            prefs.edit().putFloat("bars_visible", c).apply()
        }

    var trimMode: Boolean
        get() = prefs.getBoolean("trim_mode", false)
        set(v) { prefs.edit().putBoolean("trim_mode", v).apply() }

    private val _showGhosts = mutableStateOf(prefs.getBoolean("show_ghosts", false))
    var showGhosts: Boolean
        get() = _showGhosts.value
        set(v) {
            _showGhosts.value = v
            prefs.edit().putBoolean("show_ghosts", v).apply()
        }

    /** Set of mixer tracks tagged as buses. Persisted as string set (prefs key `bus_tracks`). */
    var busTracks: Set<Int>
        get() = prefs.getStringSet("bus_tracks", emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()
        set(v) {
            prefs.edit()
                .putStringSet("bus_tracks", v.map { it.toString() }.toSet())
                .apply()
        }
}

/** Provided in MainActivity; read from any composable that needs settings. */
val LocalSettings = compositionLocalOf<SettingsStore> {
    error("LocalSettings not provided — wrap content in CompositionLocalProvider")
}
