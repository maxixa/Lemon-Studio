package com.maxixa.lemon.ui.playlist

import androidx.compose.runtime.*
import com.maxixa.lemon.LemonEngineBridge
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.state.TickFrames
import com.maxixa.lemon.state.*

/**
 * Values owned by the playlist's load-and-sync pipeline. Extracted from
 * `PlaylistScreen` to keep the composable under the 150-LOC ceiling.
 *
 * `rememberPlaylistSync` owns all the LaunchedEffects that load clips,
 * markers, arrangements, and peaks, plus playhead derivation and
 * auto-follow. Its returned `PlaylistSync` is what Screen actually uses.
 */
data class PlaylistSync(
    val patternNames: List<String>,
    val arrangements: List<Pair<Int, String>>,
    val audioClipPeaks: Map<Int, FloatArray>,
    val automationPoints: Map<Int, List<Pair<Double, Double>>>,
    val reloadTick: Int,
    val bumpReloadTick: () -> Unit,
    val playheadTicks: Int,
)

@Composable
fun rememberPlaylistSync(
    handle: Long,
    engine: EngineController,
    state: PlaylistState,
): PlaylistSync {
    var patternNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var arrangements by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var reloadTick by remember { mutableStateOf(0) }
    var audioClipPeaks by remember { mutableStateOf<Map<Int, FloatArray>>(emptyMap()) }
    var automationPoints by remember { mutableStateOf<Map<Int, List<Pair<Double, Double>>>>(emptyMap()) }

    LaunchedEffect(handle, engine.currentArrangement, engine.revision, reloadTick) {
        try { LemonEngineBridge.engineSetPlayMode(handle, true) } catch (_: Throwable) {}
        state.songMode = true
        val loaded = PlaylistBridge.load(handle)
        state.trackCount = loaded.trackCount.coerceAtLeast(8)
        state.patternCount = loaded.patternCount
        state.currentArrangement = loaded.currentArrangement
        state.barNum = engine.timeSigNum
        state.barDen = engine.timeSigDen
        state.loadClips(loaded.clips)
        val peaksMap = mutableMapOf<Int, FloatArray>()
        for (c in loaded.clips) {
            if (c.sourceKind == 1 && c.pattern !in peaksMap) {
                val p = engine.channelPeaks(c.pattern)
                if (p.isNotEmpty()) peaksMap[c.pattern] = p
            }
        }
        val autoMap = mutableMapOf<Int, List<Pair<Double, Double>>>()
        for (c in loaded.clips) {
            if (c.sourceKind == 1 && c.pattern !in autoMap) {
                val pts = engine.automationPoints(c.pattern)
                if (pts.isNotEmpty()) autoMap[c.pattern] = pts
            }
        }
        automationPoints = autoMap
        state.automationClipChannels = autoMap.keys
        audioClipPeaks = peaksMap
        state.markers.clear()
        loaded.markers.forEach { state.markers.add(it) }
        patternNames = (0 until loaded.patternCount).map { i ->
            try { LemonEngineBridge.enginePatternName(handle, i) }
            catch (_: Throwable) { "Pattern ${i + 1}" }
        }
        val arrCount = try { LemonEngineBridge.engineArrangementCount(handle) } catch (_: Throwable) { 0 }
        arrangements = (0 until arrCount).map { i ->
            val num = try { LemonEngineBridge.engineArrangementNumber(handle, i) } catch (_: Throwable) { i }
            val nm = try { LemonEngineBridge.engineArrangementName(handle, i) } catch (_: Throwable) { "Arrangement $num" }
            num to nm
        }
    }

    LaunchedEffect(engine.loopRangeStart, engine.loopRangeEnd) {
        state.loopStart = engine.loopRangeStart.toInt()
        state.loopEnd = engine.loopRangeEnd.toInt()
    }

    LaunchedEffect(engine.timeSigNum, engine.timeSigDen) {
        state.barNum = engine.timeSigNum
        state.barDen = engine.timeSigDen
    }

    val playheadTicks: Int = remember(
        engine.positionFrames, engine.sampleRate, engine.bpm, state.ppq
    ) {
        TickFrames.positionTicks(
            engine.positionFrames, state.ppq, engine.bpm, engine.sampleRate
        )
    }

    LaunchedEffect(playheadTicks, engine.isPlaying) {
        if (engine.isPlaying) state.followPlayhead(playheadTicks, 800f)
    }

    return PlaylistSync(
        patternNames = patternNames,
        arrangements = arrangements,
        audioClipPeaks = audioClipPeaks,
        automationPoints = automationPoints,
        reloadTick = reloadTick,
        bumpReloadTick = { reloadTick++ },
        playheadTicks = playheadTicks,
    )
}
