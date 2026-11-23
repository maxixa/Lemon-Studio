package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.maxixa.lemon.LemonEngineBridge
import com.maxixa.lemon.state.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

/**
 * Piano roll orchestrator — state, effects, dialog visibility.
 * The rendered body lives in PianoRollBody; dialogs in PianoRollDialogHost.
 * Quantize and Chord have their own standalone dialogs (not in DialogHost)
 * because they don't open from toolbar chips — Quantize operates on the
 * selection, Chord inserts at playhead with no selection needed.
 *
 * Chord FAB + dialog live in PianoRollChordSection.
 */
@Composable
fun PianoRollScreen(
    handle: Long,
    patternNum: Int = 1,
    trackCount: Int = 0,
    positionFrames: Long,
    sampleRate: Int,
    bpm: Float = 130f,
    isPlaying: Boolean,
    patternLabel: String = "",
    patternNames: List<String> = emptyList(),
    channelLabels: List<String> = emptyList(),
    revision: Long = 0L,
    onSetPattern: (Int) -> Unit = {},
    onSeekFrames: (Long) -> Unit = {},
    onBack: () -> Unit,
    barNum: Int = 4,
    barDen: Int = 4,
) {
    val settings = LocalSettings.current
    val state = remember { PianoRollState(settings) }
    var showZoom by remember { mutableStateOf(false) }
    var showArpDialog by remember { mutableStateOf(false) }
    var showTransform by remember { mutableStateOf(false) }
    var showGroove by remember { mutableStateOf(false) }
    var showChannelPicker by remember { mutableStateOf(false) }
    var showPatternPicker by remember { mutableStateOf(false) }
    var showPatternLength by remember { mutableStateOf(false) }
    var showQuantize by remember { mutableStateOf(false) }
    var showExtensions by remember { mutableStateOf(false) }
    var showChord by remember { mutableStateOf(false) }
    var showScale by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    var laneField by remember { mutableStateOf(LaneField.Velocity) }

    LaunchedEffect(handle, patternNum, trackCount, revision, reloadTick) {
        PianoRollBridge.load(handle, state)
    }

    LaunchedEffect(barNum, barDen) {
        state.barNum = barNum.coerceAtLeast(1)
        state.barDen = barDen.coerceAtLeast(1)
    }

    val playheadTicks: Int = remember(positionFrames, sampleRate, state.ppq, bpm) {
        TickFrames.positionTicks(positionFrames, state.ppq, bpm, sampleRate)
    }

    LaunchedEffect(playheadTicks, isPlaying) {
        if (isPlaying) state.followPlayhead(playheadTicks)
    }

    val commit: () -> Unit = { PianoRollBridge.commit(handle, state) }
    val activeChannelLabel = channelLabels.getOrNull(state.targetChannel) ?: ""
    val onSeekTickWrapped: (Int) -> Unit = { tick ->
        onSeekFrames(TickFrames.framesForTicks(tick, state.ppq, bpm, sampleRate))
    }

    Box(Modifier.fillMaxSize()) {
        PianoRollBody(
            state = state,
            playheadTicks = playheadTicks,
            laneField = laneField,
            onLaneFieldChange = { laneField = it },
            commit = commit,
            patternLabel = patternLabel,
            channelLabel = activeChannelLabel,
            onBack = onBack,
            onOpenZoom = { showZoom = true },
            onOpenTransform = { showTransform = true },
            onOpenGroove = { showGroove = true },
            onOpenChannelPicker = { showChannelPicker = true },
            onOpenPatternPicker = { showPatternPicker = true },
            onOpenPatternLength = { showPatternLength = true },
            onOpenQuantize = { showQuantize = true },
            onSeekTick = onSeekTickWrapped,
        )
        PianoRollChordSection(
            showChord = showChord,
            onShowChordChange = { showChord = it },
            playheadTicks = playheadTicks,
            state = state,
            onApplied = commit,
        )
        PianoRollScaleSection(
            showScale = showScale,
            onShowScaleChange = { showScale = it },
            state = state,
            onApplied = commit,
        )

        // Extension editor trigger
        androidx.compose.material3.ExtendedFloatingActionButton(
            onClick = { showExtensions = true },
            modifier = androidx.compose.ui.Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            androidx.compose.material3.Text("fx")
        }
    }

    PianoRollDialogHost(
        state = state,
        showZoom = showZoom,
        showTransform = showTransform,
        showGroove = showGroove,
        showChannelPicker = showChannelPicker,
        showPatternPicker = showPatternPicker,
        showPatternLength = showPatternLength,
        channelLabels = channelLabels,
        patternNames = patternNames,
        currentPattern = patternNum,
        onSetPattern = { num -> onSetPattern(num) },
        onSetPatternLength = { ticks ->
            val ok = try {
                LemonEngineBridge.engineSetPatternLength(handle, ticks)
            } catch (t: Throwable) { false }
            if (ok) reloadTick++
        },
        onCommit = commit,
        onDismissZoom = { showZoom = false },
        onDismissTransform = { showTransform = false },
        onDismissGroove = { showGroove = false },
        onDismissChannelPicker = { showChannelPicker = false },
        onDismissPatternPicker = { showPatternPicker = false },
        onDismissPatternLength = { showPatternLength = false },
    )

    if (showQuantize) {
        QuantizeDialog(
            state = state,
            onApply = { snapTicks, strength, swing, ends ->
                state.quantizeWithSwing(snapTicks, strength, swing, ends)
                showQuantize = false
                commit()
            },
            onDismiss = { showQuantize = false },
        )
    }

    if (state.notes.any { it.selected } && !showArpDialog) {
        Box(Modifier.fillMaxSize()) {
            Text(
                "ARP",
                color = BgDeep,
                fontSize = 9.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 204.dp)
                    .background(AccentAmber, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .clickable { showArpDialog = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }

    if (showArpDialog) {
        ArpeggiatorDialog(
            selected = state.notes.filter { it.selected },
            ppq = state.ppq,
            onApply = { notes ->
                state.notes.removeAll { it.selected }
                for (n in notes) state.addFull(n)
                showArpDialog = false
                commit()
            },
            onDismiss = { showArpDialog = false },
        )
    }


    // ── Extension editor menu (kind=editor) ──
    // onApply currently only logs the state object + available methods;
    // the actual apply logic lands in a follow-up once we know the real
    // selection API (introspected at runtime to logcat).
    if (showExtensions) {
        com.maxixa.lemon.ui.ext.ExtensionsMenu(
            onDismiss = { showExtensions = false },
            onApply = { id, params ->
                showExtensions = false
                try {
                    android.util.Log.i("Lemon", "extension picked: $id, params=$params")
                    val cls = state::class.java
                    val methodNames = cls.methods
                        .filter { m ->
                            m.name.contains("elect", ignoreCase = true) ||
                            m.name.contains("otes", ignoreCase = true) ||
                            m.name.contains("commit", ignoreCase = true)
                        }
                        .map { it.name }
                        .distinct()
                        .sorted()
                    android.util.Log.i("Lemon", "PianoRollState selection-ish methods: $methodNames")
                    // Try known field names
                    val fields = cls.fields
                        .filter { f ->
                            f.name.contains("elect", ignoreCase = true) ||
                            f.name.contains("otes", ignoreCase = true)
                        }
                        .map { "${it.name}: ${it.type.simpleName}" }
                        .distinct()
                    android.util.Log.i("Lemon", "PianoRollState selection-ish fields: $fields")
                } catch (t: Throwable) {
                    android.util.Log.e("Lemon", "introspection failed", t)
                }
            },
            kindFilter = "editor",
        )
    }
}
