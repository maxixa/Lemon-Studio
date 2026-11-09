package com.maxixa.lemon.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

/**
 * Body of the AddAutomationDialog: target chip grid, picker (or global
 * note), value slider. Extracted from AddAutomationDialog to keep both
 * files under the 150-LOC ceiling.
 */
@Composable
internal fun AddAutomationDialogContent(
    targetType: AutoTargetType,
    onTargetChange: (AutoTargetType) -> Unit,
    channelIdx: Int,
    onChannelChange: (Int) -> Unit,
    channelLabels: List<String>,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    val pickerLabels = when (targetType.kind) {
        TargetKind.Channel -> channelLabels
        TargetKind.MixerTrack -> remember(channelLabels.size) {
            List(channelLabels.size) { "Track ${it + 1}" }
        }
        TargetKind.Global -> emptyList()
    }

    Column(Modifier.fillMaxWidth()) {
        Text("Target", color = TextMuted, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))

        // 2 rows of 4 chips
        for (chunk in AutoTargetType.values().toList().chunked(4)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (t in chunk) {
                    AutomationChip(t.label, targetType == t) { onTargetChange(t) }
                }
                repeat(4 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(4.dp))
        }

        if (targetType.kind != TargetKind.Global) {
            Spacer(Modifier.height(8.dp))
            Text(pickerLabel(targetType), color = TextMuted, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            if (pickerLabels.isEmpty()) {
                Text("No channels available", color = TextMuted, fontSize = 12.sp)
            } else {
                AutomationChannelList(
                    channelLabels = pickerLabels,
                    selectedIdx = channelIdx.coerceIn(0, pickerLabels.size - 1),
                    onSelect = onChannelChange,
                )
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Text("Tempo is a global target — no channel or track selection.",
                color = TextMuted, fontSize = 11.sp)
        }

        Spacer(Modifier.height(12.dp))
        Text(targetValueLabel(targetType), color = TextMuted, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = AccentOrange,
                activeTrackColor = AccentOrange,
                inactiveTrackColor = BorderSubtle,
            ),
        )
    }
}

@Composable
private fun RowScope.AutomationChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).height(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) AccentCyan.copy(alpha = 0.25f) else BgRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) AccentCyan else TextMuted,
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
