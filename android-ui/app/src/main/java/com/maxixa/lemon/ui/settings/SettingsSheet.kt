package com.maxixa.lemon.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.state.LocalSettings
import com.maxixa.lemon.ui.theme.*
import com.maxixa.lemon.state.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    engine: EngineController,
    onDismiss: () -> Unit,
) {
    val settings = LocalSettings.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        contentColor = TextPrimary,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("Settings", color = TextPrimary, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("UI Scale", color = TextPrimary, fontSize = 14.sp)
                Text(
                    "${(settings.uiScale.floatValue * 100).toInt()}%",
                    color = AccentOrange, fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Slider(
                value = settings.uiScale.floatValue,
                onValueChange = { settings.setUiScale(it) },
                valueRange = 0.7f..1.5f,
                colors = SliderDefaults.colors(
                    thumbColor = AccentOrange,
                    activeTrackColor = AccentOrange,
                    inactiveTrackColor = BorderSubtle,
                ),
            )
            Spacer(Modifier.height(24.dp))
            Text("Audio Buffer", color = TextPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Higher = fewer xruns, more latency. Restart applies.",
                color = TextMuted, fontSize = 10.sp,
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (opt in listOf(512, 1024, 2048, 4096, 8192, 16384)) {
                    val active = settings.audioBufferFrames == opt
                    androidx.compose.material3.Text(
                        "$opt",
                        color = if (active) BgDeep else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                if (active) AccentOrange else BgRaised,
                                androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                            )
                            .clickable {
                                settings.audioBufferFrames = opt
                                engine.setPreferredBufferFrames(opt)
                                engine.restartAudio()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Current: ${settings.audioBufferFrames} frames (${"%.1f".format(settings.audioBufferFrames.toFloat() / 48f)} ms @ 48 kHz)",
                color = AccentOrange, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
