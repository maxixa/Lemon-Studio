package com.maxixa.lemon.ui.export

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.ExportFormat
import com.maxixa.lemon.ui.theme.*
import com.maxixa.lemon.state.*

@Composable
fun ExportDialog(
    onConfirm: (songMode: Boolean, rate: Int, format: ExportFormat, withStems: Boolean, wavBits: Int, mp3Kbps: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { com.maxixa.lemon.state.SettingsStore(ctx.applicationContext) }

    var songMode by remember { mutableStateOf(settings.exportSongMode) }
    var rate by remember { mutableStateOf(settings.exportRate) }
    var format by remember { mutableStateOf(
        try { ExportFormat.valueOf(settings.exportFormatName) }
        catch (_: Throwable) { ExportFormat.Wav }
    ) }
    var wavBits by remember { mutableStateOf(settings.exportWavBits) }
    var mp3Kbps by remember { mutableStateOf(settings.exportMp3Kbps) }
    var withStems by remember { mutableStateOf(settings.exportWithStems) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = { Text("Export audio", color = TextPrimary, fontSize = 15.sp) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Source", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Toggle("Song", songMode) { songMode = true }
                    Toggle("Pattern", !songMode) { songMode = false }
                }
                Spacer(Modifier.height(12.dp))
                Text("Format", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (f in ExportFormat.values()) {
                        val enabled = !(withStems && f != ExportFormat.Wav)
                        Toggle(f.label, active = format == f, enabled = enabled) {
                            if (enabled) format = f
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (format == ExportFormat.Wav) {
                    Text("WAV Bit Depth", color = TextMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Toggle("16-bit", wavBits == 16) { wavBits = 16 }
                        Toggle("24-bit", wavBits == 24) { wavBits = 24 }
                        Toggle("32f",    wavBits == 32) { wavBits = 32 }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (format == ExportFormat.Mp3) {
                    Text("MP3 Bitrate", color = TextMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Toggle("128", mp3Kbps == 128) { mp3Kbps = 128 }
                        Toggle("192", mp3Kbps == 192) { mp3Kbps = 192 }
                        Toggle("320", mp3Kbps == 320) { mp3Kbps = 320 }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Text("Sample rate", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in listOf(44100, 48000, 96000)) {
                        val label = if (r % 1000 == 0) "${r/1000} kHz"
                                    else "%.1f kHz".format(r/1000.0)
                        Toggle(label, rate == r) { rate = r }
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Stems checkbox
                Row(
                    Modifier.fillMaxWidth().height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (withStems) AccentOrange.copy(alpha = 0.15f) else BgRaised)
                        .clickable { withStems = !withStems }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.Canvas(
                        Modifier.size(20.dp)
                    ) {
                        drawRoundRect(
                            color = if (withStems) AccentOrange else BorderSubtle,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
                        )
                        if (withStems) {
                            drawLine(
                                color = BgDeep,
                                start = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.5f),
                                end = androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.7f),
                                strokeWidth = 3f,
                            )
                            drawLine(
                                color = BgDeep,
                                start = androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.7f),
                                end = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.28f),
                                strokeWidth = 3f,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Also export stems",
                            color = TextPrimary, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text(
                            if (withStems) "One WAV per mixer track + mix (pick folder)"
                            else "Off (pick a single file)",
                            color = TextMuted, fontSize = 9.sp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    when (format) {
                        ExportFormat.Wav  -> if (wavBits == 32) "32-bit float stereo WAV" else "${wavBits}-bit stereo WAV"
                        ExportFormat.Flac -> "24-bit FLAC (lossless, ~50% smaller)"
ExportFormat.Ogg  -> "Vorbis OGG (lossy, smaller than FLAC)"
                        ExportFormat.Mp3  -> "${mp3Kbps} kbps MP3 (lossy, smallest)"
                        ExportFormat.Midi -> "Standard MIDI (.mid) — notes only"
                    },
                    color = TextMuted, fontSize = 10.sp,
                )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "≈ " + estimateSizePerMin(rate, format, wavBits)
                            + if (withStems) " × (1 + tracks)" else "",
                        color = TextMuted.copy(alpha = 0.7f), fontSize = 9.sp,
                    )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.exportSongMode = songMode
                settings.exportRate = rate
                settings.exportFormatName = format.name
                settings.exportWavBits = wavBits
                settings.exportWithStems = withStems
                settings.exportMp3Kbps = mp3Kbps
                onConfirm(songMode, rate, format, withStems, wavBits, mp3Kbps)
            }) {
                Text("Export…", color = AccentOrange,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted, fontSize = 13.sp)
            }
        },
    )
}

@Composable
private fun Toggle(
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier.height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    !enabled -> BgRaised.copy(alpha = 0.4f)
                    active -> AccentOrange
                    else -> BgRaised
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label,
            color = when {
                !enabled -> TextMuted.copy(alpha = 0.4f)
                active -> BgDeep
                else -> TextPrimary
            },
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Approximate size per minute of audio for the given export configuration.
 * Rounded for UI display — actual file size depends on content (VBR formats)
 * and stem count. Informative, not exact.
 */
private fun estimateSizePerMin(rate: Int, format: ExportFormat, wavBits: Int): String {
    val mbPerMin: Double = when (format) {
        ExportFormat.Wav -> {
            val bytesPerSample = when (wavBits) { 16 -> 2; 24 -> 3; else -> 4 }
            rate.toLong() * 60L * 2L * bytesPerSample / 1_048_576.0
        }
        ExportFormat.Flac -> rate.toLong() * 60L * 2L * 3L / 1_048_576.0 * 0.5
        ExportFormat.Ogg  -> 1.5
        ExportFormat.Mp3  -> 1.44
        ExportFormat.Midi -> 0.002
    }
    return if (mbPerMin < 0.01) "<1 KB / min" else "%.1f MB / min".format(mbPerMin)
}

