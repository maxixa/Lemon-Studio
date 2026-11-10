package com.maxixa.lemon.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.EngineController.AutomationLfo
import com.maxixa.lemon.ui.theme.*
import com.maxixa.lemon.state.*

private enum class EditorTab(val label: String) { Points("Points"), Lfo("LFO") }

@Composable
fun AutomationEditorDialog(
    channelName: String,
    initialPoints: List<Pair<Double, Double>>,
    initialLfo: AutomationLfo,
    clipBeats: Double,
    onApply: (points: List<Pair<Double, Double>>, lfo: AutomationLfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableStateOf(EditorTab.Points) }
    var pts by remember {
        mutableStateOf(
            if (initialPoints.isEmpty()) listOf(0.0 to 0.5)
            else initialPoints.sortedBy { it.first }
        )
    }
    var lfo by remember { mutableStateOf(initialLfo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Automation · $channelName", color = TextPrimary, fontSize = 14.sp)
                Text("${pts.size} pts${if (lfo.isOn) " · LFO" else ""}",
                    color = AccentCyan, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (t in EditorTab.values()) {
                        TabChip(t.label, tab == t) { tab = t }
                    }
                }
                Spacer(Modifier.height(8.dp))
                when (tab) {
                    EditorTab.Points -> {
                        Text("Tap to add · drag to move · long-press to delete",
                            color = TextMuted, fontSize = 10.sp)
                        Spacer(Modifier.height(6.dp))
                        AutomationEditorPoints(
                            initialPoints = pts,
                            clipBeats = clipBeats,
                            onPointsChange = { pts = it },
                        )
                    }
                    EditorTab.Lfo -> AutomationEditorLfo(
                        initial = lfo,
                        onLfoChange = { lfo = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(pts, lfo) }) {
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

@Composable
private fun TabChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) AccentCyan.copy(alpha = 0.2f) else BgRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) AccentCyan else TextMuted,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
