package com.signall.app.ui.state

import android.webkit.WebView
import com.signall.app.data.BackendHealth
import com.signall.app.data.SignAllWebSocketClient
import com.signall.app.data.SignAssetRepository
import com.signall.app.data.SignHistoryRepository
import com.signall.app.data.SettingsRepository
import com.signall.app.domain.BackendEvent
import com.signall.app.domain.Character
import com.signall.app.domain.Setting
import com.signall.app.domain.Settings
import com.signall.app.domain.SignQueueItem
import com.signall.app.ui.designsystem.components.DrawerSide
import com.signall.app.ui.webview.SignAllWebViewController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La máquina de estados es el corazón de la app: traduce frames del backend en
 * lo que el usuario ve. Se prueba con dobles de las tres dependencias que hablan
 * por red o con el WebView, y con un backend falso que no abre socket.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainScreenStateTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var history: FakeHistory
    private lateinit var settings: FakeSettings
    private lateinit var webView: FakeWebView
    private lateinit var backend: SignAllWebSocketClient
    private lateinit var assets: SignAssetRepository
    private lateinit var health: BackendHealth
    private lateinit var events: MutableSharedFlow<BackendEvent>
    private lateinit var state: MainScreenState

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        history = FakeHistory()
        settings = FakeSettings()
        webView = FakeWebView()
        backend = mock()
        assets = mock()
        health = mock()
        events = MutableSharedFlow(extraBufferCapacity = 64)
        whenever(backend.events()).thenReturn(events)
        whenever(backend.isConnected).thenReturn(true)
        whenever(backend.sendText(any())).thenReturn(1)
        whenever(backend.sendAudio(any())).thenReturn(true)
        whenever(backend.sendEnd()).thenReturn(true)
        runBlocking {
            whenever(assets.resolve(any(), any())).thenReturn(null)
            whenever(health.silenceTimeoutMs()).thenReturn(1_500)
        }
        state = MainScreenState(history, settings, webView, backend, assets, health)
        dispatcher.scheduler.runCurrent()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun dispatch(event: BackendEvent) {
        events.tryEmit(event)
        dispatcher.scheduler.runCurrent()
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun `al arrancar sin sesion el estado es desconectado`() {
        assertEquals(PlaybackState.Desconectado, state.playbackState)
    }

    @Test
    fun `una sesion conectada vuelve a inactivo`() {
        dispatch(BackendEvent.Connected)
        assertEquals(PlaybackState.Inactivo, state.playbackState)
    }

    @Test
    fun `una desconexion deja el estado en desconectado`() {
        dispatch(BackendEvent.Connected)
        dispatch(BackendEvent.Disconnected)
        assertEquals(PlaybackState.Desconectado, state.playbackState)
    }

    @Test
    fun `sin texto esperando el estado que ve el usuario es el de la maquina`() {
        dispatch(BackendEvent.Connected)
        assertEquals(PlaybackState.Inactivo, state.stateForUi)
        assertEquals("Inactivo", state.stateLabel)
        assertEquals("Inactivo", state.statusLine)
    }

    @Test
    fun `las capacidades apagan la voz sin tumbar la sesion`() {
        dispatch(BackendEvent.Connected)
        dispatch(BackendEvent.Capabilities(audio = false))
        assertFalse(state.sttAvailable)
        assertTrue(state.backendConnected)
    }

    @Test
    fun `las capacidades con voz dejan el servicio disponible`() {
        dispatch(BackendEvent.Connected)
        dispatch(BackendEvent.Capabilities(audio = true))
        assertTrue(state.sttAvailable)
    }

    @Test
    fun `un cambio de ajuste se persiste y llega a la escena`() {
        state.toggle(Setting.DarkTheme, false)
        assertFalse(state.settings.darkTheme)
        assertEquals(false, settings.saved.darkTheme)
        assertEquals(false, webView.lastTheme)
    }

    @Test
    fun `el personaje se persiste al elegirlo`() {
        state.setCharacter(Character.Pauline)
        assertEquals(Character.Pauline, state.settings.character)
        assertEquals(Character.Pauline, settings.saved.character)
        assertTrue(webView.avatarCalls.contains("pauline"))
    }

    @Test
    fun `elegir la sena de respaldo la guarda como actual`() {
        val id = state.signs[5]
        state.pickSign(id)
        assertEquals(id, state.sign)
        assertEquals("signs/$id.glb", webView.lastDefaultSign)
    }

    @Test
    fun `elegir la misma sena dos veces no vuelve a avisar a la escena`() {
        val id = state.signs[5]
        state.pickSign(id)
        webView.lastDefaultSign = null
        state.pickSign(id)
        assertNull(webView.lastDefaultSign)
    }

    @Test
    fun `la velocidad se recorta al rango valido`() {
        state.setSpeed(999)
        assertEquals(100, state.settings.speed)
        state.setSpeed(-40)
        assertEquals(10, state.settings.speed)
    }

    @Test
    fun `el tamano de interfaz se recorta al rango valido`() {
        state.setInterfaceSize(999)
        assertEquals(100, state.settings.interfaceSize)
        state.setInterfaceSize(-1)
        assertEquals(0, state.settings.interfaceSize)
    }

    @Test
    fun `abrir y cerrar un drawer cambia el panel abierto`() {
        assertNull(state.openDrawer)
        state.openDrawer(DrawerSide.Start)
        assertEquals(DrawerSide.Start, state.openDrawer)
        state.closeDrawer()
        assertNull(state.openDrawer)
    }

    @Test
    fun `el segundo dedo no se queda con el gesto de un drawer abierto`() {
        assertTrue(state.claimDrawerGesture(DrawerSide.Start))
        assertFalse(state.claimDrawerGesture(DrawerSide.End))
        // Reclamar el mismo lado sí puede (es el mismo dedo siguiendo).
        assertTrue(state.claimDrawerGesture(DrawerSide.Start))
        state.releaseDrawerGesture(DrawerSide.Start)
        assertTrue(state.claimDrawerGesture(DrawerSide.End))
    }

    @Test
    fun `el texto del campo se puede escribir`() {
        state.setInput("hola")
        assertEquals("hola", state.inputText)
        // Borrarlo deja el campo vacío: nada queda retenido.
        state.setInput("")
        assertEquals("", state.inputText)
    }

    @Test
    fun `una escena muerta deja el estado en error y no se repite`() {
        dispatch(BackendEvent.Connected)
        state.onSceneFailed("se cayó")
        assertEquals(PlaybackState.Error, state.playbackState)
        assertFalse(state.canSwitchAvatar)
        // El segundo aviso no dispara otro parpadeo.
        state.onSceneFailed("otra vez")
        assertEquals(PlaybackState.Error, state.playbackState)
    }

    @Test
    fun `negar el permiso del microfono deja un aviso visible`() {
        state.onMicPermissionDenied()
        assertTrue(state.statusLine.isNotBlank())
        assertTrue(state.statusLine != state.stateLabel)
    }

    @Test
    fun `faltar el permiso al arrancar deja un aviso visible`() {
        state.onMicPermissionMissingAtLaunch()
        assertTrue(state.statusLine.isNotBlank())
        assertTrue(state.statusLine != state.stateLabel)
    }

    @Test
    fun `una frase vacia no se manda`() {
        state.play("   ")
        assertEquals(PlaybackState.Desconectado, state.playbackState)
    }

    @Test
    fun `el dictado es un interruptor`() {
        // El permiso lo pide la interfaz; el estado solo cambia de modo.
        assertEquals(MicMode.Off, state.micMode)
        state.toggleDictation()
        assertEquals(MicMode.Dictation, state.micMode)
        state.toggleDictation()
        assertEquals(MicMode.Off, state.micMode)
    }

    @Test
    fun `el modo en vivo y el dictado no pueden estar prendidos juntos`() {
        state.toggleDictation()
        state.toggleLive()
        assertEquals(MicMode.Live, state.micMode)
        assertFalse(state.dictationActive)
        assertTrue(state.liveActive)
    }

    private class FakeHistory : SignHistoryRepository {
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

    private class FakeSettings : SettingsRepository {
        var saved: Settings = Settings()
        override fun load(): Settings = saved
        override fun save(settings: Settings) {
            saved = settings
        }
    }

    private class FakeWebView : SignAllWebViewController {
        val avatarCalls = mutableListOf<String>()
        var lastTheme: Boolean? = null
        var lastDefaultSign: String? = null
        override fun attach(webView: WebView) = Unit
        override fun play() = Unit
        override fun setQueue(items: List<SignQueueItem>) = Unit
        override fun appendQueue(items: List<SignQueueItem>) = Unit
        override fun playFrom(index: Int) = Unit
        override fun stop() = Unit
        override fun setDefaultSign(url: String) {
            lastDefaultSign = url
        }
        override fun setAvatar(id: String) {
            avatarCalls.add(id)
        }
        override fun setBackground(on: Boolean) = Unit
        override fun setGrid(on: Boolean) = Unit
        override fun setFreeCamera(on: Boolean) = Unit
        override fun setDebug(on: Boolean) = Unit
        override fun setReduceMotion(on: Boolean) = Unit
        override fun setSpeed(factor: Float) = Unit
        override fun setTheme(dark: Boolean) {
            lastTheme = dark
        }
        override fun setSilenceTimeoutMs(ms: Int?) = Unit
        override fun applyBlur(on: Boolean) = Unit
    }
}
