package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

@Composable
internal fun RandomizeParams(strength: Int, onStrength: (Int) -> Unit) {
    ParamSlider("Strength", strength, 0, 127, "$strength") { onStrength(it) }
}

@Composable
internal fun ScaleParams(percent: Int, onPercent: (Int) -> Unit) {
    ParamSlider("Percent", percent, 10, 200, "${percent}%") { onPercent(it) }
}

@Composable
internal fun LimitParams(min: Int, max: Int, onMin: (Int) -> Unit, onMax: (Int) -> Unit) {
    ParamSlider("Min", min, 1, 127, "$min") { onMin(it) }
    ParamSlider("Max", max, 1, 127, "$max") { onMax(it) }
}

@Composable
internal fun FlipParams(byPitch: Boolean, onByPitch: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ToggleChip("Position", !byPitch) { onByPitch(false) }
        ToggleChip("Pitch",    byPitch)  { onByPitch(true) }
    }
}

@Composable
internal fun ChopParams(parts: Int, gap: Int, onParts: (Int) -> Unit, onGap: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (p in intArrayOf(2, 3, 4, 8)) ToggleChip("$p", parts == p) { onParts(p) }
    }
    Spacer(Modifier.height(8.dp))
    ParamSlider("Gap", gap, 0, 90, "${gap}%") { onGap(it) }
}

@Composable
internal fun ParamSlider(
    label: String, value: Int, min: Int, max: Int,
    display: String, onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextPrimary, fontSize = 12.sp)
        Text(display, color = AccentOrange, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.toInt().coerceIn(min, max)) },
        valueRange = min.toFloat()..max.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = AccentOrange, activeTrackColor = AccentOrange,
            inactiveTrackColor = BorderSubtle,
        ),
    )
}

@Composable
internal fun ToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) AccentCyan.copy(alpha = 0.25f) else BgRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) AccentCyan else TextMuted,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
