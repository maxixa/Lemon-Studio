package com.maxixa.lemon.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Hides or shows the Android system bars (status + navigation). */
@Composable
fun SystemBarEffect(hide: Boolean) {
    val ctx = LocalContext.current
    SideEffect {
        val activity = ctx as? Activity ?: return@SideEffect
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
