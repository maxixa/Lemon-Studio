package com.maxixa.lemon.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

@Composable
fun PatternRenameDialog(
    patternNum: Int,
    currentName: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = { Text("Rename pattern $patternNum", color = TextPrimary, fontSize = 15.sp) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Leave blank to use default name.",
                    color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
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
