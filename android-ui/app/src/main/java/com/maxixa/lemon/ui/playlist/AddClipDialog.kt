package com.maxixa.lemon.ui.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

@Composable
fun AddClipDialog(
    patternNames: List<String>,
    onPick: (patternNumber: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = { Text("Insert clip", color = TextPrimary, fontSize = 14.sp) },
        text = {
            if (patternNames.isEmpty()) {
                Text("No patterns in project", color = TextMuted, fontSize = 13.sp)
            } else {
                LazyColumn {
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
