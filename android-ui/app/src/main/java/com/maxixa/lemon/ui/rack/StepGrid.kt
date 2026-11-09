package com.maxixa.lemon.ui.rack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maxixa.lemon.data.NoteView
import com.maxixa.lemon.ui.theme.TrackColors

/**
 * Grid never reads the masks map itself — only passes it to StepRow so
 * each row can subscribe to its own key. This keeps a toggle on channel i
 * from recomposing rows 0..N on all other channels.
 */
@Composable
fun StepGrid(
    trackNames: List<String>,
    masks: SnapshotStateMap<Int, List<Boolean>>,
    maskSize: Int,
    allNotes: List<NoteView>,
    ppq: Int,
    patternLength: Int,
    onToggle: (channelIdx: Int, step: Int) -> Unit,
    onRotate: (channelIdx: Int, delta: Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    val notesByChannel = remember(allNotes) { allNotes.groupBy { it.channel } }

    LazyColumn(
        Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(trackNames) { idx, name ->
            StepRow(
                index = idx,
                name = name,
                accent = TrackColors[idx % TrackColors.size],
                masks = masks,
                maskSize = maskSize,
                scrollState = scrollState,
                notes = notesByChannel[idx] ?: emptyList(),
                ppq = ppq,
                patternLength = patternLength,
                onToggle = { step -> onToggle(idx, step) },
                onRotate = { delta -> onRotate(idx, delta) },
            )
        }
    }
}
