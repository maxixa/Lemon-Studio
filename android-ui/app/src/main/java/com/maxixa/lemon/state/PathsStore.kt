package com.maxixa.lemon.state

import android.content.Context
import android.util.Log

class PathsStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("lemon-paths", Context.MODE_PRIVATE)
    private val tag = "Lemon"

    var factoryPath: String
        get() = prefs.getString("factory", "") ?: ""
        set(v) {
            prefs.edit().putString("factory", v).apply()
            Log.i(tag, "saved factory: ${if (v.isEmpty()) "(none)" else v}")
        }

    /** Suggested folder that the app can always read. */
    fun defaultFactoryDir(ctx: Context): String {
        val f = ctx.getExternalFilesDir(null)?.resolve("factory") ?: return ""
        return f.absolutePath
    }

    /** Create the default factory dir if missing, return its path. */
    fun ensureDefaultFactory(ctx: Context): String {
        val f = ctx.getExternalFilesDir(null)?.resolve("factory") ?: return ""
        if (!f.exists()) f.mkdirs()
        return f.absolutePath
    }
}
