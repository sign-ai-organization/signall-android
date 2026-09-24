package com.signai.webview.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.signai.webview.ui.designsystem.Colors
import com.signai.webview.ui.designsystem.SignAiTheme as LumoSignAiTheme

/**
 * Public theme entry point for SignAI. Delegates to the Lumo theme and exposes
 * the Lumo [Colors] palette (the single source of color truth in the app).
 *
 * Supports light/dark and the accessibility options from `Settings`
 * (font scale, UI scale, bold, high contrast, reduced motion).
 */
object SignAiTheme {
    val colors: Colors
        @Composable @ReadOnlyComposable
        get() = LumoSignAiTheme.colors

    val typography: SignAiTypography
        get() = SignAiTypography

    @Composable
    operator fun invoke(
        darkTheme: Boolean = true,
        fontScale: Float = 1f,
        uiScale: Float = 1f,
        bold: Boolean = false,
        highContrast: Boolean = false,
        reduceMotion: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        LumoSignAiTheme(
            isDarkTheme = darkTheme,
            fontScale = fontScale,
            uiScale = uiScale,
            bold = bold,
            highContrast = highContrast,
            reduceMotion = reduceMotion,
            content = content,
        )
    }
}
