package com.maxixa.lemon.ui.mixer

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.ui.theme.*

/**
 * Send routing dialog. Lists every other mixer track with a level slider.
 * Level 0 = no send (or silent sidechain-only route if already wired).
 * Level 100% = unity gain.
 *
 * Reads initial state via `engine.mixerGetSends(trackId)`. On Apply, calls
 * `engine.mixerSetSendLevel` for changed destinations only.
 */
@Composable
fun SendRoutingDialog(
    engine: EngineController,
    trackId: Int,
    trackName: String,
    trackNames: List<String>,
    onApplied: () -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember(trackId) { engine.mixerGetSends(trackId) }
    var sends by remember(trackId) { mutableStateOf(initial) }

    val activeCount = sends.count { it.value > 0.001f }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Sends · $trackName", color = TextPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("$activeCount active", color = AccentCyan, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Route this track's signal to other mixer tracks.",
                    color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))

                for (dest in trackNames.indices) {
                    if (dest == trackId) continue
                    val label = trackNames[dest].ifEmpty { "Track ${dest + 1}" }
                    val isMaster = dest == 0
                    val level = sends[dest] ?: 0f

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isMaster) "MASTER" else label,
                            color = if (isMaster) AccentOrange else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isMaster) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${(level * 100).toInt()}%",
                            color = if (level > 0.001f) AccentCyan else TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Slider(
                        value = level,
                        onValueChange = { newLevel ->
                            sends = sends.toMutableMap().also { it[dest] = newLevel.coerceIn(0f, 1f) }
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCyan,
                            activeTrackColor = AccentCyan,
                            inactiveTrackColor = BorderSubtle,
                        ),
                    )
                    Spacer(Modifier.height(2.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                for ((dest, level) in sends) {
                    val prev = initial[dest] ?: 0f
                    if (prev != level) {
                        engine.mixerSetSendLevel(trackId, dest, level.toDouble())
                    }
                }
                onApplied()
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
