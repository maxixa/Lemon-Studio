package com.maxixa.lemon.ui.playlist

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

@Composable
fun MarkerDialog(
    marker: MarkerView,
    ppq: Int,
    onSave: (newName: String, newPositionTicks: Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val barTicks = (ppq * 4).coerceAtLeast(1)
    var name by remember { mutableStateOf(marker.name) }
    var bar by remember { mutableStateOf(marker.position / barTicks) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Text("Marker at bar ${bar + 1}", color = TextPrimary, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Name", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(32) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = TextPrimary, fontSize = 13.sp,
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

                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Position", color = TextPrimary, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        BarButton("\u2212") { bar = (bar - 1).coerceAtLeast(0) }
                        Text("Bar ${bar + 1}",
                            color = AccentOrange, fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp))
                        BarButton("+") { bar += 1 }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.fillMaxWidth().height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentRed.copy(alpha = 0.15f))
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Delete marker", color = AccentRed, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), bar * barTicks) }) {
                Text("Save", color = AccentOrange, fontSize = 13.sp,
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
private fun BarButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(28.dp).width(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(BgRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
