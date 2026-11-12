package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.data.NoteView
import com.maxixa.lemon.ui.theme.*

@Composable
fun ArpeggiatorDialog(
    selected: List<NoteView>,
    ppq: Int,
    onApply: (List<NoteView>) -> Unit,
    onDismiss: () -> Unit,
) {
    var pattern by remember { mutableStateOf(ArpSeq.Up) }
    var subdivision by remember { mutableStateOf(16) }
    var octaves by remember { mutableStateOf(1) }
    var gatePercent by remember { mutableStateOf(80) }
    var velocity by remember { mutableStateOf(100) }

    val params = ArpParams(
        pattern = pattern,
        subdivision = subdivision,
        octaves = octaves,
        gatePercent = gatePercent,
        velocity = velocity,
    )
    val preview = remember(selected, params) { generateArpNotes(selected, ppq, params) }
    val distinctPitches = selected.map { it.key }.distinct().size
    val canGenerate = selected.isNotEmpty() && distinctPitches >= 1

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Text("Arpeggiate · ${preview.size} notes",
                color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("Selected: ${selected.size} notes · $distinctPitches pitch classes",
                    color = TextMuted, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))

                Text("Pattern", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (p in ArpSeq.values()) {
                        val active = pattern == p
                        Text(
                            p.label,
                            color = if (active) BgDeep else TextMuted,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(
                                    if (active) AccentCyan else BgRaised,
                                    RoundedCornerShape(4.dp),
                                )
                                .clickable { pattern = p }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Subdivision", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((label, sub) in listOf(
                        "1/4" to 4, "1/8" to 8, "1/16" to 16, "1/32" to 32,
                        "1/8T" to -8, "1/16T" to -16,
                    )) {
                        val active = subdivision == sub
                        Text(
                            label,
                            color = if (active) BgDeep else TextMuted,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(
                                    if (active) AccentCyan else BgRaised,
                                    RoundedCornerShape(4.dp),
                                )
                                .clickable { subdivision = sub }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Octaves", color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(64.dp))
                    Slider(
                        value = octaves.toFloat(),
                        onValueChange = { octaves = it.toInt().coerceIn(1, 4) },
                        valueRange = 1f..4f, steps = 2,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan, inactiveTrackColor = BorderSubtle),
                    )
                    Text(" $octaves", color = AccentCyan, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(24.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gate", color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(64.dp))
                    Slider(
                        value = gatePercent.toFloat(),
                        onValueChange = { gatePercent = it.toInt().coerceIn(10, 100) },
                        valueRange = 10f..100f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan, inactiveTrackColor = BorderSubtle),
                    )
                    Text(" $gatePercent%", color = AccentCyan, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(42.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Velocity", color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(64.dp))
                    Slider(
                        value = velocity.toFloat(),
                        onValueChange = { velocity = it.toInt().coerceIn(1, 127) },
                        valueRange = 1f..127f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan, inactiveTrackColor = BorderSubtle),
                    )
                    Text(" $velocity", color = AccentCyan, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(32.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (canGenerate) onApply(preview) }, enabled = canGenerate) {
                Text("Generate",
                    color = if (canGenerate) AccentOrange else TextMuted.copy(alpha = 0.4f),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted, fontSize = 13.sp)
            }
        },
    )
}
