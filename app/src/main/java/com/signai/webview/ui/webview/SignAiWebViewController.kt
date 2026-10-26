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

    /**
     * Agrega [items] al final de la cola en curso, sin reiniciarla.
     *
     * Es lo que hace posible representar mientras la persona sigue hablando: el
     * backend no espera a tener la frase entera, manda un fragmento y esto lo
     * encadena detrás de lo que ya está sonando.
     */
    fun appendQueue(items: List<SignQueueItem>)

    /** Arranca a representar desde [index]; no hace nada si ya hay algo sonando. */
    fun playFrom(index: Int)

    /**
     * Corta la representación en curso y deja al muñeco en pose de reposo.
     *
     * Estado *Desconectado* del SRS (`main.tex:761-772`): cuando la conexión se cae
     * con el avatar representando, la representación se interrumpe. Es aditivo —
     * el resto del puente no cambia (`BRG-8`).
     */
    fun stop()

    fun setDefaultSign(url: String)

    /**
     * Cambia el avatar en escena (`juan` / `pauline`).
     *
     * La escena valida el identificador contra su catálogo local y responde con
     * `avatarLoaded:<id>` o `avatarFailed:<id>`. Aditivo: no cambia ningún
     * comando existente (`BRG-8`).
     */
    fun setAvatar(id: String)

    fun setDebug(on: Boolean)

    fun setFreeCamera(on: Boolean)

    fun setGrid(on: Boolean)

    fun setBackground(on: Boolean)

    fun setTheme(dark: Boolean)

    fun setSpeed(factor: Float)

    /** Sin fundición entre señas: cada transición es un corte seco (`ACC-*`). */
    fun setReduceMotion(on: Boolean)

    /**
     * El `SILENCE_TIMEOUT_MS` del STT, para el overlay de depurar.
     *
     * No cambia cómo se representa nada: el valor solo se muestra (decisión
     * D-6), y `null` deja el overlay diciendo "?", que es lo que hay que ver
     * cuando no se sabe.
     */
    fun setSilenceTimeoutMs(ms: Int?)

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
        js(SignAiCommands.setQueue(serialize(items)))
    }

    override fun appendQueue(items: List<SignQueueItem>) {
        if (items.isEmpty()) return
        js(SignAiCommands.appendQueue(serialize(items)))
    }

    override fun playFrom(index: Int) = js(SignAiCommands.playFrom(index))

    override fun stop() = js(SignAiCommands.STOP)

    private fun serialize(items: List<SignQueueItem>): String = JSONArray().apply {
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

    override fun setDefaultSign(url: String) = js(SignAiCommands.setDefaultSign(url))

    override fun setAvatar(id: String) = js(SignAiCommands.setAvatar(id))

    override fun setDebug(on: Boolean) = js(SignAiCommands.setDebug(on))

    override fun setFreeCamera(on: Boolean) = js(SignAiCommands.setFreeCamera(on))

    override fun setGrid(on: Boolean) = js(SignAiCommands.setGrid(on))

    override fun setBackground(on: Boolean) = js(SignAiCommands.setBackground(on))

    override fun setTheme(dark: Boolean) = js(SignAiCommands.setTheme(dark))

    override fun setSpeed(factor: Float) = js(SignAiCommands.setSpeed(factor))

    override fun setReduceMotion(on: Boolean) = js(SignAiCommands.setReduceMotion(on))

    override fun setSilenceTimeoutMs(ms: Int?) = js(SignAiCommands.setSilenceTimeoutMs(ms))

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
