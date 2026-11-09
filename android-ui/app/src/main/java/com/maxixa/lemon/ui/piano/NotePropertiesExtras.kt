package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

@Composable
internal fun NoteExtrasSection(
    pan: Int, onPan: (Int) -> Unit,
    finePitch: Int, onFinePitch: (Int) -> Unit,
    release: Int, onRelease: (Int) -> Unit,
    modX: Int, onModX: (Int) -> Unit,
    modY: Int, onModY: (Int) -> Unit,
    repeat: Int, onRepeat: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        ExtraRow("Pan",     pan,       0, 128, center = 64) { onPan(it) }
        ExtraRow("Fine",    finePitch, 0, 255, center = 120) { onFinePitch(it) }
        ExtraRow("Release", release,   0, 255, center = -1) { onRelease(it) }
        ExtraRow("Mod X",   modX,      0, 255, center = -1) { onModX(it) }
        ExtraRow("Mod Y",   modY,      0, 255, center = -1) { onModY(it) }
        ExtraRow("Repeat",  repeat,    0, 14,  center = -1) { onRepeat(it) }
    }
}

@Composable
private fun ExtraRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    center: Int,
    onChange: (Int) -> Unit,
) {
    val display = if (center >= 0 && value == center) "C" else "$value"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextPrimary, fontSize = 12.sp)
        Text(display, color = AccentOrange, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.toInt().coerceIn(min, max)) },
        valueRange = min.toFloat()..max.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = AccentCyan,
            activeTrackColor = AccentCyan,
            inactiveTrackColor = BorderSubtle,
        ),
    )
}
