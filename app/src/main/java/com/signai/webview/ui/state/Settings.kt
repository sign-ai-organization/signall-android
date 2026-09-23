package com.signai.webview.ui.state

data class Settings(
    // Advanced / debug (spec `SET-8`).
    val debug: Boolean = false,
    val freeCam: Boolean = false,
    val grid: Boolean = true,
    val bg: Boolean = false,
    // Avatar (spec `SET-3`/`SET-4`, `PER-4`).
    val character: Character = Character.Gris,
    val speed: Int = 50, // 10..100
    // Accessibility (spec `ACC-*`).
    val interfaceSize: Int = 50, // 0..100
    val bold: Boolean = false,
    val contrast: Boolean = false,
    val large: Boolean = false,
    val reduceMotion: Boolean = false,
    // Appearance (spec `APP-*`).
    val darkTheme: Boolean = true,
)

enum class Setting {
    Debug,
    FreeCam,
    Grid,
    Background,
    Bold,
    Contrast,
    Large,
    ReduceMotion,
    DarkTheme,
}
