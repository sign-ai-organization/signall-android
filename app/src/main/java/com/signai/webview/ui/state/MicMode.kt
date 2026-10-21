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
 * - [Dictation] — el micrófono de la fila de arriba. Se toca, se habla, y la
 *   frase se cierra sola cuando el STT oye la pausa (o cuando se toca de nuevo).
 *   Solo **transcribe**: el texto queda en el input, editable, y el muñeco no se
 *   mueve. Mandarlo al módulo de lenguaje es una decisión aparte y explícita del
 *   usuario —RF-STT-03, y el tercer estado de RF-UI-01—, que es el botón Traducir.
 * - [Live] — el interruptor `TR` de la fila de abajo. El micrófono **queda
 *   prendido** entre frases: cada frase que cierra por la pausa se manda sola y
 *   el avatar la representa oración por oración, sin que nadie toque nada.
 *
 * [Off] además significa "no hay captura": el micrófono no manda audio, el
 * backend recibe solo frases escritas.
 *
 * Lo que el valor se llama dice qué hace: antes este valor era `Realtime` y
 * significaba "el avatar representa mientras la persona habla, sin esperar al final de
 * la frase". Eso se desarmó —la unidad de traducción es la oración y el STT
 * corta por pausas de 1,5 s—, así que hoy *en vivo* quiere decir otra cosa: la
 * frase sale sola cuando termina, no a mitad de camino. Ver
 * `docs/CAMBIOS.md` (opción A).
 */
enum class MicMode {
    Off,
    Dictation,
    Live,
}