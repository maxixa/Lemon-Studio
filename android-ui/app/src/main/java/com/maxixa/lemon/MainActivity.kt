package com.maxixa.lemon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.maxixa.lemon.state.LocalSettings
import com.maxixa.lemon.state.SettingsStore
import com.maxixa.lemon.ui.screen.DawScreen
import com.maxixa.lemon.ui.settings.SettingsSheet
import com.maxixa.lemon.ui.theme.BgDeep
import com.maxixa.lemon.ui.theme.LemonTheme
import com.maxixa.lemon.ui.theme.ScaledContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = SettingsStore(this)
        setContent {
            CompositionLocalProvider(LocalSettings provides settings) {
                LemonTheme {
                    var showSettings by remember { mutableStateOf(false) }
                    val engine = remember { com.maxixa.lemon.state.EngineController() }

                    // App content — scaled
                    ScaledContent(scale = settings.uiScale.floatValue) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(BgDeep)
                                .safeDrawingPadding()
                        ) {
                            DawScreen(engine = engine, onOpenSettings = { showSettings = true })
                        }
                    }

                    // Settings sheet — NOT scaled, so slider drag stays fixed
                    if (showSettings) {
                        SettingsSheet(engine = engine, onDismiss = { showSettings = false })
                    }
                }
            }
        }
    }
}
