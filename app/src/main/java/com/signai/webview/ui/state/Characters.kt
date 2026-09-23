package com.signai.webview.ui.state

import androidx.compose.ui.graphics.Color

/**
 * Avatar characters (spec `PER-*`). Selection is **UI-only** (`PER-4`): it does
 * not change the GLB yet, it just persists the user's choice.
 */
enum class Character(
    val label: String,
    val dotColor: Color,
) {
    Julian("Julián", Color(0xFF765044)),
    Samuel("Samuel", Color(0xFF303843)),
    Ximena("Ximena", Color(0xFFA46B54)),
    Gris("Gris", Color(0xFF9A9A94)),
}
