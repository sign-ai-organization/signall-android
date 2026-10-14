package com.signai.webview.ui.state

/**
 * Cuál de los dos interruptores del micrófono está prendido, si alguno (spec
 * `STA-16`/`STA-17`).
 *
 * Los dos son interruptores y los dos usan la **misma** captura: hay un solo
 * `AudioRecord` y un solo WebSocket, así que no pueden estar prendidos a la vez.
 * Por eso no son dos booleanos sino un solo enum —con dos flags, "el micrófono
 * está apagado" y "los dos están prendidos" serían estados posibles, y en los
 * dos la app se rompe.
 *
 * - [Dictation] — el micrófono de la fila de arriba. Se toca, se habla
 *   mientras se quiera y se toca de nuevo. Solo **transcribe**: el texto se va
 *   acumulando en el input y el muñeco no se mueve. Traducirlo es una decisión
 *   aparte, la del botón Traducir.
 * - [Realtime] — el interruptor `TR` de la fila de abajo. Se toca, firma
 *   mientras se habla sin esperar a que termine la frase, y se toca de nuevo
 *   para apagar.
 *
 * [Off] además significa "no hay captura": el micrófono no manda audio, el
 * backend recibe solo frases escritas.
 */
enum class MicMode {
    Off,
    Dictation,
    Realtime,
}
