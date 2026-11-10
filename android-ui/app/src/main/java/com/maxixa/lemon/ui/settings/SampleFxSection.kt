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
import com.maxixa.lemon.state.*

@Composable
fun SampleFxSection(channelIndex: Int, engine: EngineController) {
    var trimStart by remember { mutableStateOf(engine.channelFxTrimStart(channelIndex).toFloat()) }
    var trimLength by remember { mutableStateOf(engine.channelFxTrimLength(channelIndex).toFloat()) }
    var reverse by remember { mutableStateOf(engine.channelFxFlag(channelIndex, engine.FX_REVERSE)) }
    var swap by remember { mutableStateOf(engine.channelFxFlag(channelIndex, engine.FX_SWAP)) }
    var clip by remember { mutableStateOf(engine.channelFxFlag(channelIndex, engine.FX_CLIP)) }
    var normalize by remember { mutableStateOf(engine.channelFxNormalize(channelIndex)) }

    Text("Trim", color = TextMuted, fontSize = 11.sp)
    Spacer(Modifier.height(6.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Start", color = TextPrimary, fontSize = 12.sp)
        Text("%.1f%%".format(trimStart * 100), color = AccentOrange,
            fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
    Slider(
        value = trimStart,
        onValueChange = { v ->
            trimStart = v.coerceIn(0f, 0.99f - trimLength + 0.01f)
            engine.setChannelFxTrim(channelIndex, trimStart.toDouble(), trimLength.toDouble())
        },
        valueRange = 0f..0.99f,
        colors = SliderDefaults.colors(
            thumbColor = AccentOrange, activeTrackColor = AccentOrange,
            inactiveTrackColor = BorderSubtle,
        ),
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Length", color = TextPrimary, fontSize = 12.sp)
        Text("%.1f%%".format(trimLength * 100), color = AccentOrange,
            fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
    Slider(
        value = trimLength,
        onValueChange = { v ->
            trimLength = v.coerceIn(0.01f, 1f - trimStart)
            engine.setChannelFxTrim(channelIndex, trimStart.toDouble(), trimLength.toDouble())
        },
        valueRange = 0.01f..1f,
        colors = SliderDefaults.colors(
            thumbColor = AccentOrange, activeTrackColor = AccentOrange,
            inactiveTrackColor = BorderSubtle,
        ),
    )

    Spacer(Modifier.height(12.dp))
    Text("Effects", color = TextMuted, fontSize = 11.sp)
    Spacer(Modifier.height(6.dp))

    FxToggle("Reverse", reverse) {
        reverse = it
        engine.setChannelFxFlag(channelIndex, engine.FX_REVERSE, it)
    }
    FxToggle("Swap L/R", swap) {
        swap = it
        engine.setChannelFxFlag(channelIndex, engine.FX_SWAP, it)
    }
    FxToggle("Clip", clip) {
        clip = it
        engine.setChannelFxFlag(channelIndex, engine.FX_CLIP, it)
    }
    FxToggle("Normalize", normalize) {
        normalize = it
        engine.setChannelFxNormalize(channelIndex, it)
    }
}

@Composable
private fun FxToggle(label: String, active: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) AccentOrange.copy(alpha = 0.2f) else BgRaised)
            .clickable { onToggle(!active) }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = if (active) AccentOrange else TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
        Box(
            Modifier.size(32.dp, 18.dp)
                .background(
                    if (active) AccentOrange else BgRaised,
                    RoundedCornerShape(9.dp)
                ),
            contentAlignment = if (active) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.padding(horizontal = 2.dp).size(14.dp)
                .background(if (active) BgDeep else TextMuted, RoundedCornerShape(7.dp)))
        }
    }
    Spacer(Modifier.height(4.dp))
}
