package com.maxixa.lemon.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

@Composable
fun EffectPickerDialog(
    trackIndex: Int,
    slotIndex: Int,
    currentId: String,
    currentlyBypassed: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    catalog: List<Pair<String, String>>,
    onPick: (String) -> Unit,
    onBypass: (Boolean) -> Unit = {},
    onEditParams: () -> Unit = {},
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = { Text("Slot ${slotIndex + 1} — track ${trackIndex + 1}",
            color = TextPrimary, fontSize = 15.sp) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (currentId.isNotEmpty()) {
                    // Bypass chip
                    Row(
                        Modifier.fillMaxWidth().height(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (currentlyBypassed) BgRaised else AccentCyan.copy(alpha = 0.2f))
                            .clickable { onBypass(!currentlyBypassed) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (currentlyBypassed) "Bypassed (tap to enable)" else "Active (tap to bypass)",
                            color = if (currentlyBypassed) TextMuted else AccentCyan,
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))

                    // Edit params + reorder
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier.weight(1f).height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentOrange.copy(alpha = 0.15f))
                                .clickable { onEditParams(); onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Edit parameters",
                                color = AccentOrange, fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold)
                        }
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))
                                .background(if (canMoveUp) BgRaised else BgRaised.copy(alpha = 0.3f))
                                .clickable { if (canMoveUp) { onMoveUp(); onDismiss() } },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("↑",
                                color = if (canMoveUp) TextPrimary else TextMuted.copy(alpha = 0.3f),
                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))
                                .background(if (canMoveDown) BgRaised else BgRaised.copy(alpha = 0.3f))
                                .clickable { if (canMoveDown) { onMoveDown(); onDismiss() } },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("↓",
                                color = if (canMoveDown) TextPrimary else TextMuted.copy(alpha = 0.3f),
                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Text("Pick an effect to insert in this slot.",
                    color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(catalog) { (id, name) ->
                        val active = id == currentId
                        Row(
                            Modifier.fillMaxWidth().height(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) AccentOrange.copy(alpha = 0.2f) else BgRaised)
                                .clickable { onPick(id); onDismiss() }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name,
                                color = if (active) AccentOrange else TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
                if (currentId.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.fillMaxWidth().height(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(BgRaised)
                            .clickable { onClear(); onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Remove effect",
                            color = AccentRed, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted, fontSize = 13.sp)
            }
        },
    )
}
