package com.maxixa.lemon.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn

@Composable
fun ChannelOptionsDialog(
    channelIndex: Int,
    currentName: String,
    hasSample: Boolean,
    canDelete: Boolean,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onRename: (String) -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onChangeInstrument: () -> Unit = {},
    currentInstrumentLabel: String = "",
    onChangeColor: () -> Unit = {},
    currentColorPacked: Int = -1,
    onStartOffsetChange: (Float) -> Unit = {},
    currentStartOffset: Float = 0f,
    onOpenSampleEditor: () -> Unit = {},
    onLoadSample: () -> Unit,
    onClearSample: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    var confirmingDelete by remember { mutableStateOf(false) }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            containerColor = BgElevated,
            title = { Text("Delete channel ${channelIndex + 1}?", color = TextPrimary, fontSize = 15.sp) },
            text = {
                Text(
                    "This removes the channel and all of its notes from every pattern. " +
                    "You can Undo this from the top bar.",
                    color = TextMuted, fontSize = 12.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete() }) {
                    Text("Delete", color = AccentRed,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text("Cancel", color = TextMuted, fontSize = 13.sp)
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Channel ${channelIndex + 1}", color = TextPrimary, fontSize = 15.sp)
                Text(
                    if (hasSample) "sample loaded" else "built-in tone",
                    color = if (hasSample) AccentGreen else TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Name", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = TextPrimary, fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = BgRaised,
                        unfocusedContainerColor = BgRaised,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentOrange,
                    ),
                )

                if (hasSample) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth().height(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentOrange.copy(alpha = 0.15f))
                            .clickable { onOpenSampleEditor() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Open sample editor",
                            color = AccentOrange, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("Order", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ActionChip(
                        label = "↑ Up",
                        tint = if (canMoveUp) AccentCyan else TextMuted,
                        modifier = Modifier.weight(1f),
                    ) { if (canMoveUp) onMoveUp() }
                    ActionChip(
                        label = "↓ Down",
                        tint = if (canMoveDown) AccentCyan else TextMuted,
                        modifier = Modifier.weight(1f),
                    ) { if (canMoveDown) onMoveDown() }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ActionChip(
                        label = "Duplicate",
                        tint = AccentAmber,
                        modifier = Modifier.weight(1f),
                    ) { onDuplicate() }
                }

                Spacer(Modifier.height(20.dp))
                Text("Color", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BgRaised)
                        .clickable { onChangeColor() }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                            .background(
                                if (currentColorPacked >= 0)
                                    androidx.compose.ui.graphics.Color(0xFF000000.toInt() or currentColorPacked)
                                else AccentOrange
                            )
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (currentColorPacked >= 0) "Custom color" else "Default (tap to change)",
                        color = if (currentColorPacked >= 0) AccentCyan else TextMuted,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text("Instrument", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth().height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BgRaised)
                        .clickable { onChangeInstrument() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        currentInstrumentLabel.ifEmpty { "Built-in tone (tap to pick synth)" },
                        color = if (currentInstrumentLabel.isEmpty()) TextMuted else AccentCyan,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text("Sample", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ActionChip(
                        label = if (hasSample) "Replace sample" else "Load sample",
                        tint = AccentCyan,
                        modifier = Modifier.weight(1f),
                    ) { onLoadSample() }
                    if (hasSample) {
                        ActionChip(
                            label = "Clear",
                            tint = AccentAmber,
                            modifier = Modifier.weight(1f),
                        ) { onClearSample() }
                    }
                }

                Spacer(Modifier.height(20.dp))
                if (hasSample) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Start offset", color = TextMuted, fontSize = 11.sp)
                        Text(
                            "${(currentStartOffset * 100).toInt()}%",
                            color = AccentOrange, fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    androidx.compose.material3.Slider(
                        value = currentStartOffset,
                        onValueChange = { onStartOffsetChange(it) },
                        valueRange = 0f..0.99f,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = AccentOrange,
                            activeTrackColor = AccentOrange,
                            inactiveTrackColor = BorderSubtle,
                        ),
                    )
                }

                Text("Actions", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                if (canDelete) {
                    Row(
                        Modifier.fillMaxWidth().height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentRed.copy(alpha = 0.15f))
                            .clickable { confirmingDelete = true }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Delete channel",
                            color = AccentRed, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Text("Can't delete the last channel.",
                        color = TextMuted, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onRename(name.trim())
                onDismiss()
            }) {
                Text("Rename", color = AccentOrange,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
private fun ActionChip(
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
        Text(label, color = tint, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold)
    }
}
