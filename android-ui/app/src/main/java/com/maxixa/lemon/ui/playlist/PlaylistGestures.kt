package com.maxixa.lemon.ui.playlist

import androidx.compose.ui.Modifier

/**
 * Playlist gesture dispatch. Picks the pencil or select state machine
 * based on `state.tool`. Each tool lives in its own file so neither
 * exceeds the readable size limit.
 */
fun Modifier.playlistGestures(
    state: PlaylistState,
    onCommit: () -> Unit,
    onLoopRangeChange: (Int, Int) -> Unit = { _, _ -> },
): Modifier = when {
    state.trimMode -> this.trimGesture(state, onCommit)
    state.tool == PlaylistTool.Pencil -> this.pencilGesture(state, onCommit, onLoopRangeChange)
    state.tool == PlaylistTool.Select -> this.selectGesture(state, onCommit, onLoopRangeChange)
    else -> this
}
