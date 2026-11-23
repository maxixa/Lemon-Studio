package com.maxixa.lemon.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.state.ExportController
import com.maxixa.lemon.state.ExportFormat
import com.maxixa.lemon.state.PathsStore
import com.maxixa.lemon.state.RecentProject
import com.maxixa.lemon.state.RecentProjectsStore
import com.maxixa.lemon.state.TickFrames
import com.maxixa.lemon.ui.theme.SystemBarEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.maxixa.lemon.state.*
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

internal enum class DawView { Tracks, PianoRoll, StepSequencer, Playlist, Mixer }

internal data class ExportRequest(
    val songMode: Boolean,
    val rate: Int,
    val format: ExportFormat,
    val withStems: Boolean,
        val wavBits: Int = 32,
        val mp3Kbps: Int = 192,
    )

@Composable
fun DawScreen(
    engine: EngineController,
    onOpenSettings: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val pathsStore = remember { PathsStore(ctx) }
    val defaultFactory = remember { pathsStore.ensureDefaultFactory(ctx) }
    val exportController = remember { ExportController(engine.handleRef) }
    val recentStore = remember { RecentProjectsStore(ctx) }

    var recentProjects by remember { mutableStateOf<List<RecentProject>>(emptyList()) }
    var view by remember { mutableStateOf(DawView.Tracks) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isZen by remember { mutableStateOf(false) }
    var zenReveal by remember { mutableStateOf(false) }
    var patternNames by remember { mutableStateOf<List<String>>(emptyList()) }

    var showRecentDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showTempoDialog by remember { mutableStateOf(false) }
    var showTimeSigDialog by remember { mutableStateOf(false) }
    var showMetronomeDialog by remember { mutableStateOf(false) }
    var showLoopRangeDialog by remember { mutableStateOf(false) }
    var showFactoryDialog by remember { mutableStateOf(false) }
    var showRecordDialog by remember { mutableStateOf(false) }
    var showExtensions by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ExportRequest?>(null) }
    var renameTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var pendingSampleTarget by remember { mutableStateOf<Int?>(null) }
    var showInstrumentPicker by remember { mutableStateOf<Int?>(null) }
    var showColorPicker by remember { mutableStateOf<Int?>(null) }
    var showSampleEditor by remember { mutableStateOf<Int?>(null) }

    val channelLabels = remember(engine.tracks, engine.trackPeaks) {
        val catalog = engine.instrumentCatalog()
        engine.tracks.mapIndexed { i, name ->
            val instId = try { engine.channelInstrument(i) } catch (_: Throwable) { "" }
            val instLabel = catalog.firstOrNull { it.first == instId }?.second ?: instId
            if (instLabel.isEmpty()) name else "$name · $instLabel"
        }
    }

    SystemBarEffect(hide = isFullscreen)
    DawBootstrap(
        engine, pathsStore, recentStore,
        onRecentProjects = { recentProjects = it },
        onPatternNames = { patternNames = it },
    )

    val coreLaunchers = rememberCoreLaunchers(
        engine, ctx, scope, snackbarHost, recentStore,
        pendingSampleTarget = pendingSampleTarget,
        onSampleTargetConsumed = { pendingSampleTarget = null },
        onRecentProjectsReload = { recentProjects = recentStore.load() },
        onShowRecordDialog = { showRecordDialog = true },
    )
    val exportLaunchers = rememberExportLaunchers(
        engine, ctx, scope, snackbarHost, exportController,
        pendingExport = pendingExport,
        onExportConsumed = { pendingExport = null },
    )

    LaunchedEffect(engine.isPlaying) {
        while (engine.isPlaying) {
            engine.tickPosition()
            engine.refreshPeak()
            delay(50)
        }
        engine.clearPeak()
    }
    LaunchedEffect(zenReveal) {
        if (zenReveal) { delay(2500); zenReveal = false }
    }

    fun refetchPatternNames() {
        val n = try { com.maxixa.lemon.LemonEngineBridge.enginePatternCount(engine.handleRef) }
                catch (_: Throwable) { 0 }
        patternNames = (0 until n).map { i ->
            try { com.maxixa.lemon.LemonEngineBridge.enginePatternName(engine.handleRef, i) }
            catch (_: Throwable) { "Pattern ${i + 1}" }
        }
    }

    val doSave: (Boolean) -> Unit = { useLast ->
        val last = recentStore.lastSaveUri
        if (useLast && last.isNotEmpty()) {
            scope.launch {
                try {
                    val uri = android.net.Uri.parse(last)
                    val pfd = ctx.contentResolver.openFileDescriptor(uri, "w")
                    if (pfd == null) {
                        snackbarHost.showSnackbar("Cannot access saved file — pick new location")
                        return@launch
                    }
                    pfd.use {
                        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            engine.saveProject(it.fd)
                        }
                        snackbarHost.showSnackbar(
                            if (bytes > 0) "Saved $bytes bytes" else "Save failed"
                        )
                    }
                } catch (t: Throwable) {
                    snackbarHost.showSnackbar("Save error: ${t.message}")
                }
            }
        } else {
            coreLaunchers.quickSaveLauncher.launch(
                "lemon-project-${System.currentTimeMillis() / 1000}.flp"
            )
        }
    }

    val onAddChannel: () -> Unit = {
        val idx = engine.addSamplerChannel()
        scope.launch {
            snackbarHost.showSnackbar(
                if (idx >= 0) "Added channel ${idx + 1}" else "Add channel failed"
            )
        }
    }
    val onRecordToggle: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) showRecordDialog = true
        else coreLaunchers.recordPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(Modifier.fillMaxSize()) {
        DawBody(
            engine = engine, view = view,
            onViewChange = { view = it },
            isFullscreen = isFullscreen, isZen = isZen, zenReveal = zenReveal,
            onToggleFullscreen = { isFullscreen = !isFullscreen },
            onToggleZen = { isZen = !isZen; zenReveal = false },
            onZenReveal = { zenReveal = true },
            patternNames = patternNames, channelLabels = channelLabels,
            onOpenProject = { coreLaunchers.openPicker.launch(arrayOf("*/*")) },
            onOpenRecent = { showRecentDialog = true },
            onSave = { doSave(it) },
            onOpenSettings = onOpenSettings,
            onOpenExport = { showExportDialog = true },
            onOpenFactory = { showFactoryDialog = true },
            onOpenTempo = { showTempoDialog = true },
            onTimeSigTap = { showTimeSigDialog = true },
            onOpenMetronome = { showMetronomeDialog = true },
            onOpenLoopRange = { showLoopRangeDialog = true },
            onAddChannel = onAddChannel,
            onRecordToggle = onRecordToggle,
            onLongPressChannel = { i, name -> renameTarget = i to name },
            onRefetchPatternNames = { refetchPatternNames() },
        )

        if (exportController.isExporting) {
            com.maxixa.lemon.ui.export.ExportOverlay()
        }
        DawDialogsMain(
            engine = engine, exportController = exportController,
            pathsStore = pathsStore, defaultFactory = defaultFactory,
            snackbarHost = snackbarHost, scope = scope,
            snackbarModifier = Modifier.align(Alignment.BottomCenter),
            showExportDialog = showExportDialog,
            onDismissExportDialog = { showExportDialog = false },
            onPendingExport = { pendingExport = it },
            onWithStems = { withStems, fmt, wavBits ->
                if (withStems) exportLaunchers.treePicker.launch(null)
                else exportLaunchers.createDoc.launch("lemon-export.${fmt.ext}")
            },
            showTempoDialog = showTempoDialog,
            onDismissTempoDialog = { showTempoDialog = false },
            showTimeSigDialog = showTimeSigDialog,
            onDismissTimeSigDialog = { showTimeSigDialog = false },
            showMetronomeDialog = showMetronomeDialog,
            onDismissMetronomeDialog = { showMetronomeDialog = false },
            showLoopRangeDialog = showLoopRangeDialog,
            onDismissLoopRangeDialog = { showLoopRangeDialog = false },
            showFactoryDialog = showFactoryDialog,
            onDismissFactoryDialog = { showFactoryDialog = false },
            showRecordDialog = showRecordDialog,
            onDismissRecordDialog = { showRecordDialog = false },
            onRecordingComplete = { file ->
                scope.launch {
                    val ticks = TickFrames.positionTicks(
                        engine.positionFrames, 96, engine.bpm, engine.sampleRate
                    )
                    val idx = engine.addSampleChannel(file.absolutePath, ticks)
                    snackbarHost.showSnackbar(
                        if (idx >= 0) "Recorded to channel ${idx + 1}"
                        else "Failed to add recording"
                    )
                }
            },
        )

        DawDialogsChannel(
            engine = engine, scope = scope, snackbarHost = snackbarHost,
            samplePickerLauncher = coreLaunchers.samplePickerLauncher,
            renameTarget = renameTarget,
            onRenameTargetConsumed = { renameTarget = null },
            onOpenSampleEditor = { showSampleEditor = it },
            onOpenColorPicker = { showColorPicker = it },
            onOpenInstrumentPicker = { showInstrumentPicker = it },
            onLoadSampleFor = { pendingSampleTarget = it },
            showInstrumentPicker = showInstrumentPicker,
            onDismissInstrumentPicker = { showInstrumentPicker = null },
        )

        DawDialogsMisc(
            engine = engine, ctx = ctx, scope = scope, snackbarHost = snackbarHost,
            recentStore = recentStore, openPicker = coreLaunchers.openPicker,
            recentProjects = recentProjects,
            onRecentProjectsReload = { recentProjects = recentStore.load() },
            showRecentDialog = showRecentDialog,
            onDismissRecentDialog = { showRecentDialog = false },
            showColorPicker = showColorPicker,
            onDismissColorPicker = { showColorPicker = null },
            showSampleEditor = showSampleEditor,
            onDismissSampleEditor = { showSampleEditor = null },
        )

        // Extension FAB — bottom-end
        androidx.compose.material3.FloatingActionButton(
            onClick = { showExtensions = true },
            modifier = androidx.compose.ui.Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            androidx.compose.material3.Text("+", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        }
    }

    // ── Extension menu (song generators) ──
    if (showExtensions) {
        com.maxixa.lemon.ui.ext.ExtensionsMenu(
            onDismiss = { showExtensions = false },
            onApply = { id, params ->
                val summary = com.maxixa.lemon.ExtHost.generateAndApply(
                    engine.handleRef, id, params
                )
                scope.launch {
                    if (summary?.ok == true) {
                        snackbarHost.showSnackbar(
                            "Generated: ${summary.tracks} tracks · ${summary.notes} notes · ${summary.bars} bars"
                        )
                    } else {
                        snackbarHost.showSnackbar(
                            "Generate failed: ${summary?.warnings?.joinToString() ?: "unknown"}"
                        )
                    }
                }
            },
            kindFilter = "song-generator",
        )
    }
}
