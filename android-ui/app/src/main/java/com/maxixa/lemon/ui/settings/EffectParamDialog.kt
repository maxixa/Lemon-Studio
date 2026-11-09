package com.maxixa.lemon.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.EffectParamMeta
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.ui.theme.*

@Composable
fun EffectParamDialog(
    trackIndex: Int,
    slotIndex: Int,
    effectName: String,
    engine: EngineController,
    onDismiss: () -> Unit,
) {
    val count = remember(trackIndex, slotIndex) {
        engine.effectParamCount(trackIndex, slotIndex)
    }
    val metas = remember(trackIndex, slotIndex, count) {
        (0 until count).mapNotNull { i ->
            engine.effectParamMeta(trackIndex, slotIndex, i)
        }
    }
    val values = remember(trackIndex, slotIndex, metas) {
        androidx.compose.runtime.mutableStateMapOf<Int, Double>().apply {
            metas.forEach { m ->
                put(m.id, engine.effectParamValue(trackIndex, slotIndex, m.id))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Column {
                Text(effectName, color = TextPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold)
                Text("${metas.size} parameters",
                    color = TextMuted, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace)
            }
        },
        text = {
            if (metas.isEmpty()) {
                Text("This effect exposes no editable parameters.",
                    color = TextMuted, fontSize = 12.sp)
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(metas) { meta ->
                        ParamRow(
                            meta = meta,
                            value = values[meta.id] ?: meta.default,
                            onChange = { v ->
                                values[meta.id] = v
                                engine.effectSetParam(trackIndex, slotIndex, meta.id, v)
                            },
                            engine = engine,
                            track = trackIndex,
                            slot = slotIndex,
                        )
                    }
                }
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

@Composable
private fun ParamRow(
    meta: EffectParamMeta,
    value: Double,
    onChange: (Double) -> Unit,
    engine: EngineController,
    track: Int,
    slot: Int,
) {
    val range = (meta.max - meta.min).coerceAtLeast(1e-9)
    val normalized = ((value - meta.min) / range).coerceIn(0.0, 1.0)
    val displayText = remember(value) {
        try {
            val txt = engine.effectParamText(track, slot, meta.id, value)
            if (txt.isNotEmpty()) txt else formatValue(value, meta.unit)
        } catch (_: Throwable) { formatValue(value, meta.unit) }
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(meta.name,
                color = TextPrimary, fontSize = 12.sp,
                fontWeight = FontWeight.Medium)
            Text(displayText,
                color = AccentOrange, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = normalized.toFloat(),
            onValueChange = { n -> onChange(meta.min + n * range) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = AccentOrange,
                activeTrackColor = AccentOrange,
                inactiveTrackColor = BorderSubtle,
            ),
        )
    }
}

private fun formatValue(v: Double, unit: String): String {
    val n = if (kotlin.math.abs(v) >= 10) "%.1f".format(v) else "%.3f".format(v)
    return if (unit.isEmpty()) n else "$n $unit"
}
