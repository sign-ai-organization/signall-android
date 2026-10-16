package com.signai.webview.ui.designsystem

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.signai.webview.ui.designsystem.foundation.LocalReduceMotion
import com.signai.webview.ui.designsystem.foundation.ripple

object SignAiTheme {
    val colors: Colors
        @ReadOnlyComposable @Composable
        get() = LocalColors.current

    val typography: Typography
        @ReadOnlyComposable @Composable
        get() = LocalTypography.current
}

private val HighContrastLine = Color(0xFF777777)

/**
 * Whether the app is currently in the dark theme.
 *
 * It exists because one asset changes with the theme and cannot be a token: the
 * wordmark is a raster whose "Sign" half is navy, and navy disappears on the dark
 * background. Components read this instead of taking a `darkTheme` parameter, which
 * would have to be threaded down from `MainActivity` through every screen for one
 * image.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun SignAiTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    // Accessibility (`ACC-1`/`ACC-2`/`ACC-3`/`ACC-4`/`ACC-5`).
    fontScale: Float = 1f,
    uiScale: Float = 1f,
    bold: Boolean = false,
    highContrast: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val rippleIndication = ripple()
    val selectionColors = rememberTextSelectionColors(LightColors)
    val typography = provideTypography(scale = fontScale, bold = bold)
    val baseColors = if (isDarkTheme) DarkColors else LightColors
    val colors = if (highContrast) baseColors.copy(outline = HighContrastLine) else baseColors

    val density = LocalDensity.current
    val scaledDensity =
        remember(density, uiScale) {
            Density(density = density.density * uiScale, fontScale = density.fontScale)
        }

    CompositionLocalProvider(
        LocalColors provides colors,
        LocalIsDarkTheme provides isDarkTheme,
        LocalTypography provides typography,
        LocalIndication provides rippleIndication,
        LocalTextSelectionColors provides selectionColors,
        LocalContentColor provides colors.contentColorFor(colors.background),
        LocalTextStyle provides typography.body1,
        LocalDensity provides scaledDensity,
        LocalReduceMotion provides reduceMotion,
        content = content,
    )
}

@Composable
fun contentColorFor(color: Color): Color {
    return SignAiTheme.colors.contentColorFor(color)
}

@Composable
internal fun rememberTextSelectionColors(colorScheme: Colors): TextSelectionColors {
    val primaryColor = colorScheme.primary
    return remember(primaryColor) {
        TextSelectionColors(
            handleColor = primaryColor,
            backgroundColor = primaryColor.copy(alpha = TextSelectionBackgroundOpacity),
        )
    }
}

internal const val TextSelectionBackgroundOpacity = 0.4f
