package com.signai.webview.domain

/**
 * App configuration (spec `SET-*`/`ACC-*`/`APP-*`). Lives in `domain` so the
 * `data` layer can persist it without depending on `ui`.
 */
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

/**
 * Avatar characters (spec `PER-*`). Pure domain: the color is stored as ARGB
 * and converted to a Compose `Color` at the call site.
 */
enum class Character(
    val label: String,
    val colorArgb: Long,
) {
    Julian("Julián", 0xFF765044),
    Samuel("Samuel", 0xFF303843),
    Ximena("Ximena", 0xFFA46B54),
    Gris("Gris", 0xFF9A9A94),
}
