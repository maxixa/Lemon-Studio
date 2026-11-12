package com.maxixa.lemon.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

/**
 * The middle strip of PlaylistToolbar: Song/Pat toggle, Loop chip,
 * arrangement chip, zoom widget, Trim toggle.
 *
 * Extracted so PlaylistToolbar stays under the 150-LOC ceiling. Takes
 * RowScope because every chip emits directly into the parent Row (no
 * nested Row wrapping — keeps horizontal spacing/weight semantics).
 */
@Composable
internal fun RowScope.PlaylistToolbarChips(
    songMode: Boolean,
    loopActive: Boolean,
    arrangementLabel: String,
    barsVisible: Float,
    trimMode: Boolean,
    onToggleMode: () -> Unit,
    onLoopClick: () -> Unit,
    snapMode: Int,
    onSnapCycle: () -> Unit,
    onArrangementClick: () -> Unit,
    onBarsVisibleChange: (Float) -> Unit,
    onToggleTrimMode: () -> Unit,
) {
    // Song / Pat toggle
    Box(
        Modifier.height(24.dp).width(64.dp).background(BgRaised).clickable(onClick = onToggleMode),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (songMode) "Song" else "Pat",
            color = if (songMode) AccentCyan else AccentOrange,
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
        )
    }

    // Loop chip
    Box(
        Modifier.height(24.dp).width(48.dp)
            .background(if (loopActive) AccentCyan.copy(alpha = 0.2f) else BgRaised)
            .clickable(onClick = onLoopClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Loop",
            color = if (loopActive) AccentCyan else TextMuted,
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
        )
    }

    // Snap chip

    Box(

        Modifier.height(24.dp).width(32.dp)

            .background(if (snapMode > 0) AccentAmber.copy(alpha = 0.2f) else BgRaised)

            .clickable(onClick = onSnapCycle),

        contentAlignment = Alignment.Center,

    ) {

        Text(

            when (snapMode) { 0 -> "\u00b7"; 1 -> "1b"; 2 -> "\u00bdb"; 3 -> "\u00bcb"; else -> "\u00b7" },

            color = if (snapMode > 0) AccentAmber else TextMuted,

            fontSize = 10.sp, fontWeight = FontWeight.Bold,

        )

    }


    // Arrangement chip
    Box(
        Modifier.height(24.dp).width(64.dp)
            .background(BgRaised)
            .clickable(onClick = onArrangementClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            arrangementLabel,
            color = AccentCyan,
            fontSize = 10.sp, fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }

    // Zoom widget: − N b +
    Box(
        Modifier.height(24.dp)
            .background(BgRaised, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(20.dp).clickable {
                    if (barsVisible > 1f) onBarsVisibleChange((barsVisible / 2f).coerceAtLeast(1f))
                },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "\u2212",
                    color = if (barsVisible > 1f) TextPrimary else TextMuted.copy(alpha = 0.4f),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
            }
            Text(
                if (barsVisible >= 1f && barsVisible % 1f == 0f) "${barsVisible.toInt()}b"
                else "%.1fb".format(barsVisible),
                color = AccentCyan,
                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Box(
                Modifier.size(20.dp).clickable {
                    if (barsVisible < 32f) onBarsVisibleChange((barsVisible * 2f).coerceAtMost(32f))
                },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+",
                    color = if (barsVisible < 32f) TextPrimary else TextMuted.copy(alpha = 0.4f),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    // Trim mode toggle
    Box(
        Modifier.height(24.dp).width(28.dp)
            .background(if (trimMode) AccentCyan.copy(alpha = 0.2f) else BgRaised)
            .clickable(onClick = onToggleTrimMode),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "T",
            color = if (trimMode) AccentCyan else TextMuted,
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
        )
    }
}
