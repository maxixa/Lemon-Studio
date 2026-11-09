package com.maxixa.lemon.ui.rack

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import com.maxixa.lemon.ui.piano.NoteSubheader
import com.maxixa.lemon.ui.piano.ToggleChip
import com.maxixa.lemon.ui.theme.*

private enum class FillMode(val label: String) {
    EveryN("Every N"),
    Euclidean("Euclidean"),
    Random("Random"),
}

/**
 * Fill dialog: pick channel + mode + params, preview hit count, apply.
 * Destructive per channel — replaces all existing step notes on the target.
 * On apply, hands (channelIdx, mask) to the screen which calls JNI once.
 */
@Composable
fun FillDialog(
    trackNames: List<String>,
    maskSize: Int,
    onApply: (channelIdx: Int, mask: List<Boolean>) -> Unit,
    onDismiss: () -> Unit,
) {
    var channel by remember { mutableStateOf(0) }
    var mode by remember { mutableStateOf(FillMode.EveryN) }
    var everyN by remember { mutableStateOf(4) }
    var pulses by remember { mutableStateOf(maskSize / 4) }
    var density by remember { mutableStateOf(25) }

    val mask = when (mode) {
        FillMode.EveryN -> FillMath.everyN(maskSize, everyN)
        FillMode.Euclidean -> FillMath.euclidean(maskSize, pulses)
        FillMode.Random -> FillMath.random(maskSize, density)
    }
    val hitCount = mask.count { it }
    val channelLabel = trackNames.getOrNull(channel) ?: "Ch ${channel + 1}"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Fill · $channelLabel", color = TextPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold)
                Text("$hitCount / $maskSize", color = AccentCyan, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Replaces all steps on the target channel.",
                    color = AccentRed, fontSize = 10.sp)
                Spacer(Modifier.height(10.dp))

                NoteSubheader("Channel")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in trackNames.indices.take(8)) {
                        ToggleChip("${i + 1}", channel == i) { channel = i }
                    }
                }
                if (trackNames.size > 8) {
                    Text("+${trackNames.size - 8} more (first 8 shown)",
                        color = TextMuted, fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(Modifier.height(10.dp))
                NoteSubheader("Mode")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (m in FillMode.entries) ToggleChip(m.label, mode == m) { mode = m }
                }

                Spacer(Modifier.height(10.dp))
                when (mode) {
                    FillMode.EveryN -> EveryNParams(everyN) { everyN = it }
                    FillMode.Euclidean -> EuclideanParams(pulses, maskSize) { pulses = it }
                    FillMode.Random -> RandomParams(density) { density = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(channel, mask) }) {
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
