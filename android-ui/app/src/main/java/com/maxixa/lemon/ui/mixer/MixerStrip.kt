package com.maxixa.lemon.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

@Composable
fun MixerStrip(
    track: MixerTrackView,
    slotNames: List<String>,
    bypassedSlots: Set<Int> = emptySet(),
    isMaster: Boolean = false,
    onVolume: (Float) -> Unit,
    onPan: (Float) -> Unit,
    onSlotTap: (Int) -> Unit,
    onSendsTap: () -> Unit = {},
    isBus: Boolean = false,
    onToggleBus: () -> Unit = {},
) {
    Column(
        Modifier.width(72.dp).fillMaxHeight()
            .background(if (isMaster) BgRaised else BgElevated)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (isMaster) "MASTER" else track.name,
                color = if (isMaster) AccentOrange else TextPrimary,
                fontSize = 9.sp,
                fontWeight = if (isMaster) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (!isMaster) {
                Spacer(Modifier.width(2.dp))
                Text(
                    "B",
                    color = if (isBus) BgDeep else TextMuted.copy(alpha = 0.3f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            if (isBus) AccentAmber else BorderSubtle,
                            RoundedCornerShape(2.dp),
                        )
                        .clickable { onToggleBus() }
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))

        // Effect slots (4 visible)
        for (i in 0 until 4) {
            val effectName = slotNames.getOrNull(i) ?: ""
            val active = effectName.isNotEmpty()
            Box(
                Modifier.fillMaxWidth().height(22.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (active) AccentCyan.copy(alpha = 0.25f) else BgRaised)
                    .clickable { onSlotTap(i) }
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (active) shortName(effectName) else "+",
                    color = if (active) {
                        if (bypassedSlots.contains(i)) TextMuted.copy(alpha = 0.4f) else AccentCyan
                    } else TextMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(2.dp))
        }

        Spacer(Modifier.height(4.dp))
        Fader(value = track.volume, onChange = onVolume,
            modifier = Modifier.width(36.dp).weight(1f))
        Spacer(Modifier.height(4.dp))
        PanKnob(value = track.pan, onChange = onPan,
            modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("%d".format((track.volume * 100).toInt()),
                color = TextMuted, fontSize = 8.sp)
            if (!isMaster) {
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier.size(14.dp).clickable { onSendsTap() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("\u2192", color = AccentCyan, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun shortName(full: String): String = when {
    full.contains("Reverb",    ignoreCase = true) -> "Rev"
    full.contains("Delay",     ignoreCase = true) -> "Dly"
    full.contains("Limiter",   ignoreCase = true) -> "Lim"
    full.contains("EQ",        ignoreCase = true) -> "EQ"
    full.contains("Filter",    ignoreCase = true) -> "Flt"
    else -> full.take(3)
}
