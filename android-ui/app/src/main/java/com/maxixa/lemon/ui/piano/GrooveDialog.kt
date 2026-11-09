package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.*
import com.maxixa.lemon.data.NoteView
import com.maxixa.lemon.ui.theme.*

private enum class GKind(val label: String) {
    Strum("Strum"), Arp("Arp"), Flam("Flam"), Detect("Detect"),
}

@Composable
fun GrooveDialog(
    selectedCount: Int,
    state: PianoRollState,
    onApply: (List<NoteView>) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(GKind.Strum) }
    var strumStepDen by remember { mutableStateOf(32) }
    var strumAscending by remember { mutableStateOf(true) }
    var arpStepDen by remember { mutableStateOf(16) }
    var arpPattern by remember { mutableStateOf(ArpPattern.Up) }
    var flamOffset by remember { mutableStateOf(state.ppq / 8) }
    var flamCopies by remember { mutableStateOf(1) }
    var flamVelDrop by remember { mutableStateOf(20) }

    val tolerance = (state.ppq / 32).coerceAtLeast(1)
    val stepTicksStrum = (state.ppq * 4 / strumStepDen).coerceAtLeast(1)
    val stepTicksArp   = (state.ppq * 4 / arpStepDen).coerceAtLeast(1)
    val effectiveSnap  = (state.snapTicks.takeIf { it > 0 } ?: (state.ppq / 4)).coerceAtLeast(1)

    val detected: String? = remember(state.notes.toList(), kind) {
        if (kind == GKind.Detect) NoteGroove.detectChord(state.notes) else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Groove", color = TextPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold)
                Text("$selectedCount notes", color = AccentCyan, fontSize = 11.sp)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (k in listOf(GKind.Strum, GKind.Arp)) ToggleChip(k.label, kind == k) { kind = k }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (k in listOf(GKind.Flam, GKind.Detect)) ToggleChip(k.label, kind == k) { kind = k }
                }
                Spacer(Modifier.height(12.dp))
                when (kind) {
                    GKind.Strum -> StrumParams(
                        stepDen = strumStepDen, onStepDen = { strumStepDen = it },
                        ascending = strumAscending, onAscending = { strumAscending = it },
                    )
                    GKind.Arp -> ArpParams(
                        stepDen = arpStepDen, onStepDen = { arpStepDen = it },
                        pattern = arpPattern, onPattern = { arpPattern = it },
                    )
                    GKind.Flam -> FlamParams(
                        offsetTicks = flamOffset, onOffset = { flamOffset = it },
                        copies = flamCopies, onCopies = { flamCopies = it },
                        velDrop = flamVelDrop, onVelDrop = { flamVelDrop = it },
                        snapTicks = effectiveSnap,
                    )
                    GKind.Detect -> DetectReadout(detected)
                }
            }
        },
        confirmButton = {
            if (kind == GKind.Detect) {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = AccentOrange, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            } else {
                TextButton(onClick = {
                    val out = when (kind) {
                        GKind.Strum -> NoteGroove.strum(state.notes, stepTicksStrum, strumAscending, tolerance)
                        GKind.Arp   -> NoteGroove.arpeggiate(state.notes, stepTicksArp, arpPattern, tolerance)
                        GKind.Flam  -> NoteGroove.flam(state.notes, flamOffset, flamCopies, flamVelDrop)
                        GKind.Detect -> state.notes
                    }
                    onApply(out)
                }) {
                    Text("Apply", color = AccentOrange, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            if (kind != GKind.Detect) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextMuted, fontSize = 13.sp)
                }
            }
        },
    )
}
