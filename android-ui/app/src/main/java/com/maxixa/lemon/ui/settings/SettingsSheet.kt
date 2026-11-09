package com.maxixa.lemon.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxixa.lemon.state.LocalSettings
import com.maxixa.lemon.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val settings = LocalSettings.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        contentColor = TextPrimary,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("Settings", color = TextPrimary, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("UI Scale", color = TextPrimary, fontSize = 14.sp)
                Text(
                    "${(settings.uiScale.floatValue * 100).toInt()}%",
                    color = AccentOrange, fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Slider(
                value = settings.uiScale.floatValue,
                onValueChange = { settings.setUiScale(it) },
                valueRange = 0.7f..1.5f,
                colors = SliderDefaults.colors(
                    thumbColor = AccentOrange,
                    activeTrackColor = AccentOrange,
                    inactiveTrackColor = BorderSubtle,
                ),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
