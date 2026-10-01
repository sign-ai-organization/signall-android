package com.signai.webview.ui.state

/**
 * Playback state machine of the translation flow (spec `STA-*`).
 *
 * `idle → translating → signing → done`, plus the mic placeholder
 * `listening`/`processing` and the two connection states added by the backend
 * integration (plan paso 1).
 */
enum class PlaybackState(val label: String) {
    Idle("Listo para traducir"),
    Listening("Escuchando..."),
    Processing("Procesando..."),
    Translating("Traduciendo..."),
    Signing("Reproduciendo señas..."),
    Done("Reproducción completada"),
    Connecting("Conectando con el servidor..."),
    Offline("Sin conexión con el servidor"),
}
