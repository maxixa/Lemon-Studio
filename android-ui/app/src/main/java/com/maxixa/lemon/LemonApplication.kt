package com.maxixa.lemon

import android.app.Application
import android.util.Log

class LemonApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("lemon_engine_ffi")
            Log.i("Lemon", "liblemon_engine_ffi loaded")
            try { com.maxixa.lemon.LemonEngineBridge.engineClearSampleCache() } catch (_: Throwable) {}
            LemonEngineBridge.initContext(this)
            Log.i("Lemon", "initContext done")
            Log.i("Lemon", "engine version: ${LemonEngineBridge.engineVersion()}")
            // Pre-create the default factory folder so users have a place
            // to drop FL Studio samples.
            val factoryDir = getExternalFilesDir(null)?.resolve("factory")
            if (factoryDir != null && !factoryDir.exists()) {
                factoryDir.mkdirs()
                Log.i("Lemon", "created factory dir: ${factoryDir.absolutePath}")
            }
        } catch (t: Throwable) {
            Log.e("Lemon", "engine bootstrap failed", t)
        }
    }
}
