package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

@Composable
fun EventLaneSelector(current: LaneField, onChange: (LaneField) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(28.dp).background(BgRaised),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Spacer(Modifier.width(8.dp))
        for (f in LaneField.values()) {
            Box(
                Modifier.height(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (f == current) AccentCyan.copy(alpha = 0.25f) else BgElevated)
                    .clickable { onChange(f) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    f.label,
                    color = if (f == current) AccentCyan else TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
