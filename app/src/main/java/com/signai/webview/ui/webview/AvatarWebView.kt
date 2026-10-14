package com.signai.webview.ui.webview

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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.signai.webview.data.SignAssetRepository
import java.io.File
import java.io.FileInputStream

private const val BridgeObjectName = "SignAIBridge"
private const val BridgeOrigin = SignAssetRepository.WEB_ORIGIN
private const val AssetsPath = "/assets/"

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

                // JS → Kotlin: `window.SignAIBridge.postMessage("onPlaybackDone")`
                // y `postMessage("avatarReady")`.
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
