package com.maxixa.lemon.ui.playlist

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
import com.maxixa.lemon.ui.icon.DeleteIcon
import com.maxixa.lemon.ui.icon.PencilIcon

/**
 * Arrangement picker. Lists existing arrangements (num + name),
 * highlights the current, offers "+ New arrangement" at bottom.
 *
 * `arrangements` is a list of `(number, name)` pairs.
 */
@Composable
fun ArrangementPickerDialog(
    arrangements: List<Pair<Int, String>>,
    currentNum: Int,
    onPick: (num: Int) -> Unit,
    onCreate: () -> Unit,
    onRename: (num: Int, name: String) -> Unit,
    onDelete: (num: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = { Text("Arrangements", color = TextPrimary, fontSize = 15.sp) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (arrangements.isEmpty()) {
                    Text("No arrangements", color = TextMuted, fontSize = 13.sp)
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(arrangements.size) { i ->
                            val (num, name) = arrangements[i]
                            val active = num == currentNum
                            Row(
                                Modifier.fillMaxWidth().height(44.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (active) AccentCyan.copy(alpha = 0.2f) else BgRaised)
                                    .clickable { onPick(num); onDismiss() }
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                                        .background(if (active) AccentCyan else BorderSubtle),
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
                                    name,
                                    modifier = Modifier.weight(1f),
                                    color = if (active) AccentCyan else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                )
                                Box(
                                    Modifier.size(28.dp).clickable { onRename(num, name) },
                                    contentAlignment = Alignment.Center,
                                ) { PencilIcon(TextMuted, 14.dp) }
                                Box(
                                    Modifier.size(28.dp).clickable {
                                        if (arrangements.size > 1) onDelete(num)
                                    },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    DeleteIcon(
                                        if (arrangements.size > 1) TextMuted else TextMuted.copy(alpha = 0.3f),
                                        14.dp,
                                    )
                                }
                            }
                            if (i < arrangements.size - 1) Spacer(Modifier.height(3.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentOrange.copy(alpha = 0.15f))
                        .clickable(onClick = onCreate),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+ New arrangement",
                        color = AccentOrange, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold)
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
