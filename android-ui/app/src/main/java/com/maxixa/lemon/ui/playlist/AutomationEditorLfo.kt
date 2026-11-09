package com.maxixa.lemon.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.EngineController.AutomationLfo
import com.maxixa.lemon.ui.theme.*
import kotlin.math.ln
import kotlin.math.pow

private const val SPEED_DEFAULT = 45819
private const val SPEED_MIN = 0
private const val SPEED_MAX = 131071

/** Convert speed i32 → cycles/beat (matches os-model ClipLfo::cycles_per_beat). */
private fun cyclesPerBeat(speed: Int): Double =
    (10001.0.pow(speed.toDouble() / 65536.0) - 1.0) / 625.0

/** Default speed's rate — 1.0x reference. */
private val BASE_CYCLES: Double = cyclesPerBeat(SPEED_DEFAULT)

/** speed → multiplier (1.0 = default). */
private fun multiplierFromSpeed(speed: Int): Float {
    if (BASE_CYCLES <= 0.0) return 1f
    return (cyclesPerBeat(speed) / BASE_CYCLES).toFloat()
}

/** multiplier → speed (inverse of multiplierFromSpeed, clamped to range). */
private fun speedForMultiplier(mult: Float): Int {
    val m = mult.coerceAtLeast(0.01f).toDouble()
    val target = m * BASE_CYCLES
    val logArg = 1.0 + 625.0 * target
    if (logArg <= 1.0) return 0
    val raw = 65536.0 * ln(logArg) / ln(10001.0)
    return raw.toInt().coerceIn(SPEED_MIN, SPEED_MAX)
}

@Composable
internal fun AutomationEditorLfo(
    initial: AutomationLfo,
    onLfoChange: (AutomationLfo) -> Unit,
) {
    var on by remember { mutableStateOf(initial.isOn) }
    var amount by remember { mutableStateOf(initial.amount.coerceIn(0, 128)) }
    var speedMult by remember {
        mutableStateOf(multiplierFromSpeed(initial.speed).coerceIn(0.25f, 4f))
    }

    fun emit() {
        val newSpeed = speedForMultiplier(speedMult)
        onLfoChange(
            AutomationLfo(
                switch = if (on) -1 else 0,
                amount = amount,
                speed = newSpeed,
                mode = initial.mode,
            )
        )
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("LFO", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Box(
                Modifier.height(30.dp).width(64.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (on) AccentCyan.copy(alpha = 0.25f) else BgRaised)
                    .clickable { on = !on; emit() },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (on) "On" else "Off",
                    color = if (on) AccentCyan else TextMuted,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(14.dp))

        if (!on) {
            Text("Enable LFO to modulate the automation curve with a periodic wave.",
                color = TextMuted, fontSize = 11.sp)
            return@Column
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Depth", color = TextPrimary, fontSize = 12.sp)
            Text("${amount * 100 / 128}%", color = AccentCyan, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = amount.toFloat(),
            onValueChange = { amount = it.toInt().coerceIn(0, 128); emit() },
            valueRange = 0f..128f,
            colors = SliderDefaults.colors(
                thumbColor = AccentCyan, activeTrackColor = AccentCyan,
                inactiveTrackColor = BorderSubtle,
            ),
        )

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Speed", color = TextPrimary, fontSize = 12.sp)
            Text("%.2f×".format(speedMult), color = AccentCyan, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = speedMult,
            onValueChange = { speedMult = it.coerceIn(0.25f, 4f); emit() },
            valueRange = 0.25f..4f,
            colors = SliderDefaults.colors(
                thumbColor = AccentOrange, activeTrackColor = AccentOrange,
                inactiveTrackColor = BorderSubtle,
            ),
        )
        Text("0.25× = very slow · 1× = one cycle/beat · 4× = fast wobble",
            color = TextMuted, fontSize = 10.sp)
    }
}
