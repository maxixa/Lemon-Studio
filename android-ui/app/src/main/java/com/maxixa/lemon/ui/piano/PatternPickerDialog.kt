package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

@Composable
fun PatternPickerDialog(
    patternNames: List<String>,
    currentPattern: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = { Text("Switch pattern", color = TextPrimary, fontSize = 15.sp) },
        text = {
            if (patternNames.isEmpty()) {
                Text("No patterns", color = TextMuted, fontSize = 13.sp)
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(patternNames.size) { i ->
                        val num = i + 1
                        val active = num == currentPattern
                        Row(
                            Modifier.fillMaxWidth().height(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) AccentOrange.copy(alpha = 0.2f) else BgRaised)
                                .clickable { onPick(num); onDismiss() }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                                    .background(if (active) AccentOrange else BorderSubtle),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$num",
                                    color = if (active) BgDeep else TextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                patternNames[i],
                                color = if (active) AccentOrange else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                            )
                        }
                        if (i < patternNames.size - 1) Spacer(Modifier.height(3.dp))
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
