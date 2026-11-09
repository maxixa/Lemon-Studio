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
import com.maxixa.lemon.state.*
import com.maxixa.lemon.ui.theme.*

/**
 * Scale lock dialog: off/on + root + scale. When enabled, new pencil-added
 * notes and pitch-drags snap to the nearest scale note. Existing notes are
 * untouched (retroactive snap is a Transform feature, not a lock).
 */
@Composable
fun ScaleLockDialog(
    state: PianoRollState,
    onApplied: () -> Unit,
    onDismiss: () -> Unit,
) {
    var enabled by remember { mutableStateOf(state.scaleLockEnabled) }
    var root by remember { mutableStateOf(state.scaleRoot) }
    var scaleIdx by remember { mutableStateOf(state.scaleType) }

    val scaleLabel = SCALE_PRESETS.getOrNull(scaleIdx)?.label ?: "Major"
    val rootLabel = SCALE_ROOT_NAMES.getOrNull(root) ?: "C"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Scale lock", color = TextPrimary, fontSize = 15.sp)
                Text(
                    "$rootLabel $scaleLabel",
                    color = if (enabled) AccentCyan else TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScaleToggleChip("Off", !enabled) { enabled = false }
                    ScaleToggleChip("On", enabled) { enabled = true }
                }

                Spacer(Modifier.height(12.dp))
                Text("Root", color = TextMuted, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in 0..11) {
                        SmallChip(SCALE_ROOT_NAMES[i], root == i, Modifier.weight(1f)) { root = i }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Scale", color = TextMuted, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                for (row in SCALE_PRESETS.indices.chunked(4)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (i in row) {
                            SmallChip(SCALE_PRESETS[i].label, scaleIdx == i, Modifier.weight(1f)) {
                                scaleIdx = i
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(3.dp))
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    if (!enabled) "New notes land at any pitch."
                    else "New notes snap to $rootLabel $scaleLabel. Existing notes unaffected.",
                    color = TextMuted, fontSize = 10.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                state.scaleLockEnabled = enabled
                state.scaleRoot = root
                state.scaleType = scaleIdx
                onApplied()
                onDismiss()
            }) {
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

@Composable
private fun ScaleToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(30.dp).width(72.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) AccentOrange.copy(alpha = 0.25f) else BgRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) AccentOrange else TextMuted,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SmallChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.height(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) AccentCyan.copy(alpha = 0.25f) else BgRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) AccentCyan else TextMuted,
            fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
