package com.maxixa.lemon.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.icon.ForwardIcon
import com.maxixa.lemon.ui.icon.LoopIcon
import com.maxixa.lemon.ui.icon.MetronomeIcon
import com.maxixa.lemon.ui.icon.PlayIcon
import com.maxixa.lemon.ui.icon.RecordIcon
import com.maxixa.lemon.ui.icon.RewindIcon
import com.maxixa.lemon.ui.icon.StopIcon
import com.maxixa.lemon.ui.theme.*

@Composable
fun TransportBar(
    isPlaying: Boolean,
    isRecording: Boolean,
    isLooping: Boolean,
    isMetronomeOn: Boolean,
    loopRangeStart: Long = -1L,
    loopRangeEnd: Long = -1L,
    timeSigNum: Int = 4,
    timeSigDen: Int = 4,
    onPlayToggle: () -> Unit,
    onStop: () -> Unit,
    onRecordToggle: () -> Unit,
    onLoopToggle: () -> Unit,
    onMetronomeToggle: () -> Unit,
    onMetronomeLongPress: () -> Unit = {},
    onLoopRangeTap: () -> Unit = {},
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onTimeSigTap: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(72.dp).background(BgElevated)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(onRewind) { RewindIcon(TextMuted, 18.dp) }

        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(if (isPlaying) AccentOrange else BgRaised)
                .clickable { if (isPlaying) onStop() else onPlayToggle() },
            contentAlignment = Alignment.Center,
        ) {
            if (isPlaying) StopIcon(BgDeep, 20.dp)
            else PlayIcon(TextPrimary, 22.dp)
        }

        IconBox(onRecordToggle) {
            RecordIcon(if (isRecording) AccentRed
                else AccentRed.copy(alpha = 0.5f), 20.dp)
        }

        IconBox(onLoopToggle, active = isLooping) {
            LoopIcon(if (isLooping) AccentCyan else TextMuted, 20.dp)
        }

        Box(
            Modifier.height(32.dp).clip(RoundedCornerShape(6.dp))
                .background(BgRaised)
                .clickable { onTimeSigTap() }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("$timeSigNum/$timeSigDen", color = AccentCyan,
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
        IconBox(onClick = onMetronomeToggle, onLongClick = onMetronomeLongPress, active = isMetronomeOn) {
            MetronomeIcon(if (isMetronomeOn) AccentAmber else TextMuted, 20.dp)
        }

        IconBox(onForward) { ForwardIcon(TextMuted, 18.dp) }
    }
}

@Composable
private fun IconBox(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    active: Boolean = false,
    content: @Composable () -> Unit,
) {
    val combined = if (onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
            .background(if (active) BgRaised else Color.Transparent)
            .then(combined),
        contentAlignment = Alignment.Center,
    ) { content() }
}
