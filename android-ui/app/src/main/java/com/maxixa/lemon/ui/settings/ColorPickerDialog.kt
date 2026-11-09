package com.maxixa.lemon.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

private val PALETTE = listOf(
    0xFF6B35, 0x00E5FF, 0xB388FF, 0x52B788, 0xFFB02E, 0xE63946,
    0xFF76AB, 0x8B5CF6, 0xF59E0B, 0x10B981, 0xEC4899, 0x64748B,
)

@Composable
fun ColorPickerDialog(
    channelIndex: Int,
    currentPacked: Int,
    onPick: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = { Text("Color — channel ${channelIndex + 1}",
            color = TextPrimary, fontSize = 15.sp) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Pick a color", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                for (row in 0 until 2) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        for (col in 0 until 6) {
                            val packed = PALETTE[row * 6 + col]
                            val active = packed == currentPacked
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(Color(0xFF000000.toInt() or packed))
                                    .border(
                                        if (active) 3.dp else 1.dp,
                                        if (active) AccentOrange else BorderSubtle,
                                        CircleShape,
                                    )
                                    .clickable { onPick(packed) },
                            )
                        }
                    }
                    if (row == 0) Spacer(Modifier.height(10.dp))
                }
                if (currentPacked >= 0) {
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier.fillMaxWidth().height(34.dp)
                            .clickable { onClear() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Reset to default",
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
