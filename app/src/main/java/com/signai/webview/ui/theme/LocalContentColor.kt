package com.signai.webview.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// Replacement for androidx.compose.material.LocalContentColor (which lives
// in the legacy `material` artifact we don't depend on). Default to
// `textMain` because every Lumo component in dark mode treats white-ish as
// the default content color.
val LocalContentColor = compositionLocalOf { Color.Unspecified }
