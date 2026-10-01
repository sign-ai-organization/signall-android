package com.signai.webview.ui.webview

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.webkit.WebView
import com.signai.webview.domain.SignAiCommands
import com.signai.webview.domain.SignQueueItem
import org.json.JSONArray
import org.json.JSONObject

interface SignAiWebViewController {
    fun attach(webView: WebView)

    fun play()

    fun setQueue(items: List<SignQueueItem>)

    fun setDebug(on: Boolean)

    fun setFreeCamera(on: Boolean)

    fun setGrid(on: Boolean)

    fun setBackground(on: Boolean)

    fun setTheme(dark: Boolean)

    fun setSpeed(factor: Float)

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

    override fun setQueue(items: List<SignQueueItem>) {
        if (items.isEmpty()) return
        val json = JSONArray().apply {
            items.forEach { item ->
                put(
                    JSONObject().apply {
                        put("gloss", item.gloss)
                        put("type", item.type.wire)
                        put("url", item.url)
                        put("available", item.available)
                    },
                )
            }
        }.toString()
        js(SignAiCommands.setQueue(json))
    }

    override fun setDebug(on: Boolean) = js(SignAiCommands.setDebug(on))

    override fun setFreeCamera(on: Boolean) = js(SignAiCommands.setFreeCamera(on))

    override fun setGrid(on: Boolean) = js(SignAiCommands.setGrid(on))

    override fun setBackground(on: Boolean) = js(SignAiCommands.setBackground(on))

    override fun setTheme(dark: Boolean) = js(SignAiCommands.setTheme(dark))

    override fun setSpeed(factor: Float) = js(SignAiCommands.setSpeed(factor))

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
