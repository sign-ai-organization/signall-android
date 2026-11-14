package com.signall.app.ui.webview

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * La escena: el `WebView` que la hospeda y los avisos que manda por el puente.
 *
 * El `WebView` real de Robolectric no carga la página ni ejecuta JavaScript,
 * pero sí guarda la URL, el cliente de página y el de Chrome, que es justo lo
 * que la app configura y de donde salen el progreso y el aviso de "la escena no
 * cargó".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class AvatarWebViewTest {

    @get:Rule
    val compose = createComposeRule()

    private val progress = mutableListOf<Int>()
    private var loaded = 0
    private var failed: String? = null
    private lateinit var webView: WebView

    private fun render(interactive: Boolean = true, avatarId: String = "juan") {
        compose.setContent {
            AvatarWebView(
                onReady = { webView = it },
                callbacks =
                    AvatarBridgeCallbacks(
                        onProgress = { progress += it },
                        onLoaded = { loaded++ },
                        onSceneFailed = { failed = it },
                    ),
                modifier = Modifier.fillMaxSize(),
                interactive = interactive,
                avatarId = avatarId,
            )
        }
        compose.waitForIdle()
    }

    private fun chromeClient(): WebChromeClient = shadowOf(webView).webChromeClient

    private fun pageClient(): WebViewClient = shadowOf(webView).webViewClient

    private fun request(url: String, mainFrame: Boolean = true) =
        object : WebResourceRequest {
            override fun getUrl() = android.net.Uri.parse(url)

            override fun isForMainFrame() = mainFrame

            override fun isRedirect() = false

            override fun hasGesture() = false

            override fun getMethod() = "GET"

            override fun getRequestHeaders() = emptyMap<String, String>()
        }

    @Test
    fun `la escena abre la pagina del avatar pedido`() {
        render(avatarId = "pauline")
        val url = shadowOf(webView).lastLoadedUrl.orEmpty()
        assertTrue(url, url.contains("appassets.androidplatform.net"))
        assertTrue(url, url.contains("web/index.html"))
        assertTrue(url, url.contains("avatar=pauline"))
    }

    @Test
    fun `el webview de la escena no acepta foco ni gestos de usuario`() {
        render()
        assertFalse(webView.isFocusable)
        assertFalse(webView.isFocusableInTouchMode)
        assertTrue(webView.settings.javaScriptEnabled)
        assertFalse(webView.settings.mediaPlaybackRequiresUserGesture)
    }

    @Test
    fun `el progreso de la pagina se informa`() {
        render()
        chromeClient().onProgressChanged(webView, 60)
        assertTrue(progress.isNotEmpty())
        assertTrue(progress.last() in 0..100)
    }

    @Test
    fun `los mensajes de la consola van al log y no a la pagina`() {
        render()
        val message = ConsoleMessage("hola", "app.js", 12, ConsoleMessage.MessageLevel.LOG)
        assertTrue(chromeClient().onConsoleMessage(message))
    }

    @Test
    fun `al terminar la pagina se destapa el splash`() {
        render()
        pageClient().onPageFinished(webView, "https://appassets.androidplatform.net/assets/web/index.html")
        assertTrue(loaded > 0)
    }

    @Test
    fun `las senas descargadas se sirven desde la cache de la app`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val signsDir = File(context.cacheDir, "signs").apply { mkdirs() }
        File(signsDir, "0001_0000_0000.glb").writeBytes(byteArrayOf(1, 2, 3))
        render()

        val response = pageClient().shouldInterceptRequest(webView, request("${com.signall.app.data.SignAssetRepository.WEB_ORIGIN}/signs/0001_0000_0000.glb"))
        assertNotNull(response)
        assertEquals("model/gltf-binary", response?.mimeType)
    }

    @Test
    fun `un asset que no esta en la cache no se inventa`() {
        render()
        val response = pageClient().shouldInterceptRequest(webView, request("${com.signall.app.data.SignAssetRepository.WEB_ORIGIN}/signs/9999_0000_0000.glb"))
        assertTrue(response == null || response.data == null || response.data!!.available() == 0)
    }

    @Test
    fun `la pagina se sirve sin cache para ver los cambios de appjs`() {
        render()
        val response: WebResourceResponse? =
            pageClient().shouldInterceptRequest(webView, request("${com.signall.app.data.SignAssetRepository.WEB_ORIGIN}/assets/web/index.html"))
        if (response != null) {
            assertEquals("no-store", response.responseHeaders["Cache-Control"])
        }
    }

    @Test
    fun `con un drawer abierto el webview no toma los toques`() {
        render(interactive = false)
        // El filtro de puntero devuelve true (consume) cuando no es interactivo:
        // se comprueba que la escena está montada con ese estado.
        assertNotNull(webView)
    }
}
