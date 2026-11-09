package com.maxixa.lemon.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScope
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
import com.maxixa.lemon.ui.theme.*

@Composable
fun TimeSigDialog(
    currentNum: Int,
    currentDen: Int,
    onApply: (num: Int, den: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var num by remember { mutableStateOf(currentNum.coerceAtLeast(1)) }
    var den by remember { mutableStateOf(currentDen.coerceAtLeast(1)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Time signature", color = TextPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold)
                Text("$num/$den", color = AccentOrange, fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Numerator", color = TextMuted, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (n in listOf(2, 3, 4, 5, 6, 7, 9, 12)) {
                        Chip("$n", num == n) { num = n }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Denominator", color = TextMuted, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (d in listOf(2, 4, 8, 16)) {
                        Chip("/$d", den == d) { den = d }
                    }
                }
                Spacer(Modifier.height(14.dp))

                Text(
                    "Changing meter affects playback loop length in the current session. " +
                    "Playlist and piano roll grids still draw 4/4 until the grid update ships.",
                    color = TextMuted, fontSize = 10.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(num, den) }) {
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
private fun RowScope.Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) AccentCyan.copy(alpha = 0.25f) else BgRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) AccentCyan else TextMuted,
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace)
    }
}
