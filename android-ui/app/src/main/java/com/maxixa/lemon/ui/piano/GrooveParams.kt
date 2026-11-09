package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.*
import com.maxixa.lemon.ui.theme.*

@Composable
internal fun StrumParams(
    stepDen: Int, onStepDen: (Int) -> Unit,
    ascending: Boolean, onAscending: (Boolean) -> Unit,
) {
    NoteSubheader("Step")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (d in intArrayOf(4, 8, 16, 32)) ToggleChip("1/$d", stepDen == d) { onStepDen(d) }
    }
    Spacer(Modifier.height(8.dp))
    NoteSubheader("Direction")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ToggleChip("Up",   ascending)  { onAscending(true)  }
        ToggleChip("Down", !ascending) { onAscending(false) }
    }
}

@Composable
internal fun ArpParams(
    stepDen: Int, onStepDen: (Int) -> Unit,
    pattern: ArpPattern, onPattern: (ArpPattern) -> Unit,
) {
    NoteSubheader("Step")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (d in intArrayOf(4, 8, 16, 32)) ToggleChip("1/$d", stepDen == d) { onStepDen(d) }
    }
    Spacer(Modifier.height(8.dp))
    NoteSubheader("Pattern")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (p in listOf(ArpPattern.Up, ArpPattern.Down, ArpPattern.UpDown))
            ToggleChip(p.label, pattern == p) { onPattern(p) }
    }
}

@Composable
internal fun FlamParams(
    offsetTicks: Int, onOffset: (Int) -> Unit,
    copies: Int, onCopies: (Int) -> Unit,
    velDrop: Int, onVelDrop: (Int) -> Unit,
    snapTicks: Int,
) {
    ParamSlider("Offset", offsetTicks, -snapTicks, snapTicks, "${offsetTicks}t") { onOffset(it) }
    NoteSubheader("Copies")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (c in intArrayOf(1, 2, 3, 4)) ToggleChip("$c", copies == c) { onCopies(c) }
    }
    Spacer(Modifier.height(8.dp))
    ParamSlider("Velocity drop", velDrop, 0, 60, "-$velDrop") { onVelDrop(it) }
}

@Composable
internal fun DetectReadout(chordName: String?) {
    NoteSubheader("Detected chord")
    Text(
        chordName ?: "\u2014",
        color = if (chordName == null) TextMuted else AccentOrange,
        fontSize = 22.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(4.dp))
    Text("Read-only. No changes applied.", color = TextMuted, fontSize = 11.sp)
}

@Composable
internal fun NoteSubheader(text: String) {
    Text(text, color = TextMuted, fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp))
}
