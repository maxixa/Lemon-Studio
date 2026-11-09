package com.maxixa.lemon.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.maxixa.lemon.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun TempoDialog(
    initialBpm: Float,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var bpm by remember { mutableStateOf(initialBpm.coerceIn(40f, 240f)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = { Text("Tempo", color = TextPrimary, fontSize = 15.sp) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "${bpm.roundToInt()} BPM",
                    color = AccentOrange,
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(12.dp))
                Slider(
                    value = bpm,
                    onValueChange = { bpm = it },
                    valueRange = 40f..240f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentOrange,
                        activeTrackColor = AccentOrange,
                        inactiveTrackColor = BorderSubtle,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (preset in listOf(80f, 100f, 120f, 130f, 140f, 174f)) {
                        Box(
                            Modifier.weight(1f).height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (kotlin.math.abs(bpm - preset) < 0.5f) AccentOrange else BgRaised)
                                .clickable { bpm = preset },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${preset.roundToInt()}",
                                color = if (kotlin.math.abs(bpm - preset) < 0.5f) BgDeep else TextPrimary,
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(bpm) }) {
                Text("Apply", color = AccentOrange,
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
