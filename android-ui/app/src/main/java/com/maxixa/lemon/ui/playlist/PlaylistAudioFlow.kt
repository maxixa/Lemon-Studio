package com.maxixa.lemon.ui.playlist

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.maxixa.lemon.state.EngineController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.maxixa.lemon.state.*

private const val TAG = "Lemon"

/**
 * Pick → copy → add channel → compute length → place clip → commit.
 * Returns a `launch()` lambda for the toolbar's "Add audio clip" button.
 *
 * Self-contained; uses its own SAF launcher and file-copy so it doesn't
 * conflict with the existing sample-picker flow that forces ChannelKind::Sampler.
 */
@Composable
fun rememberAudioClipFlow(
    engine: EngineController,
    state: PlaylistState,
    onCommit: () -> Unit,
): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) { copyToAppStorage(ctx, uri) }
            if (path.isNullOrEmpty()) {
                Log.e(TAG, "audio clip: file copy failed")
                return@launch
            }
            val idx = engine.addAudioClipChannel(path)
            if (idx < 0) {
                Log.e(TAG, "audio clip: channel add failed")
                return@launch
            }
            val frames = engine.channelSampleFrames(idx)
            // Sample duration in ticks: frames / sampleRate = seconds;
            // seconds * (bpm / 60) = beats; beats * ppq = ticks.
            // = frames * bpm * ppq / (sampleRate * 60)
            val length = if (frames > 0 && engine.sampleRate > 0 && engine.bpm > 0f) {
                (frames.toDouble() * engine.bpm.toDouble() * state.ppq /
                    (engine.sampleRate.toDouble() * 60.0))
                    .toInt()
                    .coerceAtLeast(48)  // 1/32 tick minimum
            } else {
                state.barTicks()
            }
            val track = state.scrollY
            val position = state.scrollX.toInt()
            state.addChannelClip(track, position, length, idx)
            onCommit()
            Log.i(TAG, "audio clip placed: ch=$idx len=$length track=$track pos=$position")
        }
    }
    return {
        Log.d(TAG, "audio clip: launching picker (audio/* + */*)")
        launcher.launch(arrayOf("audio/*", "*/*"))
    }
}

private fun copyToAppStorage(ctx: Context, uri: Uri): String? {
    return try {
        val ext = guessExtension(ctx.contentResolver.getType(uri))
        val file = File(ctx.filesDir, "audio_clip_${System.currentTimeMillis()}$ext")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        } ?: return null
        file.absolutePath
    } catch (t: Throwable) {
        Log.e(TAG, "copyToAppStorage failed", t)
        null
    }
}

private fun guessExtension(mime: String?): String = when {
    mime == null -> ".audio"
    mime.contains("wav", true) || mime.contains("wave", true) -> ".wav"
    mime.contains("mpeg", true) || mime.contains("mp3", true) -> ".mp3"
    mime.contains("flac", true) -> ".flac"
    mime.contains("ogg", true) -> ".ogg"
    mime.contains("m4a", true) || mime.contains("mp4", true) -> ".m4a"
    else -> ".audio"
}
