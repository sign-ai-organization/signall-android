package com.signall.app.ui

import android.webkit.WebView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import com.signall.app.data.BackendHealth
import com.signall.app.data.SignAllWebSocketClient
import com.signall.app.data.SignAssetRepository
import com.signall.app.data.SignHistoryRepository
import com.signall.app.data.SettingsRepository
import com.signall.app.domain.BackendEvent
import com.signall.app.domain.Settings
import com.signall.app.domain.SignQueueItem
import com.signall.app.testing.ThrottledAudioRecord
import com.signall.app.testing.Themed
import com.signall.app.testing.tap
import com.signall.app.ui.designsystem.components.DrawerSide
import com.signall.app.ui.state.MainScreenState
import com.signall.app.ui.state.PlaybackState
import com.signall.app.ui.webview.SignAllWebViewController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La pantalla completa: escena, panel y los dos drawers armados alrededor de un
 * `MainScreenState` de verdad.
 *
 * El avatar 3D es un `WebView` y en Robolectric se crea pero no dibuja nada; lo
 * que se comprueba es la estructura que lo rodea —splash, panel, drawers,
 * Acerca de—, que es lo que la persona ve y toca.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class MainScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var state: MainScreenState
    private lateinit var webScene: NoopScene
    private lateinit var events: MutableSharedFlow<BackendEvent>

    @Before
    fun setUp() {
        ThrottledAudioRecord.install()
        val backend = mock<SignAllWebSocketClient>()
        val assets = mock<SignAssetRepository>()
        val health = mock<BackendHealth>()
        events = MutableSharedFlow(extraBufferCapacity = 64)
        whenever(backend.events()).thenReturn(events)
        whenever(backend.isConnected).thenReturn(true)
        whenever(backend.sendText(any())).thenReturn(1)
        whenever(backend.sendAudio(any())).thenReturn(true)
        whenever(backend.sendEnd()).thenReturn(true)
        runBlocking {
            whenever(assets.resolve(any(), any())).thenReturn(null)
            whenever(health.silenceTimeoutMs()).thenReturn(1_200)
            whenever(backend.probeVoice()).thenReturn(null)
        }
        webScene = NoopScene()
        state = MainScreenState(MemoryHistory(), MemorySettings(), webScene, backend, assets, health)
    }

    @After
    fun tearDown() {
        ThrottledAudioRecord.clear()
    }

    private fun render() {
        compose.setContent {
            Themed {
                MainScreen(state)
            }
        }
    }

    @Test
    fun `al abrir muestra el splash y despues el panel`() {
        render()
        compose.onNodeWithContentDescription("Cargando…").assertExists()
        // El splash se va solo a los 8 s aunque la escena no avise.
        compose.mainClock.advanceTimeBy(9_000)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Cargando…").assertDoesNotExist()
        compose.onNodeWithContentDescription("Traducir").assertExists()
        compose.onNodeWithContentDescription("Historial reciente").assertExists()
    }

    @Test
    fun `el drawer de conversacion se abre desde el estado`() {
        render()
        compose.mainClock.advanceTimeBy(9_000)
        state.openDrawer(DrawerSide.Start)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Aún no hay frases en esta conversación.").assertExists()
    }

    @Test
    fun `el drawer de ajustes y Acerca de`() {
        render()
        compose.mainClock.advanceTimeBy(9_000)
        state.openDrawer(DrawerSide.End)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("AVATAR").assertExists()
        // Salir de Acerca de vuelve a levantar el splash, porque la escena se
        // vuelve a crear de cero.
        compose.onNodeWithText("Acerca de").tap()
        compose.waitForIdle()
        compose.onNodeWithText("Proyecto de tesis").assertExists()
        compose.onNodeWithContentDescription("Volver").tap()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Cargando…").assertExists()
    }

    @Test
    fun `Traducir manda la frase escrita`() {
        render()
        compose.mainClock.advanceTimeBy(9_000)
        events.tryEmit(BackendEvent.Connected)
        compose.waitForIdle()
        compose.onNodeWithText("Escribe una frase...").performTextReplacement("necesito agua")
        compose.onNodeWithContentDescription("Traducir").tap()
        compose.waitForIdle()
        assertEquals(PlaybackState.Procesando, state.playbackState)
        assertEquals("necesito agua", state.inputText)
    }

    @Test
    fun `el historial se abre y reutiliza una frase`() {
        render()
        compose.mainClock.advanceTimeBy(9_000)
        compose.onNodeWithContentDescription("Historial reciente").tap()
        compose.waitForIdle()
        compose.onNodeWithText("Aún no hay frases recientes.").assertExists()
        // Los dos drawers también traen su «Cerrar»; el de la hoja es el último.
        compose.onAllNodesWithContentDescription("Cerrar").onLast().tap()
        compose.waitForIdle()
        compose.onNodeWithText("TUS FRASES").assertDoesNotExist()
    }

    @Test
    fun `sin permiso de microfono el panel lo explica`() {
        render()
        compose.mainClock.advanceTimeBy(9_000)
        compose.waitForIdle()
        // Robolectric arranca sin RECORD_AUDIO concedido: la pantalla pide el
        // permiso una vez y, al negarse, el panel muestra el motivo.
        assertTrue(state.statusLine.isNotBlank())
    }

    private class MemoryHistory : SignHistoryRepository {
        private val items = mutableListOf<String>()

        override fun load(): List<String> = items.toList()

        override fun record(text: String): List<String> {
            if (text.isNotBlank()) {
                items.remove(text)
                items.add(0, text)
            }
            return items.toList()
        }
    }

    private class MemorySettings : SettingsRepository {
        var saved: Settings = Settings()

        override fun load(): Settings = saved

        override fun save(settings: Settings) {
            saved = settings
        }
    }

    private class NoopScene : SignAllWebViewController {
        override fun attach(webView: WebView) = Unit

        override fun play() = Unit

        override fun setQueue(items: List<SignQueueItem>) = Unit

        override fun appendQueue(items: List<SignQueueItem>) = Unit

        override fun playFrom(index: Int) = Unit

        override fun stop() = Unit

        override fun setDefaultSign(url: String) = Unit

        override fun setAvatar(id: String) = Unit

        override fun setBackground(on: Boolean) = Unit

        override fun setGrid(on: Boolean) = Unit

        override fun setFreeCamera(on: Boolean) = Unit

        override fun setDebug(on: Boolean) = Unit

        override fun setReduceMotion(on: Boolean) = Unit

        override fun setSpeed(factor: Float) = Unit

        override fun setTheme(dark: Boolean) = Unit

        override fun setSilenceTimeoutMs(ms: Int?) = Unit

        override fun applyBlur(on: Boolean) = Unit
    }
}
