package com.maxixa.lemon.state

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.maxixa.lemon.LemonEngineBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ExportFormat(val label: String, val mime: String, val ext: String) {
    Wav("WAV", "audio/wav", "wav"),
    Flac("FLAC", "audio/flac", "flac"),
    Ogg("OGG", "audio/ogg", "ogg"),
    Mp3("MP3", "audio/mpeg", "mp3"),
    Midi("MIDI", "audio/midi", "mid"),
}

data class ExportResult(val success: Boolean, val message: String)

class ExportController(private val handle: Long) {
    private val tag = "Lemon"

    var isExporting by mutableStateOf(false)
        private set

    suspend fun export(fd: Int, songMode: Boolean, rate: Int, format: ExportFormat,
        bits: Int = 32,
    ): ExportResult =
        withContext(Dispatchers.IO) {
            isExporting = true
            try {
                val bytes = when (format) {
                    ExportFormat.Wav  -> LemonEngineBridge.engineExportWav(handle, fd, songMode, rate, bits) * 4L
                    ExportFormat.Flac -> LemonEngineBridge.engineExportFlac(handle, fd, songMode, rate) * 6L
                    ExportFormat.Ogg  -> LemonEngineBridge.engineExportOgg(handle, fd, songMode, rate) * 4L
                    ExportFormat.Mp3  -> LemonEngineBridge.engineExportMp3(handle, fd, songMode, rate, 192)
                    ExportFormat.Midi -> LemonEngineBridge.engineExportMidi(handle, fd, songMode)
                }
                if (bytes > 0) {
                    val msg = when (format) {
                        ExportFormat.Midi -> "Exported MIDI: $bytes bytes"
                        else -> "Exported ${format.label}: $bytes bytes"
                    }
                    Log.i(tag, "export OK: $msg")
                    ExportResult(true, msg)
                } else {
                    Log.e(tag, "export returned 0 ($format)")
                    ExportResult(false, "${format.label} export failed (check logcat)")
                }
            } catch (t: Throwable) {
                Log.e(tag, "export threw ($format)", t)
                ExportResult(false, "${format.label} threw: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                isExporting = false
            }
        }
}
