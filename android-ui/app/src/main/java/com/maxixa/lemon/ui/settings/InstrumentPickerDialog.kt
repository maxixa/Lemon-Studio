package com.maxixa.lemon.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
fun InstrumentPickerDialog(
    channelIndex: Int,
    currentId: String,
    catalog: List<Pair<String, String>>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = { Text("Instrument — channel ${channelIndex + 1}", color = TextPrimary, fontSize = 15.sp) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Pick a built-in synth to play this channel's notes.",
                    color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(catalog) { (id, name) ->
                        val active = id == currentId
                        Row(
                            Modifier.fillMaxWidth().height(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) AccentOrange.copy(alpha = 0.2f) else BgRaised)
                                .clickable { onPick(id); onDismiss() }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name,
                                color = if (active) AccentOrange else TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                        }
                        Spacer(Modifier.height(4.dp))
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
