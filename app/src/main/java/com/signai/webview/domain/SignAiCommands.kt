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
}
