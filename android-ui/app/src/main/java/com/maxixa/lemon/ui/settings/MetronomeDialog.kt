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

private val SOUND_NAMES = listOf("Hi-hat", "Tick", "Beep", "Cowbell", "Pulse")
private val VOLUME_STEPS = listOf(-12, -6, 0, 3, 6, 9, 12)

@Composable
fun MetronomeDialog(
    currentSound: Int,
    currentVolumeDb: Int,
    currentCountIn: Int = 0,
    onSoundChange: (Int) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onCountInChange: (Int) -> Unit = {},
    onDismiss: () -> Unit,
) {
    var sound by remember { mutableStateOf(currentSound.coerceIn(0, SOUND_NAMES.size - 1)) }
    var vol by remember { mutableStateOf(currentVolumeDb) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = { Text("Metronome", color = TextPrimary, fontSize = 15.sp) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Sound", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Column {
                    for (i in SOUND_NAMES.indices) {
                        val active = sound == i
                        Row(
                            Modifier.fillMaxWidth().height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) AccentOrange.copy(alpha = 0.2f) else BgRaised)
                                .clickable {
                                    sound = i
                                    onSoundChange(i)
                                }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(12.dp).clip(RoundedCornerShape(6.dp))
                                    .background(if (active) AccentOrange else BorderSubtle),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                SOUND_NAMES[i],
                                color = if (active) AccentOrange else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                        if (i < SOUND_NAMES.size - 1) Spacer(Modifier.height(4.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Volume", color = TextMuted, fontSize = 11.sp)
                    Text(
                        "${if (vol > 0) "+" else ""}$vol dB",
                        color = AccentOrange, fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Slider(
                    value = vol.toFloat(),
                    onValueChange = {
                        val nearest = VOLUME_STEPS.minByOrNull { v ->
                            kotlin.math.abs(v - it)
                        } ?: 0
                        if (nearest != vol) {
                            vol = nearest
                            onVolumeChange(nearest)
                        }
                    },
                    valueRange = VOLUME_STEPS.first().toFloat()..VOLUME_STEPS.last().toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentOrange,
                        activeTrackColor = AccentOrange,
                        inactiveTrackColor = BorderSubtle,
                    ),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("-12", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text("0", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text("+12", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)

                Spacer(Modifier.height(16.dp))
                Text("Count-in", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (b in 0..4) {
                        val active = currentCountIn == b
                        Box(
                            Modifier.weight(1f).height(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) AccentOrange else BgRaised)
                                .clickable { onCountInChange(b) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (b == 0) "Off" else "$b",
                                color = if (active) BgDeep else TextPrimary,
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (currentCountIn == 0) "Play starts immediately"
                    else "$currentCountIn bar${if (currentCountIn > 1) "s" else ""} of click before Play",
                    color = TextMuted, fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )

                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = AccentOrange,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}
