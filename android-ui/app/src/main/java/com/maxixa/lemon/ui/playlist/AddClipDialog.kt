package com.maxixa.lemon.ui.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

@Composable
fun AddClipDialog(
    patternNames: List<String>,
    onPick: (patternNumber: Int) -> Unit,
    onPickAudio: () -> Unit,
    onPickAutomation: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = { Text("Insert clip", color = TextPrimary, fontSize = 14.sp) },
        text = {
            LazyColumn {
                item { SourceRow("\u266a", "Audio Clip\u2026", AccentAmber, onPickAudio) }
                item { SourceRow("\u2699", "Automation Clip\u2026", AccentCyan, onPickAutomation) }
                item { Spacer(Modifier.height(8.dp)) }
                if (patternNames.isEmpty()) {
                    item {
                        Text("No patterns in project", color = TextMuted, fontSize = 13.sp)
                    }
                } else {
                    item {
                        Text(
                            "Patterns",
                            color = TextMuted, fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        )
                    }
                    items(patternNames.size) { i ->
                        Text(
                            patternNames[i],
                            color = TextPrimary, fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onPick(i + 1) }
                                .padding(vertical = 10.dp),
                        )
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
private fun SourceRow(
    icon: String,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Text(label, color = TextPrimary, fontSize = 13.sp)
    }
}
