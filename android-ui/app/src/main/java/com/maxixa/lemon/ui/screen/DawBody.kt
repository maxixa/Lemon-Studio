package com.maxixa.lemon.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.ui.component.TopStatusBar
import com.maxixa.lemon.ui.component.TrackList
import com.maxixa.lemon.ui.component.TransportBar
import com.maxixa.lemon.ui.mixer.MixerScreen
import com.maxixa.lemon.ui.piano.PianoRollScreen
import com.maxixa.lemon.ui.playlist.PlaylistScreen
import com.maxixa.lemon.ui.rack.StepSequencerScreen
import com.maxixa.lemon.ui.theme.BgDeep
import com.maxixa.lemon.state.*

@Composable
internal fun DawBody(
    engine: EngineController,
    view: DawView,
    onViewChange: (DawView) -> Unit,
    isFullscreen: Boolean,
    isZen: Boolean,
    zenReveal: Boolean,
    onToggleFullscreen: () -> Unit,
    onToggleZen: () -> Unit,
    onZenReveal: () -> Unit,
    patternNames: List<String>,
    channelLabels: List<String>,
    onOpenProject: () -> Unit,
    onOpenRecent: () -> Unit,
    onSave: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenFactory: () -> Unit,
    onOpenTempo: () -> Unit,
    onOpenMetronome: () -> Unit,
    onOpenLoopRange: () -> Unit,
    onAddChannel: () -> Unit,
    onRecordToggle: () -> Unit,
    onLongPressChannel: (Int, String) -> Unit,
    onRefetchPatternNames: () -> Unit,
    onTimeSigTap: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(BgDeep)) {
        if ((!isZen || zenReveal) && view == DawView.Tracks) {
            TopStatusBar(
                bpm = engine.bpm,
                positionFrames = engine.positionFrames,
                sampleRate = engine.sampleRate,
                isFullscreen = isFullscreen,
                isZen = isZen,
                canUndo = engine.canUndo,
                canRedo = engine.canRedo,
                masterPeak = engine.masterPeak,
                onOpenProject = onOpenProject,
                onOpenRecent = onOpenRecent,
                onSaveProject = { onSave(true) },
                onSaveAs = { onSave(false) },
                onOpenSettings = onOpenSettings,
                onOpenPianoRoll = { onViewChange(DawView.PianoRoll) },
                onOpenRack = { onViewChange(DawView.StepSequencer) },
                onOpenPlaylist = { onViewChange(DawView.Playlist) },
                onOpenMixer = { onViewChange(DawView.Mixer) },
                onOpenExport = onOpenExport,
                onOpenFactory = onOpenFactory,
                onAddChannel = onAddChannel,
                onToggleFullscreen = onToggleFullscreen,
                onToggleZen = onToggleZen,
                onTempoTap = onOpenTempo,
                onUndo = { engine.undo() },
                onRedo = { engine.redo() },
            )
        }

        when (view) {
            DawView.Tracks -> DawTracksView(
                engine = engine,
                isZen = isZen,
                onZenReveal = onZenReveal,
                onLongPressChannel = onLongPressChannel,
                onRecordToggle = onRecordToggle,
                onOpenMetronome = onOpenMetronome,
                onOpenLoopRange = onOpenLoopRange,
                onTimeSigTap = onTimeSigTap,
            )

            DawView.PianoRoll -> PianoRollScreen(
                handle = engine.handleRef,
                patternNum = engine.currentPattern,
                trackCount = engine.tracks.size,
                patternLabel = (patternNames.getOrNull(engine.currentPattern - 1) ?: "Pattern ${engine.currentPattern}"),
                patternNames = patternNames,
                revision = engine.revision,
                onSeekFrames = { frames -> engine.seek(frames) },
                channelLabels = channelLabels,
                positionFrames = engine.positionFrames,
                sampleRate = engine.sampleRate,
                bpm = engine.bpm,
                barNum = engine.timeSigNum,
                barDen = engine.timeSigDen,
                isPlaying = engine.isPlaying,
                onSetPattern = { num -> engine.setPattern(num) },
                onBack = { onViewChange(DawView.Tracks) },
            )

            DawView.StepSequencer -> StepSequencerScreen(
                handle = engine.handleRef,
                trackNames = engine.tracks,
                patternNum = engine.currentPattern,
                patternNames = patternNames,
                revision = engine.revision,
                onPickPattern = { num -> engine.setPattern(num) },
                onAddPattern = { engine.addPattern(); onRefetchPatternNames() },
                onDuplicatePattern = { num -> engine.duplicatePattern(num); onRefetchPatternNames() },
                onRenamePattern = { num, name -> engine.renamePattern(num, name); onRefetchPatternNames() },
                onBack = { onViewChange(DawView.Tracks) },
            )

            DawView.Playlist -> PlaylistScreen(
                handle = engine.handleRef,
                engine = engine,
                onBack = { onViewChange(DawView.Tracks) },
            )

            DawView.Mixer -> MixerScreen(
                handle = engine.handleRef,
                engine = engine,
                onBack = { onViewChange(DawView.Tracks) },
            )
        }
    }
}
