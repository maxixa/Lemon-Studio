package com.maxixa.lemon.ui.record

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.maxixa.lemon.state.LocalSettings
import com.maxixa.lemon.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun RecordDialog(
    sampleRate: Int,
    bpm: Float,
    onComplete: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()

    var isRecording by remember { mutableStateOf(false) }
    var isCountingIn by remember { mutableStateOf(false) }
    var countdownBeat by remember { mutableStateOf(0) }
    var elapsedSec by remember { mutableStateOf(0) }
    var currentFile by remember { mutableStateOf<File?>(null) }
    var recorder by remember { mutableStateOf<AudioRecorder?>(null) }
    var countInBars by remember { mutableStateOf(settings.countInBars) }
    var countInJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            var t = 0
            while (isRecording) {
                elapsedSec = t
                delay(1000)
                t++
            }
        } else {
            elapsedSec = 0
        }
    }

    fun startCaptureNow() {
        val dir = ctx.getExternalFilesDir(null)?.resolve("recordings") ?: return
        dir.mkdirs()
        val f = dir.resolve("rec_${System.currentTimeMillis()}.wav")
        val r = AudioRecorder(sampleRate)
        if (r.start(f)) {
            recorder = r
            currentFile = f
            isRecording = true
        }
    }

    fun handleRecordTap() {
        when {
            isCountingIn -> {
                countInJob?.cancel(); countInJob = null
                isCountingIn = false; countdownBeat = 0
            }
            isRecording -> {
                recorder?.stop()
                isRecording = false
                currentFile?.let { onComplete(it) }
            }
            countInBars > 0 -> {
                countInJob = scope.launch {
                    isCountingIn = true
                    val beatMs = (60_000f / bpm.coerceAtLeast(20f)).toLong()
                    val totalBeats = countInBars * 4
                    val tone = try { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }
                        catch (_: Throwable) { null }
                    try {
                        for (beat in totalBeats downTo 1) {
                            countdownBeat = beat
                            val firstOfBar = (beat - 1) % 4 == 0
                            tone?.startTone(
                                if (firstOfBar) ToneGenerator.TONE_PROP_BEEP
                                else ToneGenerator.TONE_PROP_PROMPT,
                                100,
                            )
                            delay(beatMs)
                        }
                    } finally {
                        try { tone?.release() } catch (_: Throwable) {}
                        isCountingIn = false
                        countdownBeat = 0
                    }
                    startCaptureNow()
                }
            }
            else -> startCaptureNow()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isRecording && !isCountingIn) onDismiss() },
        containerColor = BgElevated,
        title = { Text("Record audio", color = TextPrimary, fontSize = 15.sp) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isRecording && !isCountingIn) {
                    Text("Count-in", color = TextMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (b in listOf(0, 1, 2, 4)) {
                            val active = countInBars == b
                            Text(
                                if (b == 0) "Off" else "${b}b",
                                color = if (active) BgDeep else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(
                                        if (active) AccentCyan else BgRaised,
                                        RoundedCornerShape(4.dp),
                                    )
                                    .clickable {
                                        countInBars = b
                                        settings.countInBars = b
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Text(
                    if (isCountingIn) countdownBeat.toString()
                    else "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60),
                    color = when {
                        isCountingIn -> AccentCyan
                        isRecording -> AccentRed
                        else -> TextPrimary
                    },
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        isCountingIn -> "Count-in… tap to cancel"
                        isRecording -> "Recording… tap to stop"
                        else -> "Tap the red button to start"
                    },
                    color = TextMuted, fontSize = 11.sp,
                )
                Spacer(Modifier.height(20.dp))

                Box(
                    Modifier.size(72.dp).clip(CircleShape)
                        .background(
                            when {
                                isCountingIn -> AccentCyan.copy(alpha = 0.7f)
                                isRecording -> AccentRed
                                else -> AccentRed.copy(alpha = 0.5f)
                            }
                        )
                        .clickable { handleRecordTap() },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (isRecording || isCountingIn) 24.dp else 44.dp)
                            .clip(
                                if (isRecording || isCountingIn) RoundedCornerShape(4.dp)
                                else CircleShape
                            )
                            .background(Color.White)
                    )
                }
            }
        },
        confirmButton = {
            if (!isRecording && !isCountingIn) {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = AccentOrange, fontSize = 13.sp)
                }
            }
        },
    )
}
