package com.maxixa.lemon.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maxixa.lemon.state.ChannelMuteBridge
import com.maxixa.lemon.ui.theme.TrackColors
import com.maxixa.lemon.state.*

private fun rowColor(i: Int, colors: List<Int>): androidx.compose.ui.graphics.Color {
    val packed = colors.getOrNull(i) ?: -1
    return if (packed >= 0) androidx.compose.ui.graphics.Color(0xFF000000.toInt() or packed)
           else TrackColors[i % TrackColors.size]
}

private val PLACEHOLDERS = listOf(
    "Kick", "Snare", "Hi-Hat", "Bass", "Lead", "Pad", "FX 1", "FX 2"
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackList(
    names: List<String>,
    handle: Long = 0L,
    peaks: List<FloatArray> = emptyList(),
    colors: List<Int> = emptyList(),
    onLongPress: (Int, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val shown = if (names.isEmpty()) PLACEHOLDERS else names
    val muted  = remember { mutableStateMapOf<Int, Boolean>() }
    val soloed = remember { mutableStateMapOf<Int, Boolean>() }

    LaunchedEffect(handle, names.size) {
        muted.clear(); soloed.clear()
        if (handle != 0L && names.isNotEmpty()) {
            for (i in names.indices) {
                val st = ChannelMuteBridge.get(handle, i)
                muted[i] = st.muted
                soloed[i] = st.soloed
            }
        }
    }

    LazyColumn(modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(shown) { i, name ->
            Box(
                Modifier.combinedClickable(
                    onClick = { },
                    onLongClick = { onLongPress(i, name) },
                )
            ) {
                TrackRow(
                    index = i, name = name,
                    peaks = peaks.getOrNull(i),
                    color = rowColor(i, colors),
                    muted = muted[i] ?: false,
                    soloed = soloed[i] ?: false,
                    onMuteToggle = {
                        val newVal = !(muted[i] ?: false)
                        muted[i] = newVal
                        if (handle != 0L) ChannelMuteBridge.setMuted(handle, i, newVal)
                    },
                    onSoloToggle = {
                        val newVal = !(soloed[i] ?: false)
                        soloed[i] = newVal
                        if (handle != 0L) ChannelMuteBridge.setSoloed(handle, i, newVal)
                    },
                )
            }
        }
    }
}
