package com.signall.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.signall.app.ui.designsystem.Colors
import com.signall.app.ui.designsystem.SignAllTheme as LumoSignAllTheme

/**
 * Public theme entry point for SignAll. Delegates to the Lumo theme and exposes
 * the Lumo [Colors] palette (the single source of color truth in the app).
 *
 * Supports light/dark and the accessibility options from `Settings`
 * (font scale, UI scale, bold, high contrast, reduced motion).
 */
object SignAllTheme {
    val colors: Colors
        @Composable @ReadOnlyComposable
        get() = LumoSignAllTheme.colors

    val typography: SignAllTypography
        get() = SignAllTypography

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
        LumoSignAllTheme(
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
