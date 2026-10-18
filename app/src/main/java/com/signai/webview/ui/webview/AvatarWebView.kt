package com.signai.webview.ui.webview

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.PathHandler
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.signai.webview.data.SignAssetRepository
import java.io.File
import java.io.FileInputStream

private const val BridgeObjectName = "SignAIBridge"
private const val BridgeOrigin = SignAssetRepository.WEB_ORIGIN
private const val AssetsPath = "/assets/"

/** Prefix of the scene's "the avatar is moving" message (`notifySignStart`). */
const val SignStartPrefix = "signStart:"

/**
 * Prefix of the scene's "this sign could not be loaded" message
 * (`notifySignFailed`), with the queue index.
 *
 * **Aditivo**: ni este nombre ni los anteriores existían antes, así que una escena
 * vieja no manda nada nuevo y una escena nueva con una app vieja simplemente no
 * escucha el mensaje (`BRG-8`: compatible hacia atrás, siempre).
 *
 * Es lo que distingue dos cosas que antes se confundían en un mismo aviso: una glosa
 * que el catálogo no tiene —la app ya lo decía antes de entregar la cola— y una
 * glosa que el catálogo tiene pero cuyo `.glb` no se pudo bajar, que solo se
 * descubre adentro del WebView.
 */
const val SignFailedPrefix = "signFailed:"

/**
 * Serves the sign assets the app downloaded from the backend, so the 3D scene can
 * `fetch()` them on its own origin (plan paso 4).
 *
 * This exists because the scene cannot reach the backend itself: it runs on
 * `https://appassets.androidplatform.net`, so a plain-HTTP request to the backend
 * would be mixed content and Android blocks it. `shouldInterceptRequest` already
 * delegates to the `WebViewAssetLoader`, so registering the handler is all it
 * takes.
 */
private fun signsPathHandler(dir: File): PathHandler = object : PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        val name = path.removePrefix("/")
        if (name.isEmpty() || name.contains("..")) return null
        val file = File(dir, name)
        if (!file.isFile) return null
        val mime = if (name.endsWith(".glb")) "model/gltf-binary" else "application/json"
        return runCatching { WebResourceResponse(mime, "utf-8", FileInputStream(file)) }.getOrNull()
    }
}

@Composable
fun AvatarWebView(
    modifier: Modifier = Modifier,
    onReady: (WebView) -> Unit,
    onProgress: (Int) -> Unit = {},
    onPlaybackDone: () -> Unit = {},
    /**
     * The avatar is actually moving: `signStart:<queueIndex>:<jsMillis>` from the
     * scene (`notifySignStart` in `app.js`).
     *
     * This is the leg nothing measured: the backend's clock stops when the
     * `segment` goes out, and `play()` on the Kotlin side is *before* the `.glb`
     * finishes loading. Between those two is the time the user actually waits
     * staring at a still avatar. The two clocks cannot be subtracted from each
     * other, so each side reports its own and this one is the difference between
     * the segment arriving here and the bridge message arriving here.
     */
    onSignStart: (queueIndex: Int, jsMillis: Long) -> Unit = { _, _ -> },
    /**
     * A sign of the phrase could not be loaded and was skipped: the scene keeps
     * going with the rest of the phrase (`notifySignFailed` in `app.js`).
     *
     * The state uses it to say "Seña no disponible", which is the only text of its
     * own the SRS table allows on the indicator (`main.tex:201` and `:753`).
     */
    onSignFailed: (queueIndex: Int) -> Unit = {},
    /**
     * The scene is gone: the page failed to load, or the browser lost its render
     * process (`onReceivedError` / `onRenderProcessGone`).
     *
     * New: before this the app had no way of learning the canvas was dead, so it
     * kept saying *Reproduciendo* with a still avatar and no way out.
     */
    onSceneFailed: (reason: String) -> Unit = {},
    onLoaded: () -> Unit = {},
    // While a drawer is open the WebView must not take any touch: it is an
    // embedded Android View and would otherwise win the whole screen, leaving
    // the drawer's backdrop and handle unreachable (spec `EDG-22`).
    interactive: Boolean = true,
) {
    val context = LocalContext.current
    val signsDir = remember(context) { File(context.cacheDir, SignAssetRepository.SIGNS_PATH_PREFIX.trim('/')) }
    AndroidView(
        modifier = modifier.pointerInteropFilter {
            // Returning true consumes the event here, so the scene never sees it
            // while a drawer is open and the panel/backdrop keep the taps.
            !interactive
        },
        factory = {
            WebView(it).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                // The scene has no text input and never needs the IME, but a
                // WebView is focusable by default: Android hands it the initial
                // focus on attach, and it kept it, so the phrase field never got
                // it and everything typed went to the 3D canvas instead. Found
                // with `mobile_list_elements_on_screen`, where the WebView and
                // the EditText both showed as `[focused]`.
                //
                // Both flags are needed: `isFocusableInTouchMode = false` alone
                // stops it taking focus on tap, but the initial focus on attach
                // still landed on it, because it was focusable outside touch
                // mode. The canvas only uses pointer events, so it loses
                // nothing.
                isFocusable = false
                isFocusableInTouchMode = false
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        onProgress(newProgress)
                    }

                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                        android.util.Log.d(
                            "SignAIWeb",
                            "${message.messageLevel()}: ${message.message()} @${message.lineNumber()}",
                        )
                        return true
                    }
                }

                // JS → Kotlin: `window.SignAIBridge.postMessage("onPlaybackDone")`,
                // `postMessage("avatarReady")`, `postMessage("signStart:<i>:<ms>")` y
                // `postMessage("signFailed:<i>")`.
                // Only registered when the feature is supported (fallback: no
                // playback event, the UI just stays in `signing`; y el splash se
                // destapa por el timeout de 8 s).
                val bridgeSupported =
                    WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
                if (bridgeSupported) {
                    WebViewCompat.addWebMessageListener(
                        this,
                        BridgeObjectName,
                        setOf(BridgeOrigin),
                        object : WebViewCompat.WebMessageListener {
                            override fun onPostMessage(
                                view: WebView,
                                message: androidx.webkit.WebMessageCompat,
                                sourceOrigin: android.net.Uri,
                                isMainFrame: Boolean,
                                replyProxy: androidx.webkit.JavaScriptReplyProxy,
                            ) {
                                when (message.data) {
                                    "onPlaybackDone" -> onPlaybackDone()
                                    // El avatar son ~10 MB: `onPageFinished` dispara
                                    // antes de que termine de bajar y parsear, así
                                    // que usarlo para esconder el splash dejaba ver
                                    // un canvas negro. La escena avisa cuando la
                                    // tiene en escena. Si el bridge no está
                                    // soportado, el timeout de `SignAiSplash`
                                    // (8 s) destapa igual.
                                    "avatarReady" -> onLoaded()
                                    // `startsWith` y no igualdad exacta: el mensaje
                                    // trae índice de cola y reloj propio, para que
                                    // los tres tramos (Android -> escena, escena ->
                                    // primer fotograma, escena -> Kotlin) se puedan
                                    // leer por separado en logcat.
                                    else -> message.data
                                        ?.takeIf { it.startsWith(SignStartPrefix) }
                                        ?.removePrefix(SignStartPrefix)
                                        ?.split(":")
                                        ?.let { parts ->
                                            onSignStart(
                                                parts.getOrNull(0)?.toIntOrNull() ?: 0,
                                                parts.getOrNull(1)?.toLongOrNull() ?: 0L,
                                            )
                                        }
                                        // Mismo camino, mensaje nuevo: la escena se
                                        // saltó una seña y sigue con la frase. Sin
                                        // esto el salto era invisible desde Kotlin.
                                        ?: message.data
                                            ?.takeIf { it.startsWith(SignFailedPrefix) }
                                            ?.removePrefix(SignFailedPrefix)
                                            ?.toIntOrNull()
                                            ?.let(onSignFailed)
                                }
                            }
                        },
                    )
                }
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler(AssetsPath, WebViewAssetLoader.AssetsPathHandler(context))
                    .addPathHandler(SignAssetRepository.SIGNS_PATH_PREFIX, signsPathHandler(signsDir))
                    .build()
                webViewClient = object : WebViewClientCompat() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        // Fallback: si el WebView no soporta el listener de
                        // mensajes, la escena no puede avisar y hay que destapar
                        // con el fin de la carga de la página. Cuando sí hay
                        // listener, el aviso real es `avatarReady`.
                        if (!bridgeSupported) onLoaded()
                    }

                    /**
                     * La página principal no cargó.
                     *
                     * Antes no había ninguna de estas dos callbacks, y el resultado
                     * era el mismo: si el navegador dejaba de poder con la escena, la
                     * app se quedaba diciendo *Reproduciendo* con el muñeco quieto y
                     * sin salida.
                     *
                     * Solo el marco principal: los `.glb` de las señas y el Three.js
                     * de jsDelivr son subrecursos, y el fallo de uno de ellos lo
                     * avisa la escena (`notifySignFailed`), no esta callback.
                     */
                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceErrorCompat,
                    ) {
                        // La firma es medio de plataforma y medio `Compat`: la de
                        // plataforma está `final` en `WebViewClientCompat` y es ella la
                        // que despacha a esta.
                        super.onReceivedError(view, request, error)
                        if (!request.isForMainFrame) return
                        onSceneFailed("No se pudo cargar la escena: ${error.description}")
                    }

                    /**
                     * El navegador perdió su proceso de render.
                     *
                     * `false` = "no lo atendí": el sistema **mata la app** y el launcher la vuelve a
                     * levantar con todo funcionando. Es lo que decidió el owner el
                     * 2026-10-17, y es lo único que funciona: un WebView sin proceso
                     * de render **no renderiza nunca más**, así que con `true` la app
                     * sobreviviría con un canvas negro por el resto de la sesión y
                     * la única salida sería cerrarla a mano. Perder lo que había en el
                     * campo es menos malo que un avatar muerto.
                     *
                     * El aviso va igual antes de devolver: el proceso se va a morir,
                     * pero queda en el log por qué pasó, y `sceneDead` alcanza a
                     * limpiar el estado en el poco tiempo que hay.
                     */
                    override fun onRenderProcessGone(
                        view: WebView,
                        detail: android.webkit.RenderProcessGoneDetail,
                    ): Boolean {
                        android.util.Log.e(
                            "SignAIWeb",
                            "el proceso de render del WebView murió (${detail.didCrash()}); la app se reinicia",
                        )
                        onSceneFailed("La escena 3D dejó de funcionar: la app se está reiniciando.")
                        return false
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: android.webkit.WebResourceRequest,
                    ): android.webkit.WebResourceResponse? {
                        val resp = assetLoader.shouldInterceptRequest(request.url) ?: return null
                        val headers = resp.responseHeaders ?: mutableMapOf()
                        headers["Cache-Control"] = "no-store"
                        resp.responseHeaders = headers
                        return resp
                    }
                }
                loadUrl("$BridgeOrigin$AssetsPath" + "web/index.html")
                onReady(this)
            }
        },
    )
}
