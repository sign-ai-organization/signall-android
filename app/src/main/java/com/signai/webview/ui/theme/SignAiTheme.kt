package com.signai.webview.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.signai.webview.ui.designsystem.Colors
import com.signai.webview.ui.designsystem.SignAiTheme as LumoSignAiTheme

/**
 * Public theme entry point for SignAI. Delegates to the Lumo theme and exposes
 * the Lumo [Colors] palette (the single source of color truth in the app).
 *
 * Always dark for now.
 */
object SignAiTheme {
    val colors: Colors
        @Composable @ReadOnlyComposable
        get() = LumoSignAiTheme.colors

    val typography: SignAiTypography
        get() = SignAiTypography

    @Composable
    operator fun invoke(content: @Composable () -> Unit) {
        LumoSignAiTheme(isDarkTheme = true) {
            content()
        }
    }
}
