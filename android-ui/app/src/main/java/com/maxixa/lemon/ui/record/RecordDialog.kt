package com.maxixa.lemon.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.AudioRecorder
import com.maxixa.lemon.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun RecordDialog(
    sampleRate: Int,
    onComplete: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableStateOf(0) }
    var currentFile by remember { mutableStateOf<File?>(null) }
    var recorder by remember { mutableStateOf<AudioRecorder?>(null) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            var t = 0
            while (isRecording) {
                elapsedSec = t
                delay(1000)
                t++
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isRecording) onDismiss() },
        containerColor = BgElevated,
        title = { Text("Record audio", color = TextPrimary, fontSize = 15.sp) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60),
                    color = if (isRecording) AccentRed else TextPrimary,
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isRecording) "Recording… tap to stop"
                    else "Tap the red button to start",
                    color = TextMuted, fontSize = 11.sp,
                )
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier.size(72.dp).clip(CircleShape)
                        .background(if (isRecording) AccentRed else AccentRed.copy(alpha = 0.5f))
                        .clickable {
                            if (!isRecording) {
                                val dir = ctx.getExternalFilesDir(null)?.resolve("recordings")
                                    ?: return@clickable
                                dir.mkdirs()
                                val f = dir.resolve("rec_${System.currentTimeMillis()}.wav")
                                val r = AudioRecorder(sampleRate)
                                if (r.start(f)) {
                                    recorder = r
                                    currentFile = f
                                    isRecording = true
                                }
                            } else {
                                recorder?.stop()
                                isRecording = false
                                currentFile?.let { onComplete(it) }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (isRecording) 24.dp else 44.dp)
                            .clip(if (isRecording) androidx.compose.foundation.shape.RoundedCornerShape(4.dp) else CircleShape)
                            .background(Color.White)
                    )
                }
            }
        },
        confirmButton = {
            if (!isRecording) {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = AccentOrange, fontSize = 13.sp)
                }
            }
        },
    )
}
