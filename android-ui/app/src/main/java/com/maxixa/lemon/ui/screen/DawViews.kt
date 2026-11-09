package com.maxixa.lemon.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.ui.component.TrackList
import com.maxixa.lemon.ui.component.TransportBar

/**
 * Tracks view body — extracted from DawBody to keep the routing
 * composable under the 150-LOC ceiling after time-signature wiring.
 *
 * Owns nothing. Renders TrackList + TransportBar. All state lives
 * in DawBody/DawScreen.
 */
@Composable
internal fun ColumnScope.DawTracksView(
    engine: EngineController,
    isZen: Boolean,
    onZenReveal: () -> Unit,
    onLongPressChannel: (Int, String) -> Unit,
    onRecordToggle: () -> Unit,
    onOpenMetronome: () -> Unit,
    onOpenLoopRange: () -> Unit,
    onTimeSigTap: () -> Unit,
) {
    Box(
        Modifier.weight(1f).pointerInput(isZen) {
            if (isZen) detectTapGestures { onZenReveal() }
        }
    ) {
        TrackList(
            names = engine.tracks,
            handle = engine.handleRef,
            peaks = engine.trackPeaks,
            colors = engine.trackColors,
            onLongPress = onLongPressChannel,
            modifier = Modifier.fillMaxSize(),
        )
    }
    TransportBar(
        isPlaying = engine.isPlaying,
        isRecording = engine.isRecording,
        isLooping = engine.isLooping,
        isMetronomeOn = engine.metronomeOn,
        loopRangeStart = engine.loopRangeStart,
        loopRangeEnd = engine.loopRangeEnd,
        onPlayToggle = { engine.togglePlay() },
        onStop = { engine.stop() },
        onRecordToggle = onRecordToggle,
        onLoopToggle = { engine.applyLooping(!engine.isLooping) },
        onMetronomeToggle = { engine.toggleMetronome() },
        onMetronomeLongPress = onOpenMetronome,
        onLoopRangeTap = onOpenLoopRange,
        onRewind = { engine.rewind() },
        onForward = { engine.forward() },
        timeSigNum = engine.timeSigNum,
        timeSigDen = engine.timeSigDen,
        onTimeSigTap = onTimeSigTap,
    )
}
