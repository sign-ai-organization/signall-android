package com.signai.webview.ui.state

/**
 * Playback state machine of the translation flow (spec `STA-*`).
 *
 * `idle → translating → signing → done`, plus the mic placeholder
 * `listening`/`processing`. Phase B only renders these states; the real
 * transitions and the JS `onPlaybackDone` callback are wired in Phase E.
 */
enum class PlaybackState(val label: String) {
    Idle("Listo para traducir"),
    Listening("Escuchando..."),
    Processing("Procesando..."),
    Translating("Traduciendo..."),
    Signing("Reproduciendo señas..."),
    Done("Reproducción completada"),
}
