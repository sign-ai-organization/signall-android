package com.signai.webview.domain

object SignAiCommands {
    const val PLAY = "window.SignAI?.play()"

    fun setDebug(on: Boolean) = jsToggle("setDebug", on)

    fun setFreeCamera(on: Boolean) = jsToggle("setFreeCamera", on)

    fun setGrid(on: Boolean) = jsToggle("setGrid", on)

    fun setBackground(on: Boolean) = jsToggle("setBackground", on)

    fun jsToggle(prop: String, on: Boolean) = "window.SignAI?.$prop(!!$on)"
}
