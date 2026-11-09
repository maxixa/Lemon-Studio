package com.maxixa.lemon.ui.rack

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.data.NoteView
import com.maxixa.lemon.ui.theme.TextMuted
import com.maxixa.lemon.ui.theme.TextPrimary

/**
 * One channel row. Reads `masks[index]` internally so a write to
 * `masks[i]` for i != index does NOT trigger this row's recomposition.
 * All params are either stable primitives, SnapshotStateMap (stable),
 * or the row's own note list which only changes on pattern edits.
 */
@Composable
fun StepRow(
    index: Int,
    name: String,
    accent: Color,
    masks: SnapshotStateMap<Int, List<Boolean>>,
    maskSize: Int,
    scrollState: ScrollState,
    notes: List<NoteView>,
    ppq: Int,
    patternLength: Int,
    onToggle: (Int) -> Unit,
    onRotate: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val fallback = remember(maskSize) { List(maskSize) { false } }
    val mask = masks[index] ?: fallback

    Row(
        Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier.width(20.dp).fillMaxHeight().clickable { expanded = !expanded },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (expanded) "\u25be" else "\u25b8",
                color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(2.dp))
        Column(Modifier.width(72.dp)) {
            Text(
                "%02d".format(index + 1),
                color = TextMuted, fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                name,
                color = TextPrimary, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1,
            )
        }
        Spacer(Modifier.width(2.dp))

        if (expanded) {
            MiniRollCanvas(
                notes = notes,
                patternLength = patternLength,
                ppq = ppq,
                accent = accent,
                modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp),
            )
        } else {
            RotateBtn("\u2039") { onRotate(-1) }
            RotateBtn("\u203a") { onRotate(+1) }
            Spacer(Modifier.width(2.dp))
            Row(
                Modifier.horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (s in mask.indices) {
                    StepButton(
                        on = mask[s],
                        beatGroup = s % 4 == 0,
                        accent = accent,
                        onClick = { onToggle(s) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RotateBtn(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.width(20.dp).fillMaxHeight().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = TextMuted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
