package com.signai.webview.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class SignAiColors(
    val bg: Color,
    val bar: Color,
    val border: Color,
    val input: Color,
    val inputBorder: Color,
    val textMain: Color,
    val primary: Color,
)

val DefaultSignAiColors = SignAiColors(
    bg = Color(0xFF000000),
    bar = Color(0xFF0A0A0B),
    border = Color(0xFF1C1C1E),
    input = Color(0xFF111114),
    inputBorder = Color(0xFF2A2A2E),
    textMain = Color(0xFFEDEDED),
    primary = Color(0xFF2563EB),
)

val LocalSignAiColors = staticCompositionLocalOf { DefaultSignAiColors }
