package com.maxixa.lemon.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.ui.theme.*
import kotlin.math.sin

@Composable
fun TrackRow(
    index: Int, name: String, color: Color,
    peaks: FloatArray?,
    muted: Boolean, soloed: Boolean,
    onMuteToggle: () -> Unit, onSoloToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(72.dp).background(BgSurface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight()
            .clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Text("%02d".format(index + 1), color = TextMuted,
            fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = TextPrimary, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                val w = size.width; val h = size.height
                val mid = h / 2f
                if (peaks != null && peaks.isNotEmpty()) {
                    val barW = w / peaks.size
                    for (i in peaks.indices) {
                        val amp = peaks[i].coerceIn(0f, 1f)
                        if (amp < 0.005f) continue
                        val barH = amp * h * 0.9f
                        drawRect(
                            color = color.copy(alpha = 0.85f),
                            topLeft = Offset(i * barW, mid - barH / 2f),
                            size = Size(barW.coerceAtLeast(1f), barH),
                        )
                    }
                    drawLine(color.copy(alpha = 0.25f),
                        Offset(0f, mid), Offset(w, mid), 1f)
                } else {
                    val path = Path()
                    val steps = 60
                    for (i in 0..steps) {
                        val x = (i.toFloat() / steps) * w
                        val amp = (sin(i * 0.4f + index) * 0.5f + 0.5f) * 0.4f
                        val y = mid + sin(i * 1.3f) * amp * h
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color.copy(alpha = 0.75f),
                        style = Stroke(width = 1.5f))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        MiniToggle("M", muted, AccentAmber, onMuteToggle)
        Spacer(Modifier.width(4.dp))
        MiniToggle("S", soloed, AccentCyan, onSoloToggle)
        Spacer(Modifier.width(8.dp))
        LevelMeter(color)
    }
}

@Composable
private fun MiniToggle(label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))
            .background(if (active) activeColor else BgRaised)
            .border(1.dp, if (active) activeColor else BorderSubtle,
                RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) BgDeep else TextMuted,
            fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LevelMeter(color: Color) {
    Canvas(Modifier.width(4.dp).height(48.dp)) {
        val w = size.width; val h = size.height
        drawRoundRect(color = BgDeep, topLeft = Offset.Zero,
            size = Size(w, h), cornerRadius = CornerRadius(w / 2f))
        val level = 0.6f
        drawRoundRect(color = color,
            topLeft = Offset(0f, h * (1f - level)),
            size = Size(w, h * level),
            cornerRadius = CornerRadius(w / 2f))
    }
}
