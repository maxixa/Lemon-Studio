package com.maxixa.lemon.ui.piano

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
import com.maxixa.lemon.data.NoteView
import com.maxixa.lemon.ui.theme.*

@Composable
fun NotePropertiesDialog(
    note: NoteView,
    snapTicks: Int,
    onApply: (NoteView) -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onDismiss: () -> Unit,
) {
    var velocity    by remember { mutableStateOf(note.velocity.coerceIn(1, 127)) }
    var lengthTicks by remember { mutableStateOf(note.length) }
    var pan         by remember { mutableStateOf(note.pan.coerceIn(0, 128)) }
    var finePitch   by remember { mutableStateOf(note.finePitch.coerceIn(0, 255)) }
    var release     by remember { mutableStateOf(note.release.coerceIn(0, 255)) }
    var modX        by remember { mutableStateOf(note.modX.coerceIn(0, 255)) }
    var modY        by remember { mutableStateOf(note.modY.coerceIn(0, 255)) }
    var repeat      by remember { mutableStateOf(note.repeat.coerceIn(0, 14)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Note ${noteKeyName(note.key)}", color = TextPrimary, fontSize = 15.sp)
                Text("pos ${note.position}", color = TextMuted, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                NoteCoreSection(
                    velocity = velocity, onVelocity = { velocity = it },
                    lengthTicks = lengthTicks, onLengthTicks = { lengthTicks = it },
                    step = if (snapTicks > 0) snapTicks else 24,
                )
                Spacer(Modifier.height(12.dp))
                NoteExtrasSection(
                    pan = pan, onPan = { pan = it },
                    finePitch = finePitch, onFinePitch = { finePitch = it },
                    release = release, onRelease = { release = it },
                    modX = modX, onModX = { modX = it },
                    modY = modY, onModY = { modY = it },
                    repeat = repeat, onRepeat = { repeat = it },
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NoteActionChip("Delete", AccentRed, Modifier.weight(1f)) { onDelete() }
                    NoteActionChip("Duplicate", AccentCyan, Modifier.weight(1f)) { onDuplicate() }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(note.copy(
                    velocity = velocity, length = lengthTicks,
                    pan = pan, finePitch = finePitch, release = release,
                    modX = modX, modY = modY, repeat = repeat,
                ))
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
internal fun NoteActionChip(
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.15f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

internal fun noteKeyName(k: Int): String {
    val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    return "${names[k % 12]}${k / 12}"
}
