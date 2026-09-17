package com.signai.webview.ui.webview

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.webkit.WebView
import com.signai.webview.domain.SignAiCommands

interface SignAiWebViewController {
    fun attach(webView: WebView)

    fun play()

    fun setDebug(on: Boolean)

    fun setFreeCamera(on: Boolean)

    fun setGrid(on: Boolean)

    fun setBackground(on: Boolean)

    fun applyBlur(enabled: Boolean)
}

class WebViewSignAiController : SignAiWebViewController {
    private var webView: WebView? = null

    override fun attach(webView: WebView) {
        this.webView = webView
    }

    private fun js(script: String) {
        webView?.evaluateJavascript(script, null)
    }

    override fun play() = js(SignAiCommands.PLAY)

    override fun setDebug(on: Boolean) = js(SignAiCommands.setDebug(on))

    override fun setFreeCamera(on: Boolean) = js(SignAiCommands.setFreeCamera(on))

    override fun setGrid(on: Boolean) = js(SignAiCommands.setGrid(on))

    override fun setBackground(on: Boolean) = js(SignAiCommands.setBackground(on))

    override fun applyBlur(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            webView?.setRenderEffect(
                if (enabled) {
                    RenderEffect.createBlurEffect(16f, 16f, Shader.TileMode.CLAMP)
                } else {
                    null
                },
            )
        }
    }
}
