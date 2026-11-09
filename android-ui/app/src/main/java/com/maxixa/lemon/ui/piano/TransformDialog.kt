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

private enum class Kind(val label: String) {
    Randomize("Randomize"),
    Scale("Scale"),
    Limit("Limit"),
    Flip("Flip"),
    Chop("Chop"),
}

@Composable
fun TransformDialog(
    selectedCount: Int,
    state: PianoRollState,
    onApply: (List<NoteView>) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(Kind.Randomize) }
    var strength by remember { mutableStateOf(20) }
    var percent by remember { mutableStateOf(150) }
    var limitMin by remember { mutableStateOf(1) }
    var limitMax by remember { mutableStateOf(127) }
    var flipByPitch by remember { mutableStateOf(true) }
    var chopParts by remember { mutableStateOf(4) }
    var chopGap by remember { mutableStateOf(10) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Transform", color = TextPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold)
                Text("$selectedCount notes", color = AccentCyan, fontSize = 11.sp)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (k in listOf(Kind.Randomize, Kind.Scale, Kind.Limit))
                        ToggleChip(k.label, kind == k) { kind = k }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (k in listOf(Kind.Flip, Kind.Chop))
                        ToggleChip(k.label, kind == k) { kind = k }
                }
                Spacer(Modifier.height(12.dp))
                when (kind) {
                    Kind.Randomize -> RandomizeParams(strength) { strength = it }
                    Kind.Scale     -> ScaleParams(percent) { percent = it }
                    Kind.Limit     -> LimitParams(limitMin, limitMax, { limitMin = it }, { limitMax = it })
                    Kind.Flip      -> FlipParams(flipByPitch) { flipByPitch = it }
                    Kind.Chop      -> ChopParams(chopParts, chopGap, { chopParts = it }, { chopGap = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val out = when (kind) {
                    Kind.Randomize -> NoteTransforms.randomize(state.notes, strength)
                    Kind.Scale     -> NoteTransforms.scaleLevels(state.notes, percent)
                    Kind.Limit     -> NoteTransforms.limit(state.notes, limitMin, limitMax)
                    Kind.Flip      -> NoteTransforms.flip(state.notes, flipByPitch)
                    Kind.Chop      -> NoteTransforms.chop(state.notes, chopParts, chopGap)
                }
                onApply(out)
            }) {
                Text("Apply", color = AccentOrange, fontSize = 13.sp,
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
