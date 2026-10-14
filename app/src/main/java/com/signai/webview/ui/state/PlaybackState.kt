package com.signai.webview.ui.state

/**
 * Playback state machine of the translation flow (spec `STA-*`).
 *
 * `idle → translating → signing → done`, plus the two voice states the mic
 * switch can be in (`dictating`/`listening`), `processing` and the two
 * connection states added by the backend integration (plan paso 1).
 */
enum class PlaybackState(val label: String) {
    Idle("Listo para traducir"),
    /** 🎤 Dictado: se está transcribiendo la voz a texto y no se firma nada. */
    Dictating("Dictando..."),
    /** ◢◢ Tiempo real: se está firmando mientras la persona habla. */
    Listening("Escuchando..."),
    Processing("Procesando..."),
    Translating("Traduciendo..."),
    Signing("Reproduciendo señas..."),
    Done("Reproducción completada"),
    Connecting("Conectando con el servidor..."),
    Offline("Sin conexión con el servidor"),
}
