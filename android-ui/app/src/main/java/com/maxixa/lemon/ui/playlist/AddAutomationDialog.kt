package com.maxixa.lemon.ui.playlist

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

/**
 * Create an automation clip. Drives one of 8 targets:
 *   Channel: Volume / Pan / Cutoff / Resonance / Mod X / Mod Y
 *   Mixer track: Mixer Volume
 *   Global: Tempo
 *
 * Value 0..100 normalized → `AutoPoint.value` 0..1. Bipolar targets
 * (Pan, ModX, ModY) center at 50; unipolar at 0.
 *
 * Content (chips, picker, slider) extracted to AddAutomationDialogContent
 * to keep this file under 150 LOC.
 */
@Composable
fun AddAutomationDialog(
    channelLabels: List<String>,
    onConfirm: (target: Int, value: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var targetType by remember { mutableStateOf(AutoTargetType.Volume) }
    var channelIdx by remember { mutableStateOf(0) }
    var value by remember { mutableStateOf(80) }

    // Reset idx when switching to a global target (Tempo has no picker)
    LaunchedEffect(targetType) {
        if (targetType.kind == TargetKind.Global) channelIdx = 0
    }

    val valueLabel = displayAutomationValue(targetType, value)
    val canCreate = targetType.kind == TargetKind.Global || channelLabels.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Add automation clip", color = TextPrimary, fontSize = 15.sp)
                Text(valueLabel, color = AccentCyan, fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (channelLabels.isEmpty() && targetType.kind != TargetKind.Global) {
                Text("No channels in project", color = TextMuted, fontSize = 13.sp)
            } else {
                AddAutomationDialogContent(
                    targetType = targetType,
                    onTargetChange = { targetType = it },
                    channelIdx = channelIdx,
                    onChannelChange = { channelIdx = it },
                    channelLabels = channelLabels,
                    value = value,
                    onValueChange = { value = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (canCreate) {
                        val encoded = encodeTarget(targetType, channelIdx)
                        onConfirm(encoded, value / 100.0)
                    }
                },
                enabled = canCreate,
            ) {
                Text("Create",
                    color = if (canCreate) AccentOrange else TextMuted,
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
