package com.maxixa.lemon.ui.screen

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.maxixa.lemon.LemonEngineBridge
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.state.PathsStore
import com.maxixa.lemon.state.RecentProject
import com.maxixa.lemon.state.RecentProjectsStore
import com.maxixa.lemon.state.*

/**
 * Runs the once-per-app bootstrap: set factory path, load last project /
 * test.flp / demo, refresh metronome settings + transport extras, load
 * pattern names, register disposer. Calls `onRecentProjects` and
 * `onPatternNames` with loaded data so DawScreen owns the state.
 */
@Composable
internal fun DawBootstrap(
    engine: EngineController,
    pathsStore: PathsStore,
    recentStore: RecentProjectsStore,
    onRecentProjects: (List<RecentProject>) -> Unit,
    onPatternNames: (List<String>) -> Unit,
) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val stored = pathsStore.factoryPath
        if (stored.isNotEmpty()) engine.setFactory(stored)

        val lastUriStr = recentStore.lastProjectUri
        var loaded = false
        if (lastUriStr.isNotEmpty()) {
            try {
                val uri = Uri.parse(lastUriStr)
                ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    if (engine.loadProjectFd(pfd.fd)) {
                        Log.i("Lemon", "auto-loaded last project: $lastUriStr")
                        loaded = true
                    }
                }
            } catch (t: Throwable) {
                Log.w("Lemon", "auto-load last project failed: ${t.message}")
            }
        }
        if (!loaded) {
            val f = ctx.getExternalFilesDir(null)?.resolve("test.flp")
            if (f != null && f.exists()) engine.loadProject(f.absolutePath)
            else engine.loadDemo()
        }
        onRecentProjects(recentStore.load())
        engine.refreshMetronomeSettings()
        engine.refreshTransportExtras()

        val n = try { LemonEngineBridge.enginePatternCount(engine.handleRef) }
                catch (_: Throwable) { 0 }
        onPatternNames((0 until n).map { i ->
            try { LemonEngineBridge.enginePatternName(engine.handleRef, i) }
            catch (_: Throwable) { "Pattern ${i + 1}" }
        })

        onDispose { engine.dispose() }
    }
}
