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
 * Quantize dialog — strength + swing + optional note-end snapping.
 *
 * Strength 100% = full snap, 0% = no-op.
 * Swing 0% = straight, 50% = subtle shuffle, 66% = triplet feel,
 *      100% = odd grid indices land halfway to the next grid line.
 * Quantize ends snaps note lengths to grid; the note itself may still
 *      shift by strength%.
 *
 * Formula matches `PianoRollState.quantizeWithSwing`.
 */
@Composable
fun QuantizeDialog(
    state: PianoRollState,
    onApply: (snapTicks: Int, strength: Int, swingPercent: Int, quantizeEnds: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val snap = state.snapTicks
    var strength by remember { mutableStateOf(100) }
    var swing by remember { mutableStateOf(0) }
    var quantizeEnds by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Quantize", color = TextPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold)
                Text("${state.selectedCount} notes", color = AccentCyan, fontSize = 11.sp)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (snap <= 0) {
                    Text("Snap grid is off — pick a snap size first.",
                        color = AccentRed, fontSize = 12.sp)
                    return@Column
                }

                // Strength
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Strength", color = TextPrimary, fontSize = 13.sp)
                    Text("$strength%", color = AccentOrange, fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                ParamSlider("", strength, 0, 100, "$strength%") { strength = it }
                Spacer(Modifier.height(4.dp))

                // Swing
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Swing", color = TextPrimary, fontSize = 13.sp)
                    Text("$swing%", color = AccentOrange, fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                ParamSlider("", swing, 0, 100, "$swing%") { swing = it }
                Spacer(Modifier.height(2.dp))
                Text("0 = straight · 50 = shuffle · 66 = triplet · 100 = extreme",
                    color = TextMuted, fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(10.dp))

                // Quantize ends toggle
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Snap note ends to grid", color = TextPrimary, fontSize = 12.sp)
                    ToggleChip(if (quantizeEnds) "On" else "Off", quantizeEnds) {
                        quantizeEnds = !quantizeEnds
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Snap: $snap ticks  ·  Max shift: ${snap / 2} ticks",
                    color = TextMuted, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (snap > 0) onApply(snap, strength, swing, quantizeEnds) },
                enabled = snap > 0,
            ) {
                Text("Apply",
                    color = if (snap > 0) AccentOrange else TextMuted,
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
