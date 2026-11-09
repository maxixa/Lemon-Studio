package com.maxixa.lemon.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import kotlin.math.pow
import kotlin.math.roundToInt

private data class StretchMode(val value: Int, val label: String, val hint: String)
private val STRETCH_MODES = listOf(
    StretchMode(0, "Resample", "Pitch & length together"),
    StretchMode(-1, "Stretch", "Pitch only · realtime"),
    StretchMode(-2, "Pro", "Pitch only · HQ"),
    StretchMode(5, "Auto", "Auto-pick per sample"),
)

@Composable
fun PitchStretchSection(channelIndex: Int, engine: EngineController) {
    var pitchCents by remember { mutableStateOf(engine.channelPitch(channelIndex)) }
    var stretchMode by remember { mutableStateOf(engine.channelStretchMode(channelIndex)) }
    var multiplier by remember { mutableStateOf(engine.channelStretchMultiplier(channelIndex)) }
    var stretchCents by remember { mutableStateOf(engine.channelStretchPitch(channelIndex)) }

    // ─── Pitch ───
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Pitch", color = TextPrimary, fontSize = 13.sp)
        Text("%+.2f st".format(pitchCents / 100f),
            color = AccentOrange, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
    Slider(
        value = (pitchCents / 100f).coerceIn(-12f, 12f),
        onValueChange = { st ->
            pitchCents = (st * 100).roundToInt()
            engine.setChannelPitch(channelIndex, pitchCents)
        },
        valueRange = -12f..12f,
        colors = SliderDefaults.colors(
            thumbColor = AccentOrange,
            activeTrackColor = AccentOrange,
            inactiveTrackColor = BorderSubtle,
        ),
    )

    // ─── Stretch mode ───
    Spacer(Modifier.height(12.dp))
    Text("Stretch mode", color = TextMuted, fontSize = 11.sp)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (m in STRETCH_MODES) {
            val active = stretchMode == m.value
            Box(
                Modifier.weight(1f).height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) AccentOrange else BgRaised)
                    .clickable {
                        stretchMode = m.value
                        engine.setChannelStretchMode(channelIndex, m.value)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(m.label,
                    color = if (active) BgDeep else TextPrimary,
                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        STRETCH_MODES.firstOrNull { it.value == stretchMode }?.hint ?: "",
        color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
    )

    // ─── Multiplier ───
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Multiplier", color = TextPrimary, fontSize = 13.sp)
        Text("%d%%".format((multiplier * 100).roundToInt()),
            color = AccentOrange, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
    // Log-scale slider: 0.25 → 4.0
    val sliderPos = (kotlin.math.log2(multiplier) / 4.0 + 0.5).toFloat().coerceIn(0f, 1f)
    Slider(
        value = sliderPos,
        onValueChange = { pos ->
            val newMul = 2.0.pow(pos.toDouble() * 4.0 - 2.0)
            multiplier = newMul
            engine.setChannelStretchMultiplier(channelIndex, newMul)
        },
        valueRange = 0f..1f,
        colors = SliderDefaults.colors(
            thumbColor = AccentOrange,
            activeTrackColor = AccentOrange,
            inactiveTrackColor = BorderSubtle,
        ),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("25%", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Text("100%", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Text("400%", color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }

    // ─── Stretch pitch (realtime only) ───
    if (stretchMode == -1 || stretchMode == -2) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Stretch pitch", color = TextPrimary, fontSize = 13.sp)
            Text("%+.2f st".format(stretchCents / 100f),
                color = AccentCyan, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = (stretchCents / 100f).coerceIn(-12f, 12f),
            onValueChange = { st ->
                stretchCents = (st * 100).roundToInt()
                engine.setChannelStretchPitch(channelIndex, stretchCents)
            },
            valueRange = -12f..12f,
            colors = SliderDefaults.colors(
                thumbColor = AccentCyan,
                activeTrackColor = AccentCyan,
                inactiveTrackColor = BorderSubtle,
            ),
        )
    }
}
