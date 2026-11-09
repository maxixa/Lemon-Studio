package com.maxixa.lemon.ui.rack

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

/**
 * Pattern length picker for the Step Sequencer toolbar.
 * `currentBars` = -1 (no project), 0 (Auto), N (explicit N bars).
 */
@Composable
fun PatternLengthDialog(
    currentBars: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = { Text("Pattern length", color = TextPrimary, fontSize = 15.sp) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Auto = loop past the last note",
                    color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LengthChip("Auto", currentBars == 0) { onPick(0) }
                    for (b in intArrayOf(1, 2, 4, 8, 16)) {
                        LengthChip("${b}b", currentBars == b) { onPick(b) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AccentOrange, fontSize = 13.sp)
            }
        },
    )
}

@Composable
private fun LengthChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(30.dp)
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
