package com.signai.webview.data

import android.content.Context
import com.signai.webview.domain.Character
import com.signai.webview.domain.Settings

interface SettingsRepository {
    fun load(): Settings

    fun save(settings: Settings)
}

/**
 * Persists [Settings] in the shared `signai` SharedPreferences file (same file
 * as the history, different keys).
 */
class SharedPrefsSettingsRepository(context: Context) : SettingsRepository {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): Settings {
        val defaults = Settings()
        // `KEY_DEBUG` se **ignora** a propósito: el modo debug dejó de ser una
        // preferencia (2026-11-03) y ahora es `BuildConfig.DEV_TOOLS`. Una
        // instalación vieja puede tener el booleano guardado; leerlo volvería a
        // abrir la puerta que este cambio cierra. `save()` lo borra.
        return Settings(
            freeCam = prefs.getBoolean(KEY_FREECAM, defaults.freeCam),
            grid = prefs.getBoolean(KEY_GRID, defaults.grid),
            bg = prefs.getBoolean(KEY_BG, defaults.bg),
            character =
                prefs.getString(KEY_CHARACTER, null)
                    ?.let { name -> Character.entries.firstOrNull { it.name == name } }
                    ?: defaults.character,
            speed = prefs.getInt(KEY_SPEED, defaults.speed).coerceIn(10, 100),
            interfaceSize = prefs.getInt(KEY_INTERFACE, defaults.interfaceSize).coerceIn(0, 100),
            bold = prefs.getBoolean(KEY_BOLD, defaults.bold),
            contrast = prefs.getBoolean(KEY_CONTRAST, defaults.contrast),
            large = prefs.getBoolean(KEY_LARGE, defaults.large),
            reduceMotion = prefs.getBoolean(KEY_REDUCE_MOTION, defaults.reduceMotion),
            darkTheme = prefs.getBoolean(KEY_DARK_THEME, defaults.darkTheme),
            demoSignFallback = prefs.getBoolean(KEY_DEMO_FALLBACK, defaults.demoSignFallback),
        )
    }

    override fun save(settings: Settings) {
        prefs.edit()
            // Limpia la preferencia vieja del modo debug: ya no se lee ni se
            // escribe, y dejarla guardada solo confunde a quien audite el archivo.
            .remove(KEY_DEBUG)
            .putBoolean(KEY_FREECAM, settings.freeCam)
            .putBoolean(KEY_GRID, settings.grid)
            .putBoolean(KEY_BG, settings.bg)
            .putString(KEY_CHARACTER, settings.character.name)
            .putInt(KEY_SPEED, settings.speed)
            .putInt(KEY_INTERFACE, settings.interfaceSize)
            .putBoolean(KEY_BOLD, settings.bold)
            .putBoolean(KEY_CONTRAST, settings.contrast)
            .putBoolean(KEY_LARGE, settings.large)
            .putBoolean(KEY_REDUCE_MOTION, settings.reduceMotion)
            .putBoolean(KEY_DARK_THEME, settings.darkTheme)
            .putBoolean(KEY_DEMO_FALLBACK, settings.demoSignFallback)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "signai"

        /** Preferencia vieja del interruptor «Modo debug»: solo se borra. */
        const val KEY_DEBUG = "debug"
        const val KEY_FREECAM = "freecam"
        const val KEY_GRID = "grid"
        const val KEY_BG = "bg"
        const val KEY_CHARACTER = "character"
        const val KEY_SPEED = "speed"
        const val KEY_INTERFACE = "interface_size"
        const val KEY_BOLD = "bold"
        const val KEY_CONTRAST = "contrast"
        const val KEY_LARGE = "large"
        const val KEY_REDUCE_MOTION = "reduce_motion"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_DEMO_FALLBACK = "demo_sign_fallback"
    }
}
