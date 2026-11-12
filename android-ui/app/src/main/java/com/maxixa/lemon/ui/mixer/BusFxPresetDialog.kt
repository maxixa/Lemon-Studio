package com.maxixa.lemon.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.EngineController
import com.maxixa.lemon.state.SettingsStore
import com.maxixa.lemon.state.*
import com.maxixa.lemon.ui.theme.*

private const val SLOT_DELIM = ";;"
private const val EMPTY_SLOT = "_"

private fun encodeFxChain(engine: EngineController, track: Int): String =
    (0 until 4).map { slot ->
        val name = engine.mixerSlotEffect(track, slot)
        if (name.isEmpty()) EMPTY_SLOT else name
    }.joinToString(SLOT_DELIM)

private fun applyFxChain(engine: EngineController, track: Int, encoded: String) {
    val slots = encoded.split(SLOT_DELIM)
    for (i in 0 until 4) {
        val name = slots.getOrNull(i) ?: EMPTY_SLOT
        engine.mixerRemoveEffect(track, i)
        if (name != EMPTY_SLOT && name.isNotEmpty()) {
            engine.mixerAddEffect(track, i, name)
        }
    }
}

@Composable
fun BusFxPresetDialog(
    engine: EngineController,
    track: Int,
    trackName: String,
    settings: SettingsStore,
    onApplied: () -> Unit,
    onDismiss: () -> Unit,
) {
    var saveName by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    val presets = remember(refresh) { settings.listBusFxPresets() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = {
            Text("FX Presets · $trackName",
                color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                Text("Current chain:", color = TextMuted, fontSize = 10.sp)
                for (i in 0 until 4) {
                    val n = engine.mixerSlotEffect(track, i)
                    Text(
                        "  ${i + 1}. ${n.ifEmpty { "(empty)" }}",
                        color = if (n.isNotEmpty()) AccentCyan else TextMuted,
                        fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))

                Text("Save current as preset:", color = TextMuted, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = {
                        val name = saveName.trim()
                        if (name.isNotEmpty()) {
                            settings.setBusFxPreset(name, encodeFxChain(engine, track))
                            saveName = ""
                            refresh++
                        }
                    }) {
                        Text("Save", color = AccentOrange, fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Load preset:", color = TextMuted, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                if (presets.isEmpty()) {
                    Text("  (none saved yet)", color = TextMuted, fontSize = 10.sp)
                } else {
                    for (name in presets) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                name,
                                color = TextPrimary, fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                                    .clickable {
                                        settings.getBusFxPreset(name)?.let { chain ->
                                            applyFxChain(engine, track, chain)
                                            onApplied()
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                            )
                            Text(
                                "×",
                                color = AccentRed, fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(BgRaised, RoundedCornerShape(3.dp))
                                    .clickable {
                                        settings.deleteBusFxPreset(name)
                                        refresh++
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AccentOrange, fontSize = 13.sp)
            }
        },
    )
}
