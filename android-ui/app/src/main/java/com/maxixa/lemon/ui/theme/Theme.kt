package com.maxixa.lemon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DawDarkScheme = darkColorScheme(
    primary          = AccentOrange,
    onPrimary        = BgDeep,
    secondary        = AccentCyan,
    onSecondary      = BgDeep,
    tertiary         = AccentAmber,
    background       = BgDeep,
    onBackground     = TextPrimary,
    surface          = BgElevated,
    onSurface        = TextPrimary,
    surfaceVariant   = BgSurface,
    onSurfaceVariant = TextMuted,
    error            = AccentRed,
    onError          = BgDeep,
    outline          = BorderSubtle,
)

@Composable
fun LemonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DawDarkScheme, content = content)
}
