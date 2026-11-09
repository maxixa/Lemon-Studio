package com.maxixa.lemon.ui.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.maxixa.lemon.state.EngineController

/**
 * Syncs state used by the trim gesture:
 *  - `state.bpm`: needed for ms↔tick conversion in the trim gesture
 *  - `state.audioClipDurations`: channel → sample duration in ms, for
 *    clamping trim range to what actually exists
 *
 * Called once per playlist composition. LaunchedEffects refire when
 * dependencies change (bpm change, clips added/removed, engine revision
 * bump from undo/redo).
 */
@Composable
fun rememberTrimFlow(
    engine: EngineController,
    state: PlaylistState,
) {
    LaunchedEffect(engine.bpm) {
        state.bpm = engine.bpm
    }
    LaunchedEffect(state.clips.size, engine.revision) {
        val map = mutableMapOf<Int, Float>()
        for (c in state.clips) {
            if (c.sourceKind == 1 && c.pattern !in map) {
                val frames = try { engine.channelSampleFrames(c.pattern) }
                             catch (_: Throwable) { 0L }
                if (frames > 0 && engine.sampleRate > 0) {
                    map[c.pattern] = frames.toFloat() / engine.sampleRate * 1000f
                }
            }
        }
        state.audioClipDurations = map.toMap()
    }
}
