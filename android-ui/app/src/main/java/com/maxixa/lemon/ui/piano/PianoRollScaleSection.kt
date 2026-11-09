package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.PianoRollState
import com.maxixa.lemon.ui.theme.AccentOrange
import com.maxixa.lemon.ui.theme.BgDeep
import com.maxixa.lemon.ui.theme.BgRaised

/**
 * Renders the floating scale-lock FAB (above chord FAB, bottom-right) and
 * the ScaleLockDialog when open. FAB is orange when scale lock is enabled,
 * neutral when off — visual cue without opening the dialog.
 *
 * Called from inside a `Box(...)` overlay so the FAB can use BoxScope.align.
 */
@Composable
fun BoxScope.PianoRollScaleSection(
    showScale: Boolean,
    onShowScaleChange: (Boolean) -> Unit,
    state: PianoRollState,
    onApplied: () -> Unit,
) {
    val active = state.scaleLockEnabled
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 12.dp, bottom = 144.dp)
            .size(48.dp)
            .clip(CircleShape)
            .background(if (active) AccentOrange.copy(alpha = 0.85f) else BgRaised)
            .clickable { onShowScaleChange(true) },
        contentAlignment = Alignment.Center,
    ) {
        Text("\u266F", color = if (active) BgDeep else AccentOrange,
            fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }

    if (showScale) {
        ScaleLockDialog(
            state = state,
            onApplied = onApplied,
            onDismiss = { onShowScaleChange(false) },
        )
    }
}
