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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.RecentProject
import com.maxixa.lemon.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecentProjectsDialog(
    projects: List<RecentProject>,
    onPick: (RecentProject) -> Unit,
    onOpenNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = { Text("Recent projects", color = TextPrimary, fontSize = 15.sp) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (projects.isEmpty()) {
                    Text("No recent projects. Load a .flp to get started.",
                        color = TextMuted, fontSize = 12.sp)
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(projects) { p ->
                            Row(
                                Modifier.fillMaxWidth().height(50.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BgRaised)
                                    .clickable { onPick(p); onDismiss() }
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name,
                                        color = TextPrimary, fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold)
                                    Text(
                                        SimpleDateFormat("MMM d, HH:mm", Locale.US)
                                            .format(Date(p.timestamp)),
                                        color = TextMuted, fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Box(
                    Modifier.fillMaxWidth().height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentOrange.copy(alpha = 0.15f))
                        .clickable { onOpenNew(); onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Open new file…",
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
