package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.PianoRollState
import com.maxixa.lemon.ui.theme.*
import com.maxixa.lemon.state.*

/**
 * Insert a chord at the current playhead. Root × Octave × Quality →
 * 3-4 notes added on `state.targetChannel` at the snapped playhead tick.
 *
 * Constants + MiniChip live in ChordPickerData.kt to keep this file
 * under the 150-LOC ceiling.
 */
@Composable
fun AddChordDialog(
    playheadTicks: Int,
    state: PianoRollState,
    onApplied: () -> Unit,
    onDismiss: () -> Unit,
) {
    var root by remember { mutableStateOf(0) }
    var octave by remember { mutableStateOf(5) }
    var quality by remember { mutableStateOf("maj") }

    val rootKey = (octave * 12 + root).coerceIn(0, 127)
    val intervals = CHORD_INTERVALS[quality] ?: intArrayOf(0, 4, 7)
    val keys = intervals.map { (rootKey + it).coerceIn(0, 127) }
    val preview = keys.joinToString(" ") { noteLabel(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Add chord", color = TextPrimary, fontSize = 15.sp)
                Text(preview, color = AccentCyan, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    maxLines = 1)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Root", color = TextMuted, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in 0..11) {
                        MiniChip(CHORD_NOTE_NAMES[i], root == i, Modifier.weight(1f)) { root = i }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Octave", color = TextMuted, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (o in OCTAVE_OPTIONS) {
                        MiniChip("$o", octave == o, Modifier.weight(1f)) { octave = o }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Quality", color = TextMuted, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (q in QUALITY_ORDER.take(7)) {
                        MiniChip(q, quality == q, Modifier.weight(1f)) { quality = q }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (q in QUALITY_ORDER.drop(7)) {
                        MiniChip(q, quality == q, Modifier.weight(1f)) { quality = q }
                    }
                    repeat(7 - QUALITY_ORDER.drop(7).size) {
                        Spacer(Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Insert at playhead on current target channel",
                    color = TextMuted, fontSize = 10.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val snapTick = state.snap(playheadTicks.toFloat())
                for (k in keys) {
                    state.add(snapTick, state.defaultNoteLength, k, state.targetChannel)
                }
                onApplied()
                onDismiss()
            }) {
                Text("Insert", color = AccentOrange, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted, fontSize = 13.sp)
            }
        },
    )
}
