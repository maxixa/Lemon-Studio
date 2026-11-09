package com.maxixa.lemon.ui.rack

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maxixa.lemon.ui.piano.ParamSlider
import com.maxixa.lemon.ui.piano.ToggleChip

/** Every N: 2/3/4/6/8 step interval chips. */
@Composable
internal fun EveryNParams(n: Int, onN: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (v in intArrayOf(2, 3, 4, 6, 8)) {
            ToggleChip("$v", n == v) { onN(v) }
        }
    }
}

/** Euclidean: 0..steps pulses. */
@Composable
internal fun EuclideanParams(pulses: Int, maxPulses: Int, onPulses: (Int) -> Unit) {
    ParamSlider("Pulses", pulses, 0, maxPulses.coerceAtLeast(1), "$pulses / $maxPulses") { onPulses(it) }
}

/** Random: 0..100% density. */
@Composable
internal fun RandomParams(density: Int, onDensity: (Int) -> Unit) {
    ParamSlider("Density", density, 0, 100, "$density%") { onDensity(it) }
}
