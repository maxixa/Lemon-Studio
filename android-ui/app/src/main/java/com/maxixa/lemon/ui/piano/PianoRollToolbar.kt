package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.PianoRollState
import com.maxixa.lemon.state.Tool
import com.maxixa.lemon.ui.icon.BackIcon
import com.maxixa.lemon.ui.icon.EraserIcon
import com.maxixa.lemon.ui.icon.PanIcon
import com.maxixa.lemon.ui.icon.PencilIcon
import com.maxixa.lemon.ui.icon.SelectIcon
import com.maxixa.lemon.ui.icon.ZoomIcon
import com.maxixa.lemon.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PianoRollToolbar(
    state: PianoRollState,
    onBack: () -> Unit,
    onOpenZoom: () -> Unit,
    patternLabel: String = "",
    onPatternTap: () -> Unit = {},
    onPatternLongPress: () -> Unit = {},
    channelLabel: String = "",
    onChannelTap: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).background(BgElevated),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolSlot(onClick = onBack) { BackIcon(TextPrimary, 18.dp) }
        Spacer(Modifier.width(4.dp))

        ToolSlot(active = state.tool == Tool.Pencil,
                 onClick = { state.tool = Tool.Pencil }) {
            PencilIcon(if (state.tool == Tool.Pencil) AccentOrange else TextMuted, 20.dp)
        }
        ToolSlot(active = state.tool == Tool.Eraser,
                 onClick = { state.tool = Tool.Eraser }) {
            EraserIcon(if (state.tool == Tool.Eraser) AccentOrange else TextMuted, 20.dp)
        }
        ToolSlot(active = state.tool == Tool.Select,
                 onClick = { state.tool = Tool.Select }) {
            SelectIcon(if (state.tool == Tool.Select) AccentOrange else TextMuted, 20.dp)
        }
        ToolSlot(active = state.tool == Tool.Pan,
                 onClick = { state.tool = Tool.Pan }) {
            PanIcon(if (state.tool == Tool.Pan) AccentOrange else TextMuted, 20.dp)
        }

        Spacer(Modifier.width(8.dp))

        if (patternLabel.isNotEmpty()) {
            Box(
                Modifier.height(28.dp)
                    .background(BgRaised, RoundedCornerShape(4.dp))
                    .combinedClickable(
                        onClick = onPatternTap,
                        onLongClick = onPatternLongPress,
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    patternLabel,
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(4.dp))
        }

        if (channelLabel.isNotEmpty()) {
            Box(
                Modifier.height(28.dp)
                    .background(BgRaised, RoundedCornerShape(4.dp))
                    .clickable(onClick = onChannelTap)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    channelLabel,
                    color = AccentCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        ToolSlot(onClick = onOpenZoom) { ZoomIcon(TextPrimary, 18.dp) }
    }
}

@Composable
private fun ToolSlot(
    active: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(40.dp)
            .background(if (active) BgRaised else BgElevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
