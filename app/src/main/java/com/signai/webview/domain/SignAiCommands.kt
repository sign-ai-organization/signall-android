package com.signai.webview.domain

import org.json.JSONObject

object SignAiCommands {
    const val PLAY = "window.SignAI?.play()"

    fun setDebug(on: Boolean) = jsToggle("setDebug", on)

    fun setFreeCamera(on: Boolean) = jsToggle("setFreeCamera", on)

    fun setGrid(on: Boolean) = jsToggle("setGrid", on)

    fun setBackground(on: Boolean) = jsToggle("setBackground", on)

    fun setTheme(dark: Boolean) = jsToggle("setTheme", dark)

    fun setSpeed(factor: Float) = "window.SignAI?.setSpeed(${factor.coerceIn(0f, 1f)})"

    /**
     * Turns the sign-to-sign crossfade off (accessibility `ACC-*`).
     *
     * The scene mixes two clips over 100 ms so one sign flows into the next. That
     * movement is part of reading the sign, so with the setting on every
     * transition is a hard cut instead.
     */
    fun setReduceMotion(on: Boolean) = jsToggle("setReduceMotion", on)

    fun jsToggle(prop: String, on: Boolean) = "window.SignAI?.$prop(!!$on)"

    /**
     * Le dice a la escena cuánto silencio está usando el STT, para el overlay de
     * depurar.
     *
     * No es un ajuste: la app nunca habla con el STT, así que el valor llega
     * desde `/health` del backend (que lo copia del STT). Va como número
     * porque el JS lo valida; `null` deja el overlay con "?", que es lo que
     * hay que ver cuando no se sabe.
     */
    fun setSilenceTimeoutMs(ms: Int?) =
        "window.SignAI?.setSilenceTimeoutMs(${ms ?: "null"})"

    /**
     * Hands the scene the signs to play, in order (plan paso 3). Additive: the
     * five original commands and the `?.` guard are unchanged (`BRG-8`).
     *
     * [json] is already serialized, so it is quoted into a JS string literal and
     * parsed on the JS side — never interpolated raw into the script.
     */
    fun setQueue(json: String) = "window.SignAI?.setQueue(${JSONObject.quote(json)})"

    /**
     * Agrega señas al final de la cola sin reiniciar la que ya se está representando.
     *
     * La mitad en tiempo real de [setQueue]: el backend manda un fragmento de la
     * frase apenas confirma que esas palabras ya se dijeron, y el muñeco lo representa
     * mientras la persona sigue hablando. [setQueue] sigue reemplazando, que es
     * lo correcto para una frase escrita o un "Repetir".
     *
     * Aditivo: el guard `?.` y los comandos anteriores no cambian (`BRG-8`).
     */
    fun appendQueue(json: String) = "window.SignAI?.appendQueue(${JSONObject.quote(json)})"

    /**
     * Arranca a representar desde [index] sin volver al principio de la cola.
     *
     * `play()` asume que la cola está completa y salta al ítem 0. En streaming
     * la cola recién empieza a llenarse, así que arrancar tiene que respetar
     * el índice. Si ya hay algo sonando no hace nada, que es lo que corresponde
     * cuando llega un fragmento mientras el anterior se reproduce.
     */
    fun playFrom(index: Int) = "window.SignAI?.playFrom(${index.coerceAtLeast(0)})"

    /**
     * Corta lo que esté sonando y deja al muñeco en pose de reposo.
     *
     * Lo necesita el estado *Desconectado* del SRS (`main.tex:761-772`): "si la
     * pérdida ocurre cuando el avatar ya está representando, la representación se
     * interrumpe". Sin esto la app volvía a reposo en la interfaz mientras el
     * canvas seguía con la seña adelante, que es el peor de los dos mundos.
     *
     * Aditivo, como todos: el guard `?.` y los comandos anteriores no cambian
     * (`BRG-8`), y en el JS es un método nuevo al lado de los otros.
     */
    const val STOP = "window.SignAI?.stop()"

    /**
     * Sets the sign the scene opens on and falls back to (settings menu).
     *
     * [url] is a same-origin asset path from [SignsCatalog.assetUrl]; it is
     * quoted into a JS string literal, and the JS side additionally refuses
     * anything outside `signs/`.
     */
    fun setDefaultSign(url: String) = "window.SignAI?.setDefaultSign(${JSONObject.quote(url)})"

    /**
     * Cambia el avatar completo que está en escena.
     *
     * [id] es el identificador de [Character.id] (`juan`, `pauline`); la escena
     * lo resuelve contra un catálogo local de rutas y **nunca** arma una URL con
     * un valor arbitrario, así que este comando no puede pedir algo fuera de
     * `models/`.
     *
     * Aditivo: el guard `?.` y todos los comandos anteriores siguen igual
     * (`BRG-8`). La escena responde con `avatarLoaded:<id>` o
     * `avatarFailed:<id>` por el puente, que también son mensajes nuevos.
     */
    fun setAvatar(id: String) = "window.SignAI?.setAvatar(${JSONObject.quote(id)})"
}
