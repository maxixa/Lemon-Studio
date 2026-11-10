package com.maxixa.lemon.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.state.LocalSettings
import com.maxixa.lemon.ui.icon.BackIcon
import com.maxixa.lemon.ui.settings.EffectParamDialog
import com.maxixa.lemon.ui.settings.EffectPickerDialog
import com.maxixa.lemon.ui.theme.*
import com.maxixa.lemon.state.*

@Composable
fun MixerScreen(
    handle: Long,
    engine: EngineController,
    onBack: () -> Unit,
) {
    val settings = LocalSettings.current
    var busSet by remember { mutableStateOf(engine.busList().toSet()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val saved = settings.busTracksFor(engine.currentProjectName.ifEmpty { "demo" })
        val current = engine.busList().toSet()
        if (saved.isNotEmpty() && current.isEmpty()) {
            saved.forEach { engine.setBus(it, true) }
            busSet = saved
        } else if (current.isNotEmpty()) {
            settings.setBusTracksFor(engine.currentProjectName.ifEmpty { "demo" }, current)
        }
    }
    androidx.compose.runtime.LaunchedEffect(engine.revision) { busSet = engine.busList().toSet(); settings.setBusTracksFor(engine.currentProjectName.ifEmpty { "demo" }, busSet) }
    var tracks by remember { mutableStateOf<List<MixerTrackView>>(emptyList()) }
    var slots by remember { mutableStateOf<Map<Int, List<String>>>(emptyMap()) }
    var bypassed by remember { mutableStateOf<Map<Int, Set<Int>>>(emptyMap()) }
    var showPicker by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showParamEditor by remember { mutableStateOf<Triple<Int, Int, String>?>(null) }
    var showRouting by remember { mutableStateOf<Int?>(null) }

    fun refresh() {
        tracks = MixerBridge.load(handle)
        slots = tracks.associate { t ->
            t.id to (0 until 4).map { i -> engine.mixerSlotEffect(t.id, i) }
        }
        bypassed = tracks.associate { t ->
            t.id to (0 until 4).filter { i ->
                engine.mixerIsEffectBypassed(t.id, i)
            }.toSet()
        }
    }

    LaunchedEffect(handle) { refresh() }

    Column(Modifier.fillMaxSize().background(BgDeep)) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).background(BgElevated),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clickable(onClick = onBack),
                contentAlignment = Alignment.Center) { BackIcon(TextPrimary, 18.dp) }
            Text("Mixer", color = TextPrimary, fontSize = 14.sp,
                modifier = Modifier.padding(start = 8.dp))
        }
        Row(
            Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(4.dp),
        ) {
            for (t in tracks) {
                val idx = t.id
                val isMaster = idx == 0
                MixerStrip(
                    track = t,
                    slotNames = slots[idx] ?: emptyList(),
                    bypassedSlots = bypassed[idx] ?: emptySet(),
                    isMaster = isMaster,
                    onVolume = { v ->
                        MixerBridge.setVolume(handle, idx, v)
                        tracks = tracks.map { if (it.id == idx) it.copy(volume = v) else it }
                    },
                    onPan = { v ->
                        MixerBridge.setPan(handle, idx, v)
                        tracks = tracks.map { if (it.id == idx) it.copy(pan = v) else it }
                    },
                    onSlotTap = { slot -> showPicker = idx to slot },
                    onSendsTap = { showRouting = idx },
                    isBus = idx in busSet,
                    onToggleBus = {
                        val now = !(idx in busSet)
                        engine.setBus(idx, now)
                        busSet = if (now) busSet + idx else busSet - idx
                        settings.setBusTracksFor(engine.currentProjectName.ifEmpty { "demo" }, busSet)
                    },)
                Spacer(Modifier.width(4.dp))
            }
        }
    }

    if (showPicker != null) {
        val (track, slot) = showPicker!!
        val currentName = engine.mixerSlotEffect(track, slot)
        val slotsList = slots[track] ?: emptyList()
        EffectPickerDialog(
            trackIndex = track,
            slotIndex = slot,
            currentId = currentName,
            currentlyBypassed = engine.mixerIsEffectBypassed(track, slot),
            canMoveUp = currentName.isNotEmpty() && slot > 0 && slotsList.getOrNull(slot - 1).isNullOrEmpty(),
            canMoveDown = currentName.isNotEmpty() && slot < slotsList.size - 1 && slotsList.getOrNull(slot + 1).isNullOrEmpty(),
            catalog = engine.effectCatalog(),
            onPick = { id ->
                engine.mixerAddEffect(track, slot, id)
                refresh()
                showPicker = null
            },
            onBypass = { on ->
                engine.mixerBypassEffect(track, slot, on)
                refresh()
            },
            onEditParams = {
                showParamEditor = Triple(track, slot, currentName)
            },
            onMoveUp = {
                engine.mixerSwapEffects(track, slot, slot - 1)
                refresh()
            },
            onMoveDown = {
                engine.mixerSwapEffects(track, slot, slot + 1)
                refresh()
            },
            onClear = {
                engine.mixerRemoveEffect(track, slot)
                refresh()
                showPicker = null
            },
            onDismiss = { showPicker = null },
        )
    }

    if (showParamEditor != null) {
        val (track, slot, name) = showParamEditor!!
        EffectParamDialog(
            trackIndex = track,
            slotIndex = slot,
            effectName = name,
            engine = engine,
            onDismiss = { showParamEditor = null },
        )
    }

    if (showRouting != null) {
        val tid = showRouting!!
        val tName = tracks.firstOrNull { it.id == tid }?.name ?: "Track $tid"
        SendRoutingDialog(
            engine = engine,
            trackId = tid,
            trackName = tName,
            trackNames = tracks.map { it.name },
            onApplied = { refresh(); showRouting = null },
            onDismiss = { showRouting = null },
        )
    }
}
