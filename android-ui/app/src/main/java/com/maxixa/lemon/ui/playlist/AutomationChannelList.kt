package com.maxixa.lemon.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

/**
 * Channel picker list for AddAutomationDialog. Extracted to keep that
 * dialog under the 150-LOC ceiling.
 */
@Composable
internal fun AutomationChannelList(
    channelLabels: List<String>,
    selectedIdx: Int,
    onSelect: (Int) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
        items(channelLabels.size) { i ->
            val active = i == selectedIdx
            Row(
                Modifier.fillMaxWidth().height(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (active) AccentCyan.copy(alpha = 0.2f) else BgRaised)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${i + 1}  ${channelLabels[i]}",
                    color = if (active) AccentCyan else TextPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            if (i < channelLabels.size - 1) Spacer(Modifier.height(2.dp))
        }
    }
}
