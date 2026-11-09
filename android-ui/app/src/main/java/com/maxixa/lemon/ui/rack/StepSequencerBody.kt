package com.maxixa.lemon.ui.rack

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.data.NoteView
import com.maxixa.lemon.ui.theme.*

/**
 * Rendered body of the Step Sequencer: toolbar + (empty hint | grid).
 *
 * Extracted from StepSequencerScreen. Body takes `masks` as a
 * SnapshotStateMap (not Map) so StepRow's per-key subscription is
 * preserved — a toggle on channel i still only recomposes row i.
 * All dialog rendering lives in StepSequencerDialogs.
 */
@Composable
fun StepSequencerBody(
    trackNames: List<String>,
    masks: SnapshotStateMap<Int, List<Boolean>>,
    maskSize: Int,
    allNotes: List<NoteView>,
    ppq: Int,
    patternLength: Int,
    labels: SequencerLabels,
    onBack: () -> Unit,
    onOpenPatternPicker: () -> Unit,
    onOpenLengthPicker: () -> Unit,
    onOpenSwingPicker: () -> Unit,
    onOpenFillPicker: () -> Unit,
    onAddPattern: () -> Unit,
    onDuplicatePattern: () -> Unit,
    onRenamePattern: () -> Unit,
    onToggle: (channelIdx: Int, step: Int) -> Unit,
    onRotate: (channelIdx: Int, delta: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(BgDeep)) {
        SequencerToolbar(
            patternLabel = labels.patternLabel,
            lengthLabel = labels.lengthLabel,
            swingLabel = labels.swingLabel,
            onBack = onBack,
            onOpenPatternPicker = onOpenPatternPicker,
            onOpenLengthPicker = onOpenLengthPicker,
            onOpenSwingPicker = onOpenSwingPicker,
            onOpenFillPicker = onOpenFillPicker,
            onAddPattern = onAddPattern,
            onDuplicatePattern = onDuplicatePattern,
            onRenamePattern = onRenamePattern,
        )

        if (trackNames.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Load a project first", color = TextMuted, fontSize = 14.sp)
            }
        } else {
            StepGrid(
                trackNames = trackNames,
                masks = masks,
                maskSize = maskSize,
                allNotes = allNotes,
                ppq = ppq,
                patternLength = patternLength,
                onToggle = onToggle,
                onRotate = onRotate,
            )
        }
    }
}
