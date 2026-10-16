package com.signai.webview

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.signai.webview.data.BackendHealth
import com.signai.webview.data.SharedPrefsSettingsRepository
import com.signai.webview.data.SharedPrefsSignHistoryRepository
import com.signai.webview.data.SignAiBackendConfig
import com.signai.webview.data.SignAiWebSocketClient
import com.signai.webview.data.SignAssetRepository
import com.signai.webview.ui.MainScreen
import com.signai.webview.ui.state.MainScreenState
import com.signai.webview.ui.theme.SignAiTheme
import com.signai.webview.ui.webview.WebViewSignAiController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val backendConfig = SignAiBackendConfig.fromBuildConfig()
        val state =
            MainScreenState(
                history = SharedPrefsSignHistoryRepository(this),
                settingsStore = SharedPrefsSettingsRepository(this),
                webView = WebViewSignAiController(),
                backend = SignAiWebSocketClient(backendConfig),
                assets = SignAssetRepository(cacheDir, backendConfig.httpOrigin),
                health = BackendHealth(backendConfig.httpOrigin),
            )
        setContent {
            val settings = state.settings
            SystemBars(darkTheme = settings.darkTheme)
            SignAiTheme(
                darkTheme = settings.darkTheme,
                fontScale = if (settings.large) 1.15f else 1f,
                uiScale = 0.9f + settings.interfaceSize / 100f * 0.2f,
                bold = settings.bold,
                highContrast = settings.contrast,
                reduceMotion = settings.reduceMotion,
            ) {
                MainScreen(state)
            }
        }
    }
}

@Composable
private fun SystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    LaunchedEffect(darkTheme) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
        // MIUI ignores the controller in some builds; mirror the legacy flag.
        @Suppress("DEPRECATION")
        view.systemUiVisibility =
            if (darkTheme) {
                view.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                view.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
    }
}
