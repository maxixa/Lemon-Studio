package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.*
import com.maxixa.lemon.ui.theme.*

private data class SnapOption(val denom: Int, val label: String)
private val SNAP_OPTIONS = listOf(
    SnapOption(4,  "1/4"),
    SnapOption(8,  "1/8"),
    SnapOption(16, "1/16"),
    SnapOption(32, "1/32"),
    SnapOption(0,  "Off"),
)

@Composable
fun ZoomDialog(state: PianoRollState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = { Text("View & snap", color = TextPrimary, fontSize = 15.sp) },
        text = {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Horizontal", color = TextPrimary, fontSize = 13.sp)
                    Text("%.1f beats".format(state.hBeats), color = AccentOrange,
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = state.hBeats,
                    onValueChange = { state.hBeats = it },
                    valueRange = 1f..16f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentOrange,
                        activeTrackColor = AccentOrange,
                        inactiveTrackColor = BorderSubtle,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Vertical", color = TextPrimary, fontSize = 13.sp)
                    Text("%d rows".format(state.vRows), color = AccentOrange,
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = state.vRows.toFloat(),
                    onValueChange = { state.vRows = it.toInt() },
                    valueRange = 8f..48f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentOrange,
                        activeTrackColor = AccentOrange,
                        inactiveTrackColor = BorderSubtle,
                    ),
                )
                Spacer(Modifier.height(16.dp))
                Text("Snap grid", color = TextPrimary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (opt in SNAP_OPTIONS) {
                        val active = state.snapDenominator == opt.denom
                        Box(
                            Modifier.weight(1f).height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) AccentOrange else BgRaised)
                                .clickable { state.snapDenominator = opt.denom },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(opt.label,
                                color = if (active) BgDeep else TextPrimary,
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (state.snapDenominator == 0) "Snap off — notes land anywhere"
                    else "${state.snapTicks} ticks per grid line",
                    color = TextMuted, fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Show ghost notes", color = TextPrimary, fontSize = 13.sp)
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (state.showGhosts) AccentCyan.copy(alpha = 0.25f) else BgRaised)
                            .clickable { state.showGhosts = !state.showGhosts }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            if (state.showGhosts) "On" else "Off",
                            color = if (state.showGhosts) AccentCyan else TextMuted,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = AccentOrange, fontSize = 13.sp)
            }
        },
    )
}
