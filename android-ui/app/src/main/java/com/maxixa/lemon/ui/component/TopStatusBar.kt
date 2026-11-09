package com.maxixa.lemon.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.icon.FullscreenIcon
import com.maxixa.lemon.ui.icon.MenuIcon
import com.maxixa.lemon.ui.icon.RedoIcon
import com.maxixa.lemon.ui.icon.UndoIcon
import com.maxixa.lemon.ui.icon.ZenIcon
import com.maxixa.lemon.ui.theme.*

@Composable
fun TopStatusBar(
    bpm: Float,
    positionFrames: Long,
    sampleRate: Int,
    isFullscreen: Boolean,
    isZen: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    masterPeak: Float = 0f,
    onOpenProject: () -> Unit,
    onOpenRecent: () -> Unit,
    onSaveProject: () -> Unit,
    onSaveAs: () -> Unit,
    onAddChannel: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPianoRoll: () -> Unit,
    onOpenRack: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onOpenMixer: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenFactory: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleZen: () -> Unit,
    onTempoTap: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }
    val timeStr = formatFrames(positionFrames, sampleRate)

    Row(
        Modifier.fillMaxWidth().height(32.dp).background(BgElevated)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            IconSlot(onClick = { showMenu = true }) { MenuIcon(TextPrimary, 16.dp) }
            MenuPopup(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                onOpenProject = onOpenProject,
                onOpenRecent = onOpenRecent,
                onSaveProject = onSaveProject,
                onSaveAs = onSaveAs,
                onAddChannel = onAddChannel,
                onOpenSettings = onOpenSettings,
                onOpenPianoRoll = onOpenPianoRoll,
                onOpenRack = onOpenRack,
                onOpenPlaylist = onOpenPlaylist,
                onOpenMixer = onOpenMixer,
                onOpenExport = onOpenExport,
                onOpenFactory = onOpenFactory,
            )
        }
        IconSlot(onClick = { if (canUndo) onUndo() }) {
            UndoIcon(if (canUndo) TextPrimary else TextMuted.copy(alpha = 0.35f), 16.dp)
        }
        IconSlot(onClick = { if (canRedo) onRedo() }) {
            RedoIcon(if (canRedo) TextPrimary else TextMuted.copy(alpha = 0.35f), 16.dp)
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(timeStr, color = TextPrimary, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text("%.1f BPM".format(bpm), color = AccentOrange,
                fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(onClick = onTempoTap))
        }
        Spacer(Modifier.weight(1f))
        // Master peak meter
        Box(
            Modifier.padding(horizontal = 4.dp).width(28.dp).height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(BgRaised),
        ) {
            Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth(masterPeak.coerceIn(0f, 1f))
                    .background(
                        when {
                            masterPeak > 0.9f -> AccentRed
                            masterPeak > 0.7f -> AccentAmber
                            else -> AccentGreen
                        }
                    ),
            )
        }
        Spacer(Modifier.width(4.dp))

        IconSlot(onClick = onToggleFullscreen) {
            FullscreenIcon(if (isFullscreen) AccentOrange else TextPrimary, 16.dp)
        }
        IconSlot(onClick = onToggleZen) {
            ZenIcon(if (isZen) AccentOrange else TextPrimary, 16.dp)
        }
    }
}

@Composable
private fun IconSlot(onClick: () -> Unit = {}, content: @Composable () -> Unit) {
    Box(Modifier.size(32.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { content() }
}

private fun formatFrames(frames: Long, sampleRate: Int): String {
    if (sampleRate <= 0) return "0:00.000"
    val totalMs = frames * 1000 / sampleRate
    val min = totalMs / 60_000
    val sec = (totalMs / 1000) % 60
    val ms  = totalMs % 1000
    return "%d:%02d.%03d".format(min, sec, ms)
}
