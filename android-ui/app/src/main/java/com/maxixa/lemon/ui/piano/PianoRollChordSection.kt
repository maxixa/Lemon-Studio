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
import com.maxixa.lemon.ui.theme.AccentCyan
import com.maxixa.lemon.ui.theme.BgDeep

/**
 * Renders the floating chord-generator FAB (bottom-right, above the event
 * lane) and the AddChordDialog when open. Extracted from PianoRollScreen
 * to keep that file under the 150-LOC ceiling.
 *
 * Called from inside a `Box(...)` overlay so the FAB can use BoxScope.align.
 * AlertDialog renders to its own window so being inside the Box doesn't
 * affect layout.
 */
@Composable
fun BoxScope.PianoRollChordSection(
    showChord: Boolean,
    onShowChordChange: (Boolean) -> Unit,
    playheadTicks: Int,
    state: PianoRollState,
    onApplied: () -> Unit,
) {
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 12.dp, bottom = 84.dp)
            .size(48.dp)
            .clip(CircleShape)
            .background(AccentCyan.copy(alpha = 0.85f))
            .clickable { onShowChordChange(true) },
        contentAlignment = Alignment.Center,
    ) {
        Text("\u266B", color = BgDeep, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }

    if (showChord) {
        AddChordDialog(
            playheadTicks = playheadTicks,
            state = state,
            onApplied = onApplied,
            onDismiss = { onShowChordChange(false) },
        )
    }
}
