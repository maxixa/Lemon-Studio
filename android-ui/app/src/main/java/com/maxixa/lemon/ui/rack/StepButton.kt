package com.maxixa.lemon.ui.rack

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.maxixa.lemon.ui.theme.*

@Composable
fun StepButton(
    on: Boolean,
    beatGroup: Boolean,   // every 4th step = brighter border
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (on) accent else BgRaised)
            .border(
                width = 1.dp,
                color = if (beatGroup) AccentOrange.copy(alpha = 0.6f) else BorderSubtle,
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
    )
}
