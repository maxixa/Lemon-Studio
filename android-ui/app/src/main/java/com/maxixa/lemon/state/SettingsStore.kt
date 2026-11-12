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

    fun busTracksFor(scope: String): Set<Int> =
        prefs.getStringSet("bus_tracks:$scope", emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()

    fun setBusTracksFor(scope: String, v: Set<Int>) {
        prefs.edit()
            .putStringSet("bus_tracks:$scope", v.map { it.toString() }.toSet())
            .apply()
    }

    /** Count-in bars for mic recording: 0 = off, 1-4 = pre-roll length. */
    var countInBars: Int
        get() = prefs.getInt("record_count_in_bars", 0).coerceIn(0, 4)
        set(v) {
            prefs.edit().putInt("record_count_in_bars", v.coerceIn(0, 4)).apply()
        }

    var recordMonitorEnabled: Boolean
        get() = prefs.getBoolean("record_monitor", false)
        set(v) { prefs.edit().putBoolean("record_monitor", v).apply() }


    var exportSongMode: Boolean
        get() = prefs.getBoolean("export_song_mode", true)
        set(v) { prefs.edit().putBoolean("export_song_mode", v).apply() }

    var exportRate: Int
        get() = prefs.getInt("export_rate", 48000)
        set(v) { prefs.edit().putInt("export_rate", v).apply() }

    var exportFormatName: String
        get() = prefs.getString("export_format", "Wav") ?: "Wav"
        set(v) { prefs.edit().putString("export_format", v).apply() }

    var exportWavBits: Int
        get() = prefs.getInt("export_wav_bits", 32)
        set(v) { prefs.edit().putInt("export_wav_bits", v).apply() }

    var exportWithStems: Boolean
        get() = prefs.getBoolean("export_with_stems", false)
        set(v) { prefs.edit().putBoolean("export_with_stems", v).apply() }

    var exportMp3Kbps: Int
        get() = prefs.getInt("export_mp3_kbps", 192)
        set(v) { prefs.edit().putInt("export_mp3_kbps", v.coerceIn(64, 320)).apply() }

    private val BUS_FX_PREFIX = "bus_fx_preset:"

    fun listBusFxPresets(): List<String> =
        prefs.all.keys
            .filter { it.startsWith(BUS_FX_PREFIX) }
            .map { it.removePrefix(BUS_FX_PREFIX) }
            .sorted()

    fun getBusFxPreset(name: String): String? =
        prefs.getString("$BUS_FX_PREFIX$name", null)

    fun setBusFxPreset(name: String, encoded: String) {
        prefs.edit().putString("$BUS_FX_PREFIX$name", encoded).apply()
    }

    fun deleteBusFxPreset(name: String) {
        prefs.edit().remove("$BUS_FX_PREFIX$name").apply()
    }
}

/** Provided in MainActivity; read from any composable that needs settings. */
val LocalSettings = compositionLocalOf<SettingsStore> {
    error("LocalSettings not provided — wrap content in CompositionLocalProvider")
}
