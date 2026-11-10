package com.maxixa.lemon.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.ui.theme.*
import com.maxixa.lemon.state.*

@Composable
fun SampleEditorDialog(
    channelIndex: Int,
    channelName: String,
    peaks: FloatArray,
    engine: EngineController,
    onDismiss: () -> Unit,
) {
    val totalFrames = remember { engine.channelSampleFrames(channelIndex).toFloat().coerceAtLeast(1f) }
    val hasSample = peaks.isNotEmpty() && totalFrames > 1f

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Column {
                Text(channelName, color = TextPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    if (hasSample)
                        "%.2f s · %d frames".format(totalFrames / 44100f, totalFrames.toLong())
                    else "No sample loaded",
                    color = TextMuted, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                if (hasSample) {
                    WaveformLoopSection(
                        channelIndex = channelIndex,
                        peaks = peaks,
                        engine = engine,
                        totalFrames = totalFrames,
                    )
                    Spacer(Modifier.height(14.dp))
                } else {
                    Text("Load a sample on this channel first.",
                        color = TextMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(14.dp))
                SampleFxSection(channelIndex = channelIndex, engine = engine)
                Spacer(Modifier.height(14.dp))
                PitchStretchSection(channelIndex = channelIndex, engine = engine)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = AccentOrange,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}
