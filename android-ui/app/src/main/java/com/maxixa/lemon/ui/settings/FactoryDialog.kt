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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

@Composable
fun FactoryDialog(
    currentPath: String,
    defaultPath: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var path by remember { mutableStateOf(currentPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = { Text("Factory samples folder", color = TextPrimary, fontSize = 15.sp) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Sampler channels look here for FL Studio's factory samples. " +
                    "Put your samples in a folder the app can read, then paste its path.",
                    color = TextMuted, fontSize = 11.sp,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = TextPrimary, fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = BgRaised,
                        unfocusedContainerColor = BgRaised,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentOrange,
                    ),
                )
                if (defaultPath.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { path = defaultPath }) {
                        Text("Use app folder", color = AccentCyan, fontSize = 11.sp)
                    }
                    Text(
                        defaultPath,
                        color = TextMuted, fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(path.trim()) }) {
                Text("Save", color = AccentOrange, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted, fontSize = 13.sp)
            }
        },
    )
}
