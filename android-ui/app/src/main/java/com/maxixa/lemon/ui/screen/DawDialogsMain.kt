package com.maxixa.lemon.ui.screen

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.state.ExportController
import com.maxixa.lemon.state.ExportFormat
import com.maxixa.lemon.state.PathsStore
import com.maxixa.lemon.state.TickFrames
import com.maxixa.lemon.ui.export.ExportDialog
import com.maxixa.lemon.ui.export.ExportOverlay
import com.maxixa.lemon.ui.record.RecordDialog
import com.maxixa.lemon.ui.settings.FactoryDialog
import com.maxixa.lemon.ui.settings.LoopRangeDialog
import com.maxixa.lemon.ui.settings.MetronomeDialog
import com.maxixa.lemon.ui.settings.TempoDialog
import com.maxixa.lemon.ui.settings.TimeSigDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun DawDialogsMain(
    engine: EngineController,
    exportController: ExportController,
    pathsStore: PathsStore,
    defaultFactory: String,
    snackbarHost: SnackbarHostState,
    scope: CoroutineScope,
    snackbarModifier: Modifier,
    showExportDialog: Boolean,
    onDismissExportDialog: () -> Unit,
    onPendingExport: (ExportRequest) -> Unit,
    onWithStems: (Boolean, ExportFormat) -> Unit,
    showTempoDialog: Boolean,
    onDismissTempoDialog: () -> Unit,
    showTimeSigDialog: Boolean,
    onDismissTimeSigDialog: () -> Unit,
    showMetronomeDialog: Boolean,
    onDismissMetronomeDialog: () -> Unit,
    showLoopRangeDialog: Boolean,
    onDismissLoopRangeDialog: () -> Unit,
    showFactoryDialog: Boolean,
    onDismissFactoryDialog: () -> Unit,
    showRecordDialog: Boolean,
    onDismissRecordDialog: () -> Unit,
    onRecordingComplete: (File) -> Unit,
) {
    if (showExportDialog) {
        ExportDialog(
            onConfirm = { song, rate, fmt, withStems ->
                onPendingExport(ExportRequest(song, rate, fmt, withStems))
                onDismissExportDialog()
                onWithStems(withStems, fmt)
            },
            onDismiss = onDismissExportDialog,
        )
    }

    if (showTempoDialog) {
        TempoDialog(
            initialBpm = engine.bpm,
            onConfirm = { v -> engine.setTempo(v); onDismissTempoDialog() },
            onDismiss = onDismissTempoDialog,
        )
    }

    if (showTimeSigDialog) {
        TimeSigDialog(
            currentNum = engine.timeSigNum,
            currentDen = engine.timeSigDen,
            onApply = { num, den ->
                engine.setTimeSignature(num, den)
                onDismissTimeSigDialog()
            },
            onDismiss = onDismissTimeSigDialog,
        )
    }

    if (showMetronomeDialog) {
        MetronomeDialog(
            currentSound = engine.metronomeSound,
            currentVolumeDb = engine.metronomeVolumeDb,
            currentCountIn = engine.countInBars,
            onSoundChange = { engine.setMetronomeSound(it) },
            onVolumeChange = { engine.setMetronomeVolume(it) },
            onCountInChange = { engine.setCountIn(it) },
            onDismiss = onDismissMetronomeDialog,
        )
    }

    if (showLoopRangeDialog) {
        LoopRangeDialog(
            barNum = engine.timeSigNum,
            barDen = engine.timeSigDen,
            ppq = engine.previewPpq(),
            initialStart = engine.loopRangeStart,
            initialEnd = engine.loopRangeEnd,
            onSet = { s, e -> engine.setLoopRange(s, e) },
            onClear = { engine.clearLoopRange() },
            onDismiss = onDismissLoopRangeDialog,
        )
    }

    if (showFactoryDialog) {
        FactoryDialog(
            currentPath = engine.factory(),
            defaultPath = defaultFactory,
            onSave = { path ->
                pathsStore.factoryPath = path
                if (path.isEmpty()) engine.setFactory("") else engine.setFactory(path)
                onDismissFactoryDialog()
            },
            onDismiss = onDismissFactoryDialog,
        )
    }

    if (showRecordDialog) {
        RecordDialog(
            sampleRate = engine.sampleRate,
            onComplete = { file -> onDismissRecordDialog(); onRecordingComplete(file) },
            onDismiss = onDismissRecordDialog,
        )
    }

    if (exportController.isExporting) {
        ExportOverlay()
    }

    SnackbarHost(hostState = snackbarHost, modifier = snackbarModifier)
}
