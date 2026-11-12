package com.maxixa.lemon.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.maxixa.lemon.LemonEngineBridge
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.ui.theme.BgDeep
import com.maxixa.lemon.state.LocalSettings
import com.maxixa.lemon.state.*

/**
 * Playlist screen — orchestration only.
 * Load-and-sync lives in `rememberPlaylistSync` (PlaylistSync.kt).
 * Dialog rendering lives in `PlaylistDialogHost` (PlaylistDialogHost.kt).
 * Toolbar and canvas stay here as the visual body.
 */
@Composable
fun PlaylistScreen(
    handle: Long,
    engine: EngineController,
    onBack: () -> Unit,
) {
    val settings = LocalSettings.current
    val state = remember { PlaylistState(settings) }
    val sync = rememberPlaylistSync(handle, engine, state)
    var showStretchFor by remember { mutableStateOf<Long?>(null) }
    var clipboard by remember { mutableStateOf<List<ClipView>>(emptyList()) }

    var showAdd by remember { mutableStateOf(false) }
    var showAddAutomation by remember { mutableStateOf(false) }
    var showLoopDialog by remember { mutableStateOf(false) }

    val commit: () -> Unit = {
        PlaylistBridge.commit(
            handle,
            state.trackCount,
            state.currentArrangement,
            state.barNum,
            state.clips,
            state.markers,
        )
    }


    val copySelected: () -> Unit = {

        val sel = state.clips.filter { it.selected }

        if (sel.isNotEmpty()) {

            clipboard = sel.map { it.copy(id = 0L, selected = false) }

        }

    }
    val cutSelected: () -> Unit = {
        val sel = state.clips.filter { it.selected }
        if (sel.isNotEmpty()) {
            clipboard = sel.map { it.copy(id = 0L, selected = false) }
            state.deleteSelected()
            commit()
        }
    }


    val pasteAtPlayhead: () -> Unit = {

        if (clipboard.isNotEmpty()) {

            val base = sync.playheadTicks

            val minPos = clipboard.minOf { it.position }

            for (i in state.clips.indices) {

                state.clips[i] = state.clips[i].copy(selected = false)

            }

            for (clip in clipboard) {

                state.addClipRaw(clip.copy(

                    position = (base + (clip.position - minPos)).coerceAtLeast(0),

                    selected = true,

                ))

            }

            commit()

        }

    }
    val commitSilent: () -> Unit = {
        PlaylistBridge.commit(
            handle,
            state.trackCount,
            state.currentArrangement,
            state.barNum,
            state.clips,
            state.markers,
            silent = true,
        )
    }
    val launchAudioPicker = rememberAudioClipFlow(engine, state, commitSilent)
    rememberTrimFlow(engine, state)

    Column(Modifier.fillMaxSize().background(BgDeep)) {
        PlaylistToolbar(
            songMode = state.songMode,
            tool = state.tool,
            hasSelection = state.clips.any { it.selected },
            onStretch = {
                val sel = state.clips.filter {
                    it.selected && it.sourceKind == 1 &&
                        runCatching { engine.channelSampleFrames(it.pattern) > 0 }.getOrDefault(false)
                }
                if (sel.size == 1) showStretchFor = sel[0].id
            },
            canStretch = (state.clips.count {
                it.selected && it.sourceKind == 1 &&
                    runCatching { engine.channelSampleFrames(it.pattern) > 0 }.getOrDefault(false)
            }) == 1,
            onCopy = copySelected,
            onCut = cutSelected,
            onPaste = pasteAtPlayhead,
            clipboardSize = clipboard.size,
            isPlaying = engine.isPlaying,
            onBack = {
                try { LemonEngineBridge.engineSetPlayMode(handle, false) } catch (_: Throwable) {}
                onBack()
            },
            onToggleMode = {
                val song = !state.songMode
                if (LemonEngineBridge.engineSetPlayMode(handle, song)) {
                    state.songMode = song
                }
            },
            onPlayToggle = { engine.togglePlay() },
            onToolChange = { state.tool = it },
            onAddClip = { showAdd = true },
            onAddAudioClip = launchAudioPicker,
            onDuplicate = {
                state.duplicateSelected(state.ppq * 4)
                commit()
            },
            onDeleteSelected = {
                state.deleteSelected()
                commit()
            },
            loopActive = engine.loopRangeStart >= 0L && engine.loopRangeEnd > engine.loopRangeStart,
            snapMode = state.snapMode,
            onSnapCycle = { state.snapMode = (state.snapMode + 1) % 4 },
            arrangementLabel = sync.arrangements.firstOrNull { it.first == engine.currentArrangement }?.second ?: "Arr",
            onArrangementClick = { state.showArrangementPicker = true },
            barsVisible = state.barsVisible,
            trimMode = state.trimMode,
            onToggleTrimMode = { state.trimMode = !state.trimMode },
            onBarsVisibleChange = { state.barsVisible = it },
            onLoopClick = { showLoopDialog = true },
            onAddMarker = {
                val barTicks = state.ppq * 4
                val snapped = (sync.playheadTicks / barTicks) * barTicks
                state.addMarker(snapped, "Marker ${state.markers.size + 1}")
                commit()
            },
onAddAutomation = { showAddAutomation = true },
        )
        PlaylistCanvas(
            state = state,
            playheadTicks = sync.playheadTicks,
            onCommit = commit,
            onLoopRangeChange = { s, e -> engine.setLoopRange(s.toLong(), e.toLong()) },
            audioClipPeaks = sync.audioClipPeaks,
            automationPoints = sync.automationPoints,
            modifier = Modifier.weight(1f),
                patternNotes = sync.patternNotes,)
    }

    PlaylistDialogHost(
        state = state,
        engine = engine,
        patternNames = sync.patternNames,
        arrangements = sync.arrangements,
        automationPoints = sync.automationPoints,
        showAdd = showAdd,
        onDismissAdd = { showAdd = false },
        onAddClip = { pat ->
            state.addClip(0, state.scrollX.toInt(), 384, pat)
            showAdd = false
            commit()
        },
        showAddAutomation = showAddAutomation,
        onDismissAddAutomation = { showAddAutomation = false },
        onCreateAutomation = { target, value ->
            val idx = engine.addAutomationChannel(target, value)
            if (idx >= 0) {
                state.addChannelClip(0, state.scrollX.toInt(), state.barTicks(), idx)
                commit()
            }
            showAddAutomation = false
        },
        showLoopDialog = showLoopDialog,
        onDismissLoop = { showLoopDialog = false },
        commit = commit,
        onReloadTick = sync.bumpReloadTick,
        onAddAudioClip = {
            showAdd = false
            launchAudioPicker()
        },
        onAddAutomationClip = {
            showAdd = false
            showAddAutomation = true
        },
    )

    val stretchClip = state.clips.firstOrNull { it.id == showStretchFor }
    if (stretchClip != null) {
        PlaylistStretchDialog(
            engine = engine,
            clip = stretchClip,
            sharedCount = state.clips.count {
                it.sourceKind == 1 && it.pattern == stretchClip.pattern
            },
            onApplied = { showStretchFor = null },
            onDismiss = { showStretchFor = null },
        )
    }

}
