package com.maxixa.lemon.ui.rack

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.icon.BackIcon
import com.maxixa.lemon.ui.theme.*

/**
 * Step Sequencer toolbar — back, pattern chip, length chip, swing chip, ⋮ menu.
 * Owns its own dropdown state so the parent screen doesn't need to.
 */
@Composable
fun SequencerToolbar(
    patternLabel: String,
    lengthLabel: String,
    swingLabel: String,
    onBack: () -> Unit,
    onOpenPatternPicker: () -> Unit,
    onOpenLengthPicker: () -> Unit,
    onOpenSwingPicker: () -> Unit,
    onOpenFillPicker: () -> Unit,
    onAddPattern: () -> Unit,
    onDuplicatePattern: () -> Unit,
    onRenamePattern: () -> Unit,
) {
    var showMore by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().height(40.dp).background(BgElevated),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { BackIcon(TextPrimary, 18.dp) }

        Chip(patternLabel, TextPrimary, onOpenPatternPicker)
        Spacer(Modifier.width(6.dp))
        Chip(lengthLabel, AccentCyan, onOpenLengthPicker)
        Spacer(Modifier.width(6.dp))
        Chip(swingLabel, AccentOrange, onOpenSwingPicker)
        Spacer(Modifier.width(6.dp))
        Chip("Fill", AccentOrange, onOpenFillPicker)

        Spacer(Modifier.weight(1f))

        Box {
            Box(
                Modifier.size(40.dp).clickable { showMore = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("\u22ee", color = TextPrimary, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold)
            }
            DropdownMenu(
                expanded = showMore,
                onDismissRequest = { showMore = false },
                containerColor = BgElevated,
            ) {
                MenuEntry("Add new pattern") { showMore = false; onAddPattern() }
                MenuEntry("Duplicate current") { showMore = false; onDuplicatePattern() }
                MenuEntry("Rename current") { showMore = false; onRenamePattern() }
            }
        }
    }
}

@Composable
private fun Chip(label: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier.height(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(BgRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = tint,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun MenuEntry(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = TextPrimary, fontSize = 13.sp) },
        onClick = onClick,
    )
}
