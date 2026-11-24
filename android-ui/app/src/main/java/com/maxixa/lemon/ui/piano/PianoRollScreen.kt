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
import com.maxixa.lemon.data.NoteView

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
    var showGeneratorMenu by remember { mutableStateOf(false) }
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

    // ── gen FAB (above fx) ──
    androidx.compose.material3.ExtendedFloatingActionButton(
        onClick = { showGeneratorMenu = true },
        modifier = androidx.compose.ui.Modifier
            .align(androidx.compose.ui.Alignment.BottomEnd)
            .padding(16.dp, 84.dp, 16.dp, 16.dp),
    ) { androidx.compose.material3.Text("gen") }
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

    // ── Extension editor menu (applies to whole pattern) ──
    // No selection API dependency: quantize transforms every note in state.
    // For editors that change note count, would need per-note tracking —
    // next ship for solo-guitar etc.
    if (showExtensions) {
        com.maxixa.lemon.ui.ext.ExtensionsMenu(
            onDismiss = { showExtensions = false },
            onApply = { id, params ->
                showExtensions = false
                try {
                    // 1. Extract current notes as wire format
                    val wireNotes = state.notes.map { note ->
                        com.maxixa.lemon.ExtHost.Note(
                            startTick = note.position,
                            lengthTicks = note.length,
                            pitch = note.key.toInt(),
                            velocity = note.velocity.toInt(),
                            channel = note.channel.toInt(),
                            flags = note.flags.toInt(),
                        )
                    }
                    android.util.Log.i("Lemon", "ext apply: $id, ${wireNotes.size} notes, params=$params")

                    // 2. Run extension
                    val result = com.maxixa.lemon.ExtHost.runNoteEditor(id, wireNotes, params)
                    if (result == null) {
                        android.util.Log.e("Lemon", "extension $id returned null")
                    } else if (result.size != wireNotes.size) {
                        // Editors that change note count need different apply logic — skip for now
                        android.util.Log.w("Lemon", "ext $id changed note count (${wireNotes.size} -> ${result.size}); only 1:1 editors supported")
                    } else {
                        // 3. Apply in place — replace position/length from result, keep original ID
                        state.notes.forEachIndexed { i, note ->
                            val wn = result[i]
                            state.notes[i] = NoteView(
                                id = note.id,
                                position = wn.startTick,
                                length = wn.lengthTicks,
                                channel = wn.channel,
                                key = wn.pitch,
                                velocity = wn.velocity,
                                pan = note.pan,
                                finePitch = note.finePitch,
                                release = note.release,
                                modX = note.modX,
                                modY = note.modY,
                                flags = wn.flags,
                                group = note.group,
                                unk14 = note.unk14,
                                repeat = note.repeat,
                            )
                        }
                        // 4. Commit to engine
                        commit()
                        android.util.Log.i("Lemon", "ext $id applied to ${result.size} notes, committed")
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("Lemon", "ext $id threw", t)
                }
            },
            kindFilter = "editor",
        )
    }

    // ── Generator extension menu (solo-guitar etc.) ──
    // Reference = notes on channels OTHER than state.targetChannel.
    // Target  = current pattern, notes appended on state.targetChannel.
    // So: put chords on channel 0, set target channel to 1, tap gen → solo
    //     appears on channel 1 following channel 0's chords.
    if (showGeneratorMenu) {
        com.maxixa.lemon.ui.ext.ExtensionsMenu(
            onDismiss = { showGeneratorMenu = false },
            onApply = { id, params ->
                showGeneratorMenu = false
                try {
                    val targetCh = state.targetChannel
                    val refNotes = state.notes.filter { it.channel != targetCh }
                    if (refNotes.isEmpty()) {
                        android.util.Log.w("Lemon", "gen $id: no reference notes on other channels")
                    } else {
                        val wireRef = refNotes.map { note ->
                            com.maxixa.lemon.ExtHost.Note(
                                startTick = note.position,
                                lengthTicks = note.length,
                                pitch = note.key,
                                velocity = note.velocity,
                                channel = note.channel,
                                flags = note.flags,
                            )
                        }
                        val target = com.maxixa.lemon.ExtHost.TargetRange(
                            trackIdx = 0,
                            patternIdx = 0,
                            startTick = 0,
                            lengthTicks = state.patternLength,
                        )
                        val reference = com.maxixa.lemon.ExtHost.ReferenceTrack(
                            trackIdx = 0,
                            patternIdx = 0,
                            trackName = "Reference",
                            notes = wireRef,
                        )
                        val result = com.maxixa.lemon.ExtHost.runNoteGenerator(
                            id = id,
                            target = target,
                            references = listOf(reference),
                            params = params,
                            scale = null,
                            seed = System.currentTimeMillis(),
                            ppq = state.ppq,
                            tempo = bpm.toDouble(),
                        )
                        if (result == null) {
                            android.util.Log.e("Lemon", "gen $id: runNoteGenerator returned null")
                        } else if (result.primary.isEmpty()) {
                            android.util.Log.w("Lemon", "gen $id: 0 primary notes generated")
                        } else {
                            var nextId = (state.notes.maxOfOrNull { it.id } ?: 0L) + 1
                            result.primary.forEach { wn ->
                                state.notes.add(NoteView(
                                    id = nextId++,
                                    position = wn.startTick,
                                    length = wn.lengthTicks,
                                    channel = targetCh,
                                    key = wn.pitch,
                                    velocity = wn.velocity,
                                    pan = 0,
                                    finePitch = 0,
                                    release = 0,
                                    modX = 0,
                                    modY = 0,
                                    flags = wn.flags,
                                    group = 0,
                                    unk14 = 0,
                                    repeat = 0,
                                ))
                            }
                            // Materialize secondary tracks (harmony lines etc.)
                            var secondaryNotes = 0
                            result.secondary.forEach { sec ->
                                val targetChannel: Int? = when (sec.kind) {
                                    "existing" -> sec.idx
                                    "new" -> {
                                        val newCh = try {
                                            com.maxixa.lemon.LemonEngineBridge.engineAddSamplerChannel(handle)
                                        } catch (t: Throwable) {
                                            android.util.Log.e("Lemon", "gen $id: engineAddSamplerChannel threw", t)
                                            -1
                                        }
                                        if (newCh >= 0) {
                                            val instName = when (sec.instrumentCategory?.lowercase()) {
                                                "drums" -> "Drums"
                                                "bass" -> "Bass"
                                                "guitar" -> "Guitar"
                                                "keys", "piano" -> "Piano"
                                                "lead", "synth" -> "Lead"
                                                "pad" -> "Pad"
                                                else -> null
                                            }
                                            if (instName != null) {
                                                try {
                                                    com.maxixa.lemon.LemonEngineBridge.engineSetBuiltinInstrument(
                                                        handle, newCh, instName
                                                    )
                                                } catch (_: Throwable) {}
                                            }
                                            newCh
                                        } else { null }
                                    }
                                    else -> null
                                }
                                if (targetChannel != null) {
                                    sec.notes.forEach { wn ->
                                        state.notes.add(NoteView(
                                            id = nextId++,
                                            position = wn.startTick,
                                            length = wn.lengthTicks,
                                            channel = targetChannel,
                                            key = wn.pitch,
                                            velocity = wn.velocity,
                                            pan = 0,
                                            finePitch = 0,
                                            release = 0,
                                            modX = 0,
                                            modY = 0,
                                            flags = wn.flags,
                                            group = 0,
                                            unk14 = 0,
                                            repeat = 0,
                                        ))
                                        secondaryNotes++
                                    }
                                }
                            }
                            commit()
                            android.util.Log.i("Lemon",
                                "gen $id: ${result.primary.size} primary -> ch $targetCh, " +
                                "$secondaryNotes secondary notes materialized")
                        }
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("Lemon", "gen $id threw", t)
                }
            },
            kindFilter = "generator",
        )
    }

}
