package com.signall.app.ui.webview

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.signall.app.domain.PhraseType
import com.signall.app.domain.SignAllCommands
import com.signall.app.domain.SignQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * El puente Kotlin → JavaScript. Cada método es un comando con nombre propio:
 * si cambia el texto, la escena deja de responder y no hay compilador que lo
 * avise. Se comparan las cadenas exactas contra [SignAllCommands].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WebViewSignAllControllerTest {

    private lateinit var webView: WebView
    private lateinit var controller: WebViewSignAllController

    @Before
    fun setUp() {
        webView = WebView(ApplicationProvider.getApplicationContext())
        controller = WebViewSignAllController()
        controller.attach(webView)
    }

    private fun lastScript(): String? = shadowOf(webView).lastEvaluatedJavascript

    private val items =
        listOf(
            SignQueueItem(gloss = "YO", type = PhraseType.AFFIRMATIVE, url = "/signs/a.glb", available = true),
            SignQueueItem(gloss = "AGUA", type = PhraseType.NEGATIVE, url = "/signs/b.glb", available = false),
        )

    @Test
    fun `sin webview no explota`() {
        val suelto = WebViewSignAllController()
        suelto.play()
        suelto.setQueue(items)
        suelto.stop()
        suelto.applyBlur(true)
        suelto.setSilenceTimeoutMs(null)
    }

    @Test
    fun `los comandos simples llegan tal cual`() {
        controller.play()
        assertEquals(SignAllCommands.PLAY, lastScript())

        controller.playFrom(2)
        assertEquals(SignAllCommands.playFrom(2), lastScript())

        controller.stop()
        assertEquals(SignAllCommands.STOP, lastScript())

        controller.setDefaultSign("signs/0001_0000_0000.glb")
        assertEquals(SignAllCommands.setDefaultSign("signs/0001_0000_0000.glb"), lastScript())

        controller.setAvatar("pauline")
        assertEquals(SignAllCommands.setAvatar("pauline"), lastScript())

        controller.setTheme(false)
        assertEquals(SignAllCommands.setTheme(false), lastScript())

        controller.setSpeed(0.8f)
        assertEquals(SignAllCommands.setSpeed(0.8f), lastScript())

        controller.setReduceMotion(true)
        assertEquals(SignAllCommands.setReduceMotion(true), lastScript())

        controller.setSilenceTimeoutMs(1500)
        assertEquals(SignAllCommands.setSilenceTimeoutMs(1500), lastScript())

        controller.setSilenceTimeoutMs(null)
        assertEquals(SignAllCommands.setSilenceTimeoutMs(null), lastScript())
    }

    @Test
    fun `los ajustes de escena llegan tal cual`() {
        controller.setDebug(true)
        assertEquals(SignAllCommands.setDebug(true), lastScript())
        controller.setFreeCamera(true)
        assertEquals(SignAllCommands.setFreeCamera(true), lastScript())
        controller.setGrid(false)
        assertEquals(SignAllCommands.setGrid(false), lastScript())
        controller.setBackground(true)
        assertEquals(SignAllCommands.setBackground(true), lastScript())
    }

    @Test
    fun `la cola se serializa con la glosa el tipo y la url`() {
        controller.setQueue(items)
        val script = lastScript().orEmpty()
        assertTrue(script, script.startsWith("window.SignAll?.setQueue(\""))
        // El JSON viaja citado y con los cuatro campos del contrato.
        assertTrue(script, script.contains("gloss"))
        assertTrue(script, script.contains("AFFIRMATIVE"))
        assertTrue(script, script.contains("NEGATIVE"))
        assertTrue(script, script.contains("a.glb"))
        assertTrue(script, script.contains("b.glb"))
        // Dentro del literal de JavaScript las comillas van escapadas.
        assertTrue(script, script.contains("available\\\":false"))
    }

    @Test
    fun `agregar a la cola es otro comando`() {
        controller.appendQueue(items)
        assertTrue(lastScript().orEmpty().startsWith("window.SignAll?.appendQueue("))
    }

    @Test
    fun `una cola vacia no manda nada a la escena`() {
        controller.setQueue(emptyList())
        assertNull(lastScript())
        controller.appendQueue(emptyList())
        assertNull(lastScript())
    }

    @Test
    fun `el desenfoque del canvas se aplica y se quita`() {
        // El `RenderEffect` no se puede leer desde la API pública: lo que se
        // comprueba es que el comando llega a la vista sin romper.
        controller.applyBlur(true)
        controller.applyBlur(false)
        controller.attach(webView)
        controller.applyBlur(true)
    }
}
