package com.maxixa.lemon.ui.piano

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

@Composable
fun PatternLengthDialog(
    currentTicks: Int,
    ppq: Int,
    onApply: (ticks: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val barTicks = ppq * 4
    var text by remember { mutableStateOf(if (currentTicks > 0) currentTicks.toString() else "") }
    var auto by remember { mutableStateOf(currentTicks <= 0) }

    val currentLabel = if (currentTicks <= 0) "Auto"
        else if (currentTicks % barTicks == 0) "${currentTicks / barTicks} bar" +
            (if (currentTicks / barTicks == 1) "" else "s")
        else "$currentTicks ticks"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pattern length", color = TextPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(currentLabel, color = TextMuted, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Auto = loop past last note",
                    color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LengthChip("Auto", auto) { auto = true; text = "" }
                    for (b in intArrayOf(1, 2, 4, 8, 16)) {
                        LengthChip("${b}b", !auto && text == (b * barTicks).toString()) {
                            auto = false
                            text = (b * barTicks).toString()
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Custom (ticks)", color = TextMuted, fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it.filter { c -> c.isDigit() }
                        auto = text.isEmpty()
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = TextPrimary, fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = BgRaised,
                        unfocusedContainerColor = BgRaised,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentOrange,
                    ),
                    placeholder = { Text("Auto", color = TextMuted) },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (auto) "Result: Auto (clear explicit length)"
                    else {
                        val t = text.toIntOrNull() ?: 0
                        if (t > 0 && t % barTicks == 0)
                            "Result: ${t / barTicks} bar" + (if (t / barTicks == 1) "" else "s")
                        else "Result: $t ticks"
                    },
                    color = AccentCyan, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ticks = if (auto) 0 else (text.toIntOrNull() ?: 0)
                onApply(ticks)
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
private fun LengthChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) AccentCyan.copy(alpha = 0.25f) else BgRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) AccentCyan else TextMuted,
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
