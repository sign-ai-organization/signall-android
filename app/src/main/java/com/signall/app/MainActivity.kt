package com.signall.app

import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.signall.app.data.BackendHealth
import com.signall.app.data.SharedPrefsSettingsRepository
import com.signall.app.data.SharedPrefsSignHistoryRepository
import com.signall.app.data.SignAllBackendConfig
import com.signall.app.data.SignAllWebSocketClient
import com.signall.app.data.SignAssetRepository
import com.signall.app.ui.MainScreen
import com.signall.app.ui.state.MainScreenState
import com.signall.app.ui.theme.SignAllTheme
import com.signall.app.ui.webview.WebViewSignAllController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        excludeDrawerEdgeBands(window)
        val backendConfig = SignAllBackendConfig.fromBuildConfig()
        val state =
            MainScreenState(
                history = SharedPrefsSignHistoryRepository(this),
                settingsStore = SharedPrefsSettingsRepository(this),
                webView = WebViewSignAllController(),
                backend = SignAllWebSocketClient(backendConfig),
                assets = SignAssetRepository(cacheDir, backendConfig.httpOrigin),
                health = BackendHealth(backendConfig.httpOrigin),
            )
        setContent {
            val settings = state.settings
            SystemBars(darkTheme = settings.darkTheme)
            SignAllTheme(
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

/**
 * Le pide al sistema que **no** se quede con la franja de las pestañas laterales.
 *
 * Con navegación por gestos, arrastrar desde el borde de la pantalla es el gesto
 * de "atrás" de Android: el primer toque se lo queda SystemUI y la app nunca lo
 * ve, así que los paneles laterales solo se abrían tocando la pestaña, no
 * arrastrando (owner, 2026-10-22).
 *
 * `View.setSystemGestureExclusionRects` es la vía que da Android para esto:
 * el sistema no toma esa zona como gesto de navegación. Tiene un tope de
 * **200dp por borde**, y la pestaña mide exactamente eso, así que el rect va
 * pegado al borde, con el alto de la pestaña: el centro de la pestaña está a
 * 42 % de la altura más 31dp (`EdgeDrawer`, `tabOffsetY`), o sea
 * `0.42 * alto + 31dp`.
 *
 * No es una orden: es una petición que el fabricante puede ignorar (MIUI lo hace
 * en varias versiones). Por eso el área táctil de la pestaña también creció 25 %
 * —ver `EdgeDrawer`— y queda el toque directo, que no depende de esto.
 */
private fun excludeDrawerEdgeBands(window: Window) {
    // API 29: `setSystemGestureExclusionRects` no existe antes (y antes de 29 el
    // gesto de "atrás" era el botón/la barra, no un swipe desde el borde).
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val decor = window.decorView
    decor.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        val density = view.resources.displayMetrics.density
        val height = view.height
        val width = view.width
        if (height == 0 || width == 0) return@addOnLayoutChangeListener
        val centerY = 0.42f * height + 31f * density
        val halfBand = 100f * density // 200dp en total: el tope que acepta Android.
        val bandWidth = 24f * density
        val top = (centerY - halfBand).toInt().coerceAtLeast(0)
        val bottom = (centerY + halfBand).toInt().coerceAtMost(height)
        view.systemGestureExclusionRects =
            listOf(
                Rect(0, top, bandWidth.toInt(), bottom),
                Rect(width - bandWidth.toInt(), top, width, bottom),
            )
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
