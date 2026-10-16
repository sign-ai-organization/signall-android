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
    val character: Character = Character.Julian,
    val speed: Int = 50, // 10..100
    // Accessibility (spec `ACC-*`).
    val interfaceSize: Int = 50, // 0..100
    val bold: Boolean = false,
    val contrast: Boolean = false,
    val large: Boolean = false,
    val reduceMotion: Boolean = false,
    // Appearance (spec `APP-*`). The prototype is light-first and light still has every
    // color it had, but the SignALL brand (2026-10-16) lives in the dark palette, so
    // the app now *starts* dark: the first screen after installing is the new one.
    // The switch is persisted, so an install that already chose light stays light.
    val darkTheme: Boolean = true,
    // Development crutch, off by default (2026-10-16). The backend's `catalog` table is
    // still empty, so every gloss comes back unknown. With this on, such a gloss plays
    // the single sign baked into the APK instead of being skipped, which is what makes
    // the multi-sign queue visible before the real animations exist. It used to be on
    // by default, which made a normal run show Seña 0 over and over without anyone
    // asking for it: it belongs in AVANZADO, and off, until the catalog is seeded.
    val demoSignFallback: Boolean = false,
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
    DemoSignFallback,
}

/**
 * Avatar characters (spec `PER-*`). Pure domain: the color is stored as ARGB
 * and converted to a Compose `Color` at the call site.
 *
 * No existe una opción "gris". `Character` nunca llega a la escena 3D — el AvatarWebView
 * siempre carga `models/avatar.glb` — así que las opciones son los nombres del casting
 * y el punto de color del desplegable. Se ofrecía un "Gris" que no correspondía a
 * nada (el muñeco que se ve es Julián) y además era el default, con lo cual la primera
 * pantalla decía algo falso (owner, 2026-10-16). El default ahora es Julián, que es el
 * avatar que realmente se muestra; una instalación que tuviera guardado "Gris" cae al
 * default por el `firstOrNull` del repositorio.
 */
enum class Character(
    val label: String,
    val colorArgb: Long,
) {
    Julian("Julián", 0xFF765044),
    Samuel("Samuel", 0xFF303843),
    Ximena("Ximena", 0xFFA46B54),
}
