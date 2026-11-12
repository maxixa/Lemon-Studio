package com.maxixa.lemon.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.state.*
import com.maxixa.lemon.ui.theme.*

@Composable
fun MixerRoutingMatrixDialog(
    engine: EngineController,
    trackNames: List<String>,
    onApplied: () -> Unit,
    onDismiss: () -> Unit,
) {
    val n = trackNames.size
    var sends by remember {
        mutableStateOf((0 until n).associateWith { src -> engine.mixerGetSends(src) })
    }
    val initial = remember { sends }
    var editCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val activeCount = sends.values.sumOf { m -> m.values.count { it > 0.001f } }
    val hScroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Routing matrix", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("$activeCount active", color = AccentCyan, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                Text("Rows = source. Cols = destination. Tap to edit.",
                    color = TextMuted, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MatrixChip("Reset") {
                        sends = (0 until n).associateWith { _ ->
                            trackNames.indices.associateWith { 0f }
                        }
                    }
                    MatrixChip("Revert") { sends = initial }
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.horizontalScroll(hScroll)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(84.dp))
                        for (dst in 0 until n) {
                            val label = if (dst == 0) "M" else (trackNames.getOrNull(dst)?.take(3) ?: "#$dst")
                            Text(
                                label,
                                color = if (dst == 0) AccentOrange else TextMuted,
                                fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(28.dp),
                                textAlign = TextAlign.Center, maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    for (src in 0 until n) {
                        val srcName = trackNames.getOrNull(src)?.take(9) ?: "#$src"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                srcName,
                                color = if (src == 0) AccentOrange else TextPrimary,
                                fontSize = 10.sp,
                                modifier = Modifier.width(84.dp).padding(start = 4.dp),
                                maxLines = 1,
                            )
                            for (dst in 0 until n) {
                                val level = sends[src]?.get(dst) ?: 0f
                                val isSelf = src == dst
                                val bg = when {
                                    isSelf -> BgDeep
                                    level < 0.001f -> BgRaised
                                    else -> AccentCyan.copy(alpha = 0.18f + 0.55f * level.coerceIn(0f, 1f))
                                }
                                Box(
                                    Modifier.size(26.dp, 22.dp).padding(1.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(bg)
                                        .then(if (!isSelf) Modifier.clickable { editCell = src to dst } else Modifier),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when {
                                        isSelf -> Text("·", color = TextMuted.copy(alpha = 0.5f), fontSize = 9.sp)
                                        level > 0.001f -> Text(
                                            "${(level * 100).toInt()}",
                                            color = BgDeep, fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                                        )
                                        else -> {}
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }

                val cell = editCell
                if (cell != null) {
                    val (src, dst) = cell
                    val srcName = trackNames.getOrNull(src) ?: "#$src"
                    val dstName = if (dst == 0) "MASTER" else (trackNames.getOrNull(dst) ?: "#$dst")
                    val curLevel = sends[src]?.get(dst) ?: 0f
                    Spacer(Modifier.height(12.dp))
                    Text("$srcName → $dstName",
                        color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = curLevel,
                            onValueChange = { newLevel ->
                                sends = sends.toMutableMap().also { outer ->
                                    val inner = (outer[src] ?: emptyMap()).toMutableMap()
                                    inner[dst] = newLevel.coerceIn(0f, 1f)
                                    outer[src] = inner
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = AccentCyan,
                                activeTrackColor = AccentCyan,
                                inactiveTrackColor = BorderSubtle,
                            ),
                        )
                        Text(" ${(curLevel * 100).toInt()}%",
                            color = AccentCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        TextButton(onClick = { editCell = null }) {
                            Text("Done", color = AccentCyan, fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                for ((src, dests) in sends) {
                    val orig = initial[src] ?: emptyMap()
                    for ((dst, level) in dests) {
                        val prev = orig[dst] ?: 0f
                        if (prev != level) {
                            engine.mixerSetSendLevel(src, dst, level.toDouble())
                        }
                    }
                }
                onApplied()
            }) {
                Text("Apply", color = AccentOrange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted, fontSize = 13.sp)
            }
        },
    )
}

@Composable
private fun MatrixChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(BgRaised, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
