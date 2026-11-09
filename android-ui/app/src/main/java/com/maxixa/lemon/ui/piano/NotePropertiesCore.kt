package com.maxixa.lemon.ui.piano

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*

@Composable
internal fun NoteCoreSection(
    velocity: Int,
    onVelocity: (Int) -> Unit,
    lengthTicks: Int,
    onLengthTicks: (Int) -> Unit,
    step: Int,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Velocity", color = TextPrimary, fontSize = 13.sp)
        Text("$velocity", color = AccentOrange, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
    Slider(
        value = velocity.toFloat(),
        onValueChange = { onVelocity(it.toInt().coerceIn(1, 127)) },
        valueRange = 1f..127f,
        colors = SliderDefaults.colors(
            thumbColor = AccentOrange,
            activeTrackColor = AccentOrange,
            inactiveTrackColor = BorderSubtle,
        ),
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Length", color = TextPrimary, fontSize = 13.sp)
        Text("$lengthTicks ticks", color = AccentOrange, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CoreLengthButton("−") { onLengthTicks((lengthTicks - step).coerceAtLeast(1)) }
        CoreLengthButton("÷2") { onLengthTicks((lengthTicks / 2).coerceAtLeast(1)) }
        OutlinedTextField(
            value = lengthTicks.toString(),
            onValueChange = {
                onLengthTicks(it.filter { c -> c.isDigit() }.toIntOrNull()?.coerceAtLeast(1) ?: 1)
            },
            singleLine = true,
            modifier = Modifier.weight(2f),
            textStyle = TextStyle(
                color = TextPrimary, fontSize = 13.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = BgRaised,
                unfocusedContainerColor = BgRaised,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentOrange,
            ),
        )
        CoreLengthButton("×2") { onLengthTicks((lengthTicks * 2).coerceAtMost(384 * 16)) }
        CoreLengthButton("+") { onLengthTicks((lengthTicks + step).coerceAtMost(384 * 16)) }
    }
}

@Composable
private fun CoreLengthButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(52.dp).width(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(BgRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
