package com.maxixa.lemon.ui.component

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

@Composable
fun MenuPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpenProject: () -> Unit,
    onOpenRecent: () -> Unit,
    onSaveProject: () -> Unit,
    onSaveAs: () -> Unit,
    onAddChannel: () -> Unit,
    onOpenFactory: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenMixer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPianoRoll: () -> Unit,
    onOpenRack: () -> Unit,
    onOpenPlaylist: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
    ) {
        MenuItem("Recent projects") { onOpenRecent(); onDismiss() }
        MenuItem("Open project") { onOpenProject(); onDismiss() }
        MenuItem("Save project") { onSaveProject(); onDismiss() }
        MenuItem("Save As…") { onSaveAs(); onDismiss() }
        MenuItem("Add channel") { onAddChannel(); onDismiss() }
        MenuItem("Factory samples") { onOpenFactory(); onDismiss() }
        MenuItem("Piano roll") { onOpenPianoRoll(); onDismiss() }
        MenuItem("Step sequencer") { onOpenRack(); onDismiss() }
        MenuItem("Playlist") { onOpenPlaylist(); onDismiss() }
        MenuItem("Export…") { onOpenExport(); onDismiss() }
        MenuItem("Mixer") { onOpenMixer(); onDismiss() }
        MenuItem("Settings") { onOpenSettings(); onDismiss() }
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = TextPrimary, fontSize = 13.sp) },
        onClick = onClick,
    )
}
