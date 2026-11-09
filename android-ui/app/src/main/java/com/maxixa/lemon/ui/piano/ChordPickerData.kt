package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.AccentCyan
import com.maxixa.lemon.ui.theme.BgRaised
import com.maxixa.lemon.ui.theme.TextMuted

/**
 * Static data + micro-composables for the chord generator dialog.
 * Extracted from AddChordDialog to keep that file under 150 LOC.
 *
 * Intervals match NoteGroove's chord detector exactly so detect→generate
 * round-trips. Duplicated inline (NoteGroove.TEMPLATES is private and its
 * removal would regress GrooveDialog).
 */

internal val CHORD_NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

internal val CHORD_INTERVALS: Map<String, IntArray> = mapOf(
    "maj" to intArrayOf(0, 4, 7),
    "m" to intArrayOf(0, 3, 7),
    "dim" to intArrayOf(0, 3, 6),
    "aug" to intArrayOf(0, 4, 8),
    "sus2" to intArrayOf(0, 2, 7),
    "sus4" to intArrayOf(0, 5, 7),
    "7" to intArrayOf(0, 4, 7, 10),
    "maj7" to intArrayOf(0, 4, 7, 11),
    "m7" to intArrayOf(0, 3, 7, 10),
    "dim7" to intArrayOf(0, 3, 6, 9),
    "6" to intArrayOf(0, 4, 7, 9),
    "m6" to intArrayOf(0, 3, 7, 9),
    "add9" to intArrayOf(0, 2, 4, 7),
)

internal val QUALITY_ORDER = listOf(
    "maj", "m", "dim", "aug", "sus2", "sus4", "7",
    "maj7", "m7", "dim7", "6", "m6", "add9",
)

internal val OCTAVE_OPTIONS = intArrayOf(2, 3, 4, 5, 6)

internal fun noteLabel(k: Int): String {
    if (k < 0 || k > 127) return "?"
    return "${CHORD_NOTE_NAMES[k % 12]}${k / 12}"
}

@Composable
internal fun MiniChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.height(30.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (active) AccentCyan.copy(alpha = 0.25f) else BgRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label,
            color = if (active) AccentCyan else TextMuted,
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1)
    }
}
