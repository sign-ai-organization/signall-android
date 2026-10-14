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

    fun jsToggle(prop: String, on: Boolean) = "window.SignAI?.$prop(!!$on)"

    /**
     * Hands the scene the signs to play, in order (plan paso 3). Additive: the
     * five original commands and the `?.` guard are unchanged (`BRG-8`).
     *
     * [json] is already serialized, so it is quoted into a JS string literal and
     * parsed on the JS side — never interpolated raw into the script.
     */
    fun setQueue(json: String) = "window.SignAI?.setQueue(${JSONObject.quote(json)})"

    /**
     * Agrega señas al final de la cola sin reiniciar la que ya se está firmando.
     *
     * La mitad en tiempo real de [setQueue]: el backend manda un fragmento de la
     * frase apenas confirma que esas palabras ya se dijeron, y el muñeco lo firma
     * mientras la persona sigue hablando. [setQueue] sigue reemplazando, que es
     * lo correcto para una frase escrita o un "Repetir".
     *
     * Aditivo: el guard `?.` y los comandos anteriores no cambian (`BRG-8`).
     */
    fun appendQueue(json: String) = "window.SignAI?.appendQueue(${JSONObject.quote(json)})"

    /**
     * Arranca a firmar desde [index] sin volver al principio de la cola.
     *
     * `play()` asume que la cola está completa y salta al ítem 0. En streaming
     * la cola recién empieza a llenarse, así que arrancar tiene que respetar
     * el índice. Si ya hay algo sonando no hace nada, que es lo que corresponde
     * cuando llega un fragmento mientras el anterior se reproduce.
     */
    fun playFrom(index: Int) = "window.SignAI?.playFrom(${index.coerceAtLeast(0)})"

    /**
     * Sets the sign the scene opens on and falls back to (settings menu).
     *
     * [url] is a same-origin asset path from [SignsCatalog.assetUrl]; it is
     * quoted into a JS string literal, and the JS side additionally refuses
     * anything outside `signs/`.
     */
    fun setDefaultSign(url: String) = "window.SignAI?.setDefaultSign(${JSONObject.quote(url)})"
}
