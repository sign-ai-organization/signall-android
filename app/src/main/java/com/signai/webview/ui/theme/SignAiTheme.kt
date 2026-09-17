package com.signai.webview.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import com.signai.webview.ui.designsystem.SignAiTheme as LumoSignAiTheme

object SignAiTheme {
    val colors: SignAiColors
        @Composable @ReadOnlyComposable
        get() = LocalSignAiColors.current

    val typography: SignAiTypography
        get() = SignAiTypography

    @Composable
    operator fun invoke(content: @Composable () -> Unit) {
        LumoSignAiTheme(isDarkTheme = true) {
            CompositionLocalProvider(
                LocalSignAiColors provides DefaultSignAiColors,
                LocalContentColor provides DefaultSignAiColors.textMain,
            ) {
                content()
            }
        }
    }
}

