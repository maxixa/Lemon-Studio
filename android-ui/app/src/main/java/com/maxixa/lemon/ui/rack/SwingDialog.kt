package com.maxixa.lemon.ui.rack

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

/**
 * Project swing slider. Internal range is 0..=128; UI shows 0..=100%.
 * Swing applies on release (engine rebuild per apply, not per drag).
 * Not undoable yet — see note in EngineHandle::set_project_swing.
 */
@Composable
fun SwingDialog(
    currentSwing: Int,
    onApply: (internalSwing: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var pct by remember { mutableStateOf(swingToPct(currentSwing)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Swing", color = TextPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(if (pct == 0) "Off" else "$pct%",
                    color = AccentCyan, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Slider(
                    value = pct.toFloat(),
                    onValueChange = { pct = it.toInt().coerceIn(0, 100) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentOrange,
                        activeTrackColor = AccentOrange,
                        inactiveTrackColor = BorderSubtle,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Delays every second step. 0 = straight, 100% = maximum shuffle.",
                    color = TextMuted, fontSize = 10.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Applies to the whole project, not per channel.",
                    color = TextMuted, fontSize = 10.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(pctToSwing(pct)) }) {
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

private fun swingToPct(swing: Int): Int {
    if (swing <= 0) return 0
    return ((swing * 100 + 64) / 128).coerceIn(0, 100)
}

private fun pctToSwing(pct: Int): Int {
    if (pct <= 0) return 0
    return ((pct * 128 + 50) / 100).coerceIn(0, 128)
}
