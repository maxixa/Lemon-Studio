package com.maxixa.lemon.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.icon.AddIcon
import com.maxixa.lemon.ui.icon.BackIcon
import com.maxixa.lemon.ui.icon.DuplicateIcon
import com.maxixa.lemon.ui.icon.PlayIcon
import com.maxixa.lemon.ui.icon.SelectIcon
import com.maxixa.lemon.ui.icon.PencilIcon
import com.maxixa.lemon.ui.icon.StopIcon
import com.maxixa.lemon.ui.theme.*

/**
 * Playlist toolbar — orchestration of slots and chips.
 * Chips (Song/Pat, Loop, Arrangement, Zoom, Trim) live in
 * PlaylistToolbarChips.kt to keep this file under 150 LOC.
 */
@Composable
fun PlaylistToolbar(
    songMode: Boolean,
    isPlaying: Boolean,
    tool: PlaylistTool,
    loopActive: Boolean,
    arrangementLabel: String,
    barsVisible: Float,
    trimMode: Boolean,
    hasSelection: Boolean,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    clipboardSize: Int,
    onBack: () -> Unit,
    onToggleMode: () -> Unit,
    onPlayToggle: () -> Unit,
    onToolChange: (PlaylistTool) -> Unit,
    onAddClip: () -> Unit,
    onAddAudioClip: () -> Unit,
    onAddMarker: () -> Unit,
    onAddAutomation: () -> Unit,
    onDuplicate: () -> Unit,
    onDeleteSelected: () -> Unit,
    onLoopClick: () -> Unit,
    onArrangementClick: () -> Unit,
    onBarsVisibleChange: (Float) -> Unit,
    onToggleTrimMode: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).background(BgElevated),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slot(onBack) { BackIcon(TextPrimary, 18.dp) }
        Slot(onClick = { onToolChange(PlaylistTool.Pencil) }) {
            PencilIcon(if (tool == PlaylistTool.Pencil) AccentOrange else TextMuted, 18.dp)
        }
        Slot(onClick = { onToolChange(PlaylistTool.Select) }) {
            SelectIcon(if (tool == PlaylistTool.Select) AccentOrange else TextMuted, 18.dp)
        }
        Slot(onPlayToggle) {
            if (isPlaying) StopIcon(AccentOrange, 18.dp)
            else PlayIcon(TextPrimary, 18.dp)
        }

        PlaylistToolbarChips(
            songMode = songMode,
            loopActive = loopActive,
            arrangementLabel = arrangementLabel,
            barsVisible = barsVisible,
            trimMode = trimMode,
            onToggleMode = onToggleMode,
            onLoopClick = onLoopClick,
            onArrangementClick = onArrangementClick,
            onBarsVisibleChange = onBarsVisibleChange,
            onToggleTrimMode = onToggleTrimMode,
        )

        Spacer(Modifier.weight(1f))

        Slot(onAddAutomation) {
            Text("\u2699", color = AccentCyan,
                fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Slot(onAddMarker) {
            Text("M", color = AccentOrange, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Slot(onAddAudioClip) {
            Text("\u266a", color = AccentAmber,
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Slot(onAddClip) { AddIcon(TextPrimary, 18.dp) }
        Slot(onCopy) {
            Text("C", color = if (hasSelection) AccentCyan else TextMuted.copy(alpha = 0.4f),
                fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Slot(onPaste) {
            Text("V", color = if (clipboardSize > 0) AccentAmber else TextMuted.copy(alpha = 0.4f),
                fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Slot(onDuplicate) {
            DuplicateIcon(
                if (hasSelection) AccentOrange else TextMuted,
                18.dp,
            )
        }
        Slot(onDeleteSelected) {
            Text("\u00d7", color = AccentRed, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Slot(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(40.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { content() }
}
