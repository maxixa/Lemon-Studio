package com.maxixa.lemon.ui.playlist

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.state.*
import com.maxixa.lemon.ui.theme.*
import kotlin.math.roundToInt

private data class SMode(val value: Int, val label: String)
private val STRETCH_MODES = listOf(
    SMode(0, "Resample"),
    SMode(-1, "Stretch"),
    SMode(-2, "Pro"),
    SMode(5, "Auto"),
)

@Composable
fun PlaylistStretchDialog(
    engine: EngineController,
    clip: ClipView,
    sharedCount: Int,
    onApplied: () -> Unit,
    onDismiss: () -> Unit,
) {
    val channel = clip.pattern
    var mult by remember { mutableStateOf(engine.channelStretchMultiplier(channel)) }
    var pitchCents by remember { mutableStateOf(engine.channelPitch(channel)) }
    var mode by remember { mutableStateOf(engine.channelStretchMode(channel)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Text("Stretch clip · ch $channel",
                color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (sharedCount > 1) {
                    Text(
                        "⚠ Channel shared by $sharedCount clips — change affects all",
                        color = AccentAmber, fontSize = 10.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Text("Stretch", color = TextMuted, fontSize = 11.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = mult.toFloat(),
                        onValueChange = { mult = it.toDouble() },
                        valueRange = 0.25f..4.0f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCyan,
                            activeTrackColor = AccentCyan,
                            inactiveTrackColor = BorderSubtle,
                        ),
                    )
                    Text(" ${(mult * 100).toInt()}%",
                        color = AccentCyan, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(8.dp))
                Text("Pitch", color = TextMuted, fontSize = 11.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = pitchCents.toFloat(),
                        onValueChange = { pitchCents = (it / 100f).roundToInt() * 100 },
                        valueRange = -1200f..1200f,
                        steps = 23,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = AccentCyan,
                            activeTrackColor = AccentCyan,
                            inactiveTrackColor = BorderSubtle,
                        ),
                    )
                    Text(" %+.1f st".format(pitchCents / 100f),
                        color = AccentCyan, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(8.dp))
                Text("Mode", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (m in STRETCH_MODES) {
                        val active = mode == m.value
                        Text(
                            m.label,
                            color = if (active) BgDeep else TextMuted,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(
                                    if (active) AccentCyan else BgRaised,
                                    RoundedCornerShape(4.dp),
                                )
                                .clickable { mode = m.value }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                engine.setChannelStretchMultiplier(channel, mult)
                engine.setChannelPitch(channel, pitchCents)
                engine.setChannelStretchMode(channel, mode)
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
