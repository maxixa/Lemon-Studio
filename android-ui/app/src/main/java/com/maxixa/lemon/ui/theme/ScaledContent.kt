package com.maxixa.lemon.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Overrides `LocalDensity` to scale all `dp` and `sp` uniformly.
 *
 * `fontScale` is left as the user's system value, so text follows their
 * accessibility setting; only the base density is scaled here. Net effect:
 * every dp and sp in the tree is multiplied by `scale`, giving a global
 * UI zoom like Android's "Display size" setting.
 */
@Composable
fun ScaledContent(scale: Float, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val scaled = Density(base.density * scale, base.fontScale)
    CompositionLocalProvider(LocalDensity provides scaled, content = content)
}
