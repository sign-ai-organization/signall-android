package com.signai.webview.domain

/**
 * App configuration (spec `SET-*`/`ACC-*`/`APP-*`). Lives in `domain` so the
 * `data` layer can persist it without depending on `ui`.
 */
data class Settings(
    // Advanced / dev-only (spec `SET-8`). `debug` ya no vive acá: es una
    // condición de compilación (`BuildConfig.DEV_TOOLS`), no una preferencia.
    val freeCam: Boolean = false,
    val grid: Boolean = true,
    val bg: Boolean = false,
    // Avatar (spec `SET-3`/`SET-4`, `PER-4`).
    val character: Character = Character.Juan,
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
 * Desde el 2026-10-26 la selección **sí llega a la escena**: cada personaje
 * corresponde a un `.glb` completo con el muñeco y sus 50 señas adentro
 * (`models/juan.glb`, `models/pauline.glb`). [id] es el nombre seguro que viaja
 * a JavaScript; la escena lo resuelve contra un catálogo local y nunca arma una
 * URL con datos del usuario.
 *
 * Los valores viejos (`Julian`, `Samuel`, `Ximena`) se normalizan a [Juan] al
 * leer las preferencias: esas opciones nunca cambiaron el modelo real, así que
 * una instalación vieja no tiene por qué quedar en un avatar que ya no existe.
 */
enum class Character(
    val label: String,
    val colorArgb: Long,
    /** Identificador estable para el puente Kotlin → JS (`setAvatar`). */
    val id: String,
) {
    Juan("Juan", 0xFF765044, "juan"),
    Pauline("Pauline", 0xFFA46B54, "pauline"),
}
