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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

@Composable
fun LoopRangeDialog(
    ppq: Int,
    barNum: Int = 4,
    barDen: Int = 4,
    initialStart: Long,
    initialEnd: Long,
    onSet: (startTick: Long, endTick: Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val barTicks = (ppq * barNum.coerceAtLeast(1) * 4 / barDen.coerceAtLeast(1)).coerceAtLeast(1)
    var startBar by remember { mutableStateOf(
        if (initialStart >= 0) (initialStart / barTicks).toString() else "1"
    ) }
    var endBar by remember { mutableStateOf(
        if (initialEnd > 0) ((initialEnd + barTicks - 1) / barTicks).toString() else "4"
    ) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = { Text("Loop range", color = TextPrimary, fontSize = 15.sp) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Loop between two bar lines (1-based). Clear returns to looping the full pattern.",
                    color = TextMuted, fontSize = 11.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Field("Start bar", startBar, { startBar = it })
                    Text("→", color = TextMuted, fontSize = 16.sp)
                    Field("End bar", endBar, { endBar = it })
                }
                Spacer(Modifier.height(14.dp))
                if (initialStart >= 0 || initialEnd > 0) {
                    Box(
                        Modifier.fillMaxWidth().height(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(BgRaised)
                            .clickable { onClear(); onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Clear loop range",
                            color = AccentRed, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = (startBar.toLongOrNull() ?: 1L).coerceAtLeast(1)
                val e = (endBar.toLongOrNull() ?: 4L).coerceAtLeast(s + 1)
                val startTick = (s - 1) * barTicks
                val endTick = e * barTicks
                onSet(startTick, endTick)
                onDismiss()
            }) {
                Text("Apply", color = AccentOrange,
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
private fun RowScope.Field(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.weight(1f)) {
        Text(label, color = TextMuted, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(3)) },
            singleLine = true,
            textStyle = TextStyle(
                color = TextPrimary, fontSize = 14.sp,
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
        )
    }
}
