package com.signall.app.ui.webview

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
import com.signall.app.data.SignAssetRepository
import java.io.File
import java.io.FileInputStream

private const val BridgeObjectName = "SignAllBridge"
private const val BridgeOrigin = SignAssetRepository.WEB_ORIGIN
private const val AssetsPath = "/assets/"

/** Prefix of the scene's "the avatar is moving" message (`notifySignStart`). */
const val SignStartPrefix = "signStart:"

/**
 * Prefix of the scene's loading progress (`reportProgress`), `progress:<0..100>`.
 *
 * Existe porque el progreso de la página no sirve para el splash: el WebView dice
 * 100 % cuando terminó de cargar el HTML, y en ese momento todavía falta bajar,
 * parsear y compilar 13 MB de avatar. Los números de acá son los de la escena
 * (descarga → parseo → clips indexados → shaders compilados).
 *
 * **Aditivo**: una escena vieja no lo manda y la barra sigue como antes.
 */
const val ProgressPrefix = "progress:"

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
 * La escena terminó de cambiar de avatar: `avatarLoaded:<id>`.
 *
 * Solo se manda en los cambios posteriores al arranque. El primero sigue
 * usando `avatarReady`, que es el aviso que destapa el splash. Aditivo: una
 * escena vieja no lo manda y la app no se queda esperando nada que no exista.
 */
const val AvatarLoadedPrefix = "avatarLoaded:"

/** La escena no pudo cargar el avatar pedido: `avatarFailed:<id>`. */
const val AvatarFailedPrefix = "avatarFailed:"

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

/** Los avisos que la escena manda por el puente, todos opcionales. */
class AvatarBridgeCallbacks(
    val onProgress: (Int) -> Unit = {},
    val onPlaybackDone: () -> Unit = {},
    val onSignStart: (queueIndex: Int, jsMillis: Long) -> Unit = { _, _ -> },
    val onSignFailed: (queueIndex: Int) -> Unit = {},
    val onAvatarLoaded: (id: String) -> Unit = {},
    val onAvatarFailed: (id: String) -> Unit = {},
    val onSceneFailed: (reason: String) -> Unit = {},
    val onLoaded: () -> Unit = {},
)

@Composable
fun AvatarWebView(
    onReady: (WebView) -> Unit,
    callbacks: AvatarBridgeCallbacks,
    modifier: Modifier = Modifier,
    // While a drawer is open the WebView must not take any touch: it is an
    // embedded Android View and would otherwise win the whole screen, leaving
    // the drawer's backdrop and handle unreachable (spec `EDG-22`).
    interactive: Boolean = true,
    /**
     * Avatar que la escena tiene que cargar **al abrir la página** (`juan`,
     * `pauline`).
     *
     * Va en la URL y no por el puente porque la escena empieza a cargar el
     * modelo en cuanto corre `app.js`: un `setAvatar` posterior llegaría con la
     * descarga del avatar por defecto ya en vuelo y bajaría dos modelos. Los
     * cambios en caliente sí van por `setAvatar`.
     */
    avatarId: String = "juan",
) {
    val context = LocalContext.current
    val signsDir = remember(context) {
        File(context.cacheDir, SignAssetRepository.SIGNS_PATH_PREFIX.trim('/'))
    }
    AndroidView(
        modifier = modifier.pointerInteropFilter {
            // Returning true consumes the event here, so the scene never sees it
            // while a drawer is open and the panel/backdrop keep the taps.
            !interactive
        },
        factory = { buildSceneWebView(it, signsDir, avatarId, callbacks, onReady) },
    )
}

/** El progreso de la escena, que empieza en -1 ("todavía no avisó"). */
private class SceneBridgeState {
    var progress: Int = -1
}

private fun buildSceneWebView(
    context: android.content.Context,
    signsDir: File,
    avatarId: String,
    callbacks: AvatarBridgeCallbacks,
    onReady: (WebView) -> Unit,
): WebView {
    val state = SceneBridgeState()
    val bridgeSupported =
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    return WebView(context).apply {
        configureSceneWebView()
        webChromeClient = sceneChromeClient(bridgeSupported, state, callbacks)
        if (bridgeSupported) registerBridge(this, state, callbacks)
        webViewClient = sceneWebViewClient(context, signsDir, bridgeSupported, callbacks)
        loadUrl(
            "$BridgeOrigin$AssetsPath" + "web/index.html?avatar=" +
                android.net.Uri.encode(avatarId),
        )
        onReady(this)
    }
}

/**
 * Settings del WebView de la escena.
 *
 * El campo de texto (`settings`/`isFocusable`) importa: la escena no tiene
 * entrada de texto y nunca necesita el IME, pero un WebView es focusable por
 * defecto. Android le daba el foco inicial al attach, lo conservaba, y entonces
 * el campo de la frase nunca lo recibía y todo lo que se escribía iba al canvas
 * 3D. Las dos banderas hacen falta: `isFocusableInTouchMode = false` solo evita
 * que tome el foco al tocarlo, pero el foco inicial del attach seguía cayendo ahí
 * porque era focusable fuera del modo táctil. El canvas solo usa eventos de
 * puntero, así que no pierde nada.
 */
private fun WebView.configureSceneWebView() {
    settings.javaScriptEnabled = true
    settings.mediaPlaybackRequiresUserGesture = false
    isFocusable = false
    isFocusableInTouchMode = false
}

/**
 * El progreso de la escena NO es el de la página: la página termina de cargar
 * cuando todavía falta bajar y parsear los 13 MB del avatar, así que el splash se
 * quedaba clavado en "100 %" sobre un canvas vacío. Mientras la escena no avise,
 * la página ocupa solo el primer tramo de la barra; después manda la escena.
 */
private fun sceneChromeClient(
    bridgeSupported: Boolean,
    state: SceneBridgeState,
    callbacks: AvatarBridgeCallbacks,
): android.webkit.WebChromeClient = object : android.webkit.WebChromeClient() {
    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        callbacks.onProgress(
            when {
                !bridgeSupported -> newProgress
                state.progress >= 0 -> state.progress
                else -> minOf(newProgress * 2 / 5, 45)
            },
        )
    }

    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
        android.util.Log.d(
            "SignAllWeb",
            "${message.messageLevel()}: ${message.message()} @${message.lineNumber()}",
        )
        return true
    }
}

/**
 * JS → Kotlin: `window.SignAllBridge.postMessage("onPlaybackDone")`,
 * `postMessage("avatarReady")`, `postMessage("progress:<0..100>")`,
 * `postMessage("signStart:<i>:<ms>")` y `postMessage("signFailed:<i>")`.
 *
 * Only registered when the feature is supported (fallback: no playback event,
 * the UI just stays in `signing`; y el splash se destapa por el timeout de 8 s).
 */
private fun registerBridge(
    view: WebView,
    state: SceneBridgeState,
    callbacks: AvatarBridgeCallbacks,
) {
    WebViewCompat.addWebMessageListener(
        view,
        BridgeObjectName,
        setOf(BridgeOrigin),
        object : WebViewCompat.WebMessageListener {
            override fun onPostMessage(
                webView: WebView,
                message: androidx.webkit.WebMessageCompat,
                sourceOrigin: android.net.Uri,
                isMainFrame: Boolean,
                replyProxy: androidx.webkit.JavaScriptReplyProxy,
            ) {
                dispatchBridgeMessage(message.data, state, callbacks)
            }
        },
    )
}

/**
 * Reparte un mensaje de la escena.
 *
 * Los que traen un valor pegado (`progress:`, `avatarLoaded:`, `avatarFailed:`,
 * `signStart:`, `signFailed:`) van antes del `when` de igualdad porque se leen
 * con `startsWith`; los otros dos son nombres exactos.
 */
private fun dispatchBridgeMessage(
    data: String?,
    state: SceneBridgeState,
    callbacks: AvatarBridgeCallbacks,
) {
    if (data == null) return
    if (data.startsWith(ProgressPrefix)) {
        data.removePrefix(ProgressPrefix).toIntOrNull()?.let { pct ->
            state.progress = pct.coerceIn(0, 100)
            callbacks.onProgress(state.progress)
        }
        return
    }
    // Cambio de avatar en caliente. Va antes del `when` porque el mensaje trae
    // el id pegado.
    if (data.startsWith(AvatarLoadedPrefix)) {
        callbacks.onAvatarLoaded(data.removePrefix(AvatarLoadedPrefix))
        return
    }
    if (data.startsWith(AvatarFailedPrefix)) {
        callbacks.onAvatarFailed(data.removePrefix(AvatarFailedPrefix))
        return
    }
    when {
        data == "onPlaybackDone" -> callbacks.onPlaybackDone()
        // El avatar son 13 MB con las 50 señas adentro: `onPageFinished` dispara
        // antes de que termine de bajar, parsear y compilar, así que usarlo para
        // esconder el splash dejaba ver un canvas negro. La escena avisa cuando ya
        // está todo cargado y hay un frame pintado. Si el bridge no está
        // soportado, el timeout de `SignAllSplash` (8 s) destapa igual.
        data == "avatarReady" -> callbacks.onLoaded()
        // `startsWith` y no igualdad exacta: el mensaje trae índice de cola y
        // reloj propio, para que los tres tramos (Android -> escena, escena ->
        // primer fotograma, escena -> Kotlin) se puedan leer por separado en
        // logcat.
        data.startsWith(SignStartPrefix) -> {
            val parts = data.removePrefix(SignStartPrefix).split(":")
            callbacks.onSignStart(
                parts.getOrNull(0)?.toIntOrNull() ?: 0,
                parts.getOrNull(1)?.toLongOrNull() ?: 0L,
            )
        }
        // Mismo camino, mensaje nuevo: la escena se saltó una seña y sigue con la
        // frase. Sin esto el salto era invisible desde Kotlin.
        data.startsWith(SignFailedPrefix) ->
            data.removePrefix(SignFailedPrefix).toIntOrNull()?.let(callbacks.onSignFailed)
    }
}

private fun sceneWebViewClient(
    context: android.content.Context,
    signsDir: File,
    bridgeSupported: Boolean,
    callbacks: AvatarBridgeCallbacks,
): WebViewClientCompat {
    val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler(AssetsPath, WebViewAssetLoader.AssetsPathHandler(context))
        .addPathHandler(SignAssetRepository.SIGNS_PATH_PREFIX, signsPathHandler(signsDir))
        .build()
    return object : WebViewClientCompat() {
        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            // Fallback: si el WebView no soporta el listener de mensajes, la
            // escena no puede avisar y hay que destapar con el fin de la carga de
            // la página. Cuando sí hay listener, el aviso real es `avatarReady`.
            if (!bridgeSupported) callbacks.onLoaded()
        }

        /**
         * La página principal no cargó.
         *
         * Antes no había ninguna de estas dos callbacks, y el resultado era el
         * mismo: si el navegador dejaba de poder con la escena, la app se quedaba
         * diciendo *Reproduciendo* con el muñeco quieto y sin salida.
         *
         * Solo el marco principal: los `.glb` de las señas y el Three.js de
         * jsDelivr son subrecursos, y el fallo de uno de ellos lo avisa la escena
         * (`notifySignFailed`), no esta callback.
         */
        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceErrorCompat,
        ) {
            // La firma es medio de plataforma y medio `Compat`: la de plataforma
            // está `final` en `WebViewClientCompat` y es ella la que despacha a
            // esta.
            super.onReceivedError(view, request, error)
            if (!request.isForMainFrame) return
            callbacks.onSceneFailed("No se pudo cargar la escena: ${error.description}")
        }

        /**
         * El navegador perdió su proceso de render.
         *
         * `false` = "no lo atendí": el sistema **mata la app** y el launcher la
         * vuelve a levantar con todo funcionando. Es lo que decidió el owner el
         * 2026-10-17, y es lo único que funciona: un WebView sin proceso de render
         * **no renderiza nunca más**, así que con `true` la app sobreviviría con un
         * canvas negro por el resto de la sesión y la única salida sería cerrarla a
         * mano. Perder lo que había en el campo es menos malo que un avatar muerto.
         *
         * El aviso va igual antes de devolver: el proceso se va a morir, pero queda
         * en el log por qué pasó, y `sceneDead` alcanza a limpiar el estado en el
         * poco tiempo que hay.
         */
        override fun onRenderProcessGone(
            view: WebView,
            detail: android.webkit.RenderProcessGoneDetail,
        ): Boolean {
            android.util.Log.e(
                "SignAllWeb",
                "el proceso de render del WebView murió (${detail.didCrash()}); la app se reinicia",
            )
            callbacks.onSceneFailed(
                "La escena 3D dejó de funcionar: la app se está reiniciando.",
            )
            return false
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: android.webkit.WebResourceRequest,
        ): WebResourceResponse? {
            val resp = assetLoader.shouldInterceptRequest(request.url) ?: return null
            val headers = resp.responseHeaders ?: mutableMapOf()
            // Fuerza `no-store` para que un cambio de `app.js` se vea al recargar.
            headers["Cache-Control"] = "no-store"
            resp.responseHeaders = headers
            return resp
        }
    }
}
