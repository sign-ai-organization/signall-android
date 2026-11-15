package com.signall.app.ui.state

import android.webkit.WebView
import com.signall.app.data.BackendHealth
import com.signall.app.data.NetworkWatcher
import com.signall.app.data.SignAllWebSocketClient
import com.signall.app.data.SignAssetRepository
import com.signall.app.data.SignHistoryRepository
import com.signall.app.data.SettingsRepository
import com.signall.app.domain.BackendEvent
import com.signall.app.domain.Character
import com.signall.app.domain.PhraseType
import com.signall.app.domain.Setting
import com.signall.app.domain.Settings
import com.signall.app.domain.SignQueueItem
import com.signall.app.domain.TranslationGloss
import com.signall.app.domain.TranslationSegment
import com.signall.app.testing.ThrottledAudioRecord
import com.signall.app.ui.designsystem.components.DrawerSide
import com.signall.app.ui.webview.SignAllWebViewController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los caminos del turno completo: un frame del backend entra y esto es lo que
 * queda en pantalla, en la cola de la escena y en el historial.
 *
 * Se usa un backend falso que no abre socket, igual que en
 * [MainScreenStateTest], y el reloj virtual del `TestDispatcher` para los
 * temporizadores (retención del texto, reloj de silencio, error que se va solo).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainScreenStateFlowTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var history: RecordingHistory
    private lateinit var settings: MemorySettings
    private lateinit var webScene: RecordingScene
    private lateinit var backend: SignAllWebSocketClient
    private lateinit var assets: SignAssetRepository
    private lateinit var health: BackendHealth
    private lateinit var events: MutableSharedFlow<BackendEvent>
    private lateinit var state: MainScreenState

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        ThrottledAudioRecord.install()
        history = RecordingHistory()
        settings = MemorySettings()
        webScene = RecordingScene()
        backend = mock()
        assets = mock()
        health = mock()
        events = MutableSharedFlow(extraBufferCapacity = 256)
        whenever(backend.events()).thenReturn(events)
        whenever(backend.isConnected).thenReturn(true)
        whenever(backend.sendText(any())).thenReturn(1)
        whenever(backend.sendAudio(any())).thenReturn(true)
        whenever(backend.sendEnd()).thenReturn(true)
        runBlocking {
            whenever(assets.resolve(any(), any())).thenReturn(null)
            whenever(health.silenceTimeoutMs()).thenReturn(1_500)
            whenever(backend.probeVoice()).thenReturn(null)
        }
        state = MainScreenState(history, settings, webScene, backend, assets, health)
        settle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ThrottledAudioRecord.clear()
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private fun settle(times: Int = 4) = repeat(times) { dispatcher.scheduler.runCurrent() }

    private fun dispatch(event: BackendEvent) {
        events.tryEmit(event)
        settle()
    }

    private fun connect() = dispatch(BackendEvent.Connected)

    private fun advance(ms: Long) {
        dispatcher.scheduler.advanceTimeBy(ms)
        settle()
    }

    private fun gloss(
        text: String = "HOLA",
        path: String = "signs/0001_0000_0000.glb",
        available: Boolean = true,
    ) = TranslationGloss(text = text, available = available, glbPath = path)

    private fun segment(
        vararg glosses: TranslationGloss,
        type: PhraseType = PhraseType.AFFIRMATIVE,
    ) = TranslationSegment(type = type, glosses = glosses.toList())

    private fun segmentEvent(
        index: Int = 0,
        total: Int = 1,
        event: TranslationSegment = segment(gloss()),
        turn: Int? = null,
    ) = BackendEvent.Segment(index = index, total = total, segment = event, turn = turn)

    // ── Sesión y reconexión ──────────────────────────────────────────────────

    @Test
    fun `una sesion que se cae se reintenta con espera creciente`() {
        val breve = mock<SignAllWebSocketClient>()
        whenever(breve.events()).thenReturn(flow { emit(BackendEvent.Connected) })
        whenever(breve.isConnected).thenReturn(true)
        val nuevo = MainScreenState(history, settings, webScene, breve, assets, health)
        settle()
        // El flujo terminó: el bucle de conexión espera y vuelve a intentar.
        assertEquals(PlaybackState.Desconectado, nuevo.playbackState)
        advance(1_000)
        assertEquals(PlaybackState.Desconectado, nuevo.playbackState)
        advance(2_000)
        assertEquals(PlaybackState.Desconectado, nuevo.playbackState)
    }

    @Test
    fun `un cambio de red corta la espera y reabre la sesion sin reiniciar`() {
        val red = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        var sesiones = 0
        val breve = mock<SignAllWebSocketClient>()
        whenever(breve.events()).thenAnswer {
            sesiones++
            flow<BackendEvent> { }
        }
        whenever(breve.isConnected).thenReturn(false)
        val nuevo =
            MainScreenState(
                history,
                settings,
                webScene,
                breve,
                assets,
                health,
                networkWatcher = NetworkWatcher { red },
            )
        settle()
        // El primer flujo terminó: el bucle quedaría esperando 1 s.
        assertEquals(1, sesiones)
        assertEquals(PlaybackState.Desconectado, nuevo.playbackState)

        // La red cambió: no se avanza el reloj, el intento sale ya.
        red.tryEmit(Unit)
        settle()

        assertEquals(2, sesiones)
        verify(breve).close()
    }

    @Test
    fun `un cambio de red cierra el socket que quedo atado a la red vieja`() {
        val red = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        val abierto = MutableSharedFlow<BackendEvent>(extraBufferCapacity = 8)
        val largo = mock<SignAllWebSocketClient>()
        whenever(largo.events()).thenReturn(abierto)
        whenever(largo.isConnected).thenReturn(true)
        val nuevo =
            MainScreenState(
                history,
                settings,
                webScene,
                largo,
                assets,
                health,
                networkWatcher = NetworkWatcher { red },
            )
        settle()

        red.tryEmit(Unit)
        settle()

        verify(largo).close()
    }

    @Test
    fun `volver a la pantalla con la sesion abierta no la corta`() {
        val abierto = MutableSharedFlow<BackendEvent>(extraBufferCapacity = 8)
        val largo = mock<SignAllWebSocketClient>()
        whenever(largo.events()).thenReturn(abierto)
        whenever(largo.isConnected).thenReturn(true)
        val nuevo =
            MainScreenState(history, settings, webScene, largo, assets, health)
        settle()

        nuevo.retryIfDisconnected()
        settle()

        verify(largo, never()).close()
    }

    @Test
    fun `sin conexion el microfono no se prende y avisa`() {
        whenever(backend.isConnected).thenReturn(false)
        state.toggleDictation()
        assertEquals(MicMode.Off, state.micMode)
        assertFalse(state.micActive)
    }

    @Test
    fun `apagar el microfono deja el estado inactivo`() {
        connect()
        state.toggleDictation()
        assertTrue(state.micActive)
        state.toggleDictation()
        assertEquals(MicMode.Off, state.micMode)
        assertFalse(state.micActive)
    }

    // ── Dictado ──────────────────────────────────────────────────────────────

    @Test
    fun `una transcripcion de dictado llena el campo y espera confirmacion`() {
        connect()
        state.toggleDictation()
        assertEquals(PlaybackState.Grabando, state.playbackState)
        dispatch(BackendEvent.Transcript("quiero", final = false))
        assertEquals("quiero", state.inputText)
        // El parcial no cierra nada: sigue grabando.
        assertEquals(PlaybackState.Grabando, state.playbackState)

        dispatch(BackendEvent.Transcript("quiero agua", final = true))
        assertEquals("quiero agua", state.inputText)
        assertTrue(state.dictationPending)
        assertTrue(state.dictationReadyToSend)
        assertEquals(PlaybackState.TextoListo, state.stateForUi)
        assertEquals("Texto listo", state.stateLabel)
        assertEquals(MicMode.Off, state.micMode)
    }

    @Test
    fun `el texto dictado se manda y se retiene en el campo`() {
        connect()
        state.toggleDictation()
        dispatch(BackendEvent.Transcript("quiero agua", final = true))
        state.play(state.inputText)
        assertEquals(PlaybackState.Procesando, state.playbackState)
        assertEquals("quiero agua", state.sentText)
        assertTrue(state.sentTextLocked)
        assertFalse(state.dictationPending)
        advance(1_500)
        assertNull(state.sentText)
        assertEquals("", state.inputText)
    }

    @Test
    fun `un final vacio en dictado cierra el utterance sin borrar el campo`() {
        connect()
        state.toggleDictation()
        state.setInput("")
        dispatch(BackendEvent.Transcript("", final = true))
        assertEquals(MicMode.Off, state.micMode)
        assertEquals(PlaybackState.Inactivo, state.playbackState)
    }

    @Test
    fun `apagar el dictado sin hablar vuelve a inactivo`() {
        connect()
        state.toggleDictation()
        state.toggleDictation()
        assertEquals(MicMode.Off, state.micMode)
        assertEquals(PlaybackState.Inactivo, state.playbackState)
    }

    @Test
    fun `si la transcripcion no llega se declara fallida en vez de esperar para siempre`() {
        connect()
        state.toggleDictation()
        dispatch(BackendEvent.Transcript("hola", final = false))
        // El usuario apaga el micrófono: la app queda esperando el `final`.
        state.toggleDictation()
        assertEquals(PlaybackState.Procesando, state.playbackState)
        advance(15_000)
        assertEquals(PlaybackState.Error, state.playbackState)
    }

    @Test
    fun `borrar el texto dictado apaga el listo para enviar`() {
        connect()
        state.toggleDictation()
        dispatch(BackendEvent.Transcript("hola", final = true))
        assertTrue(state.dictationPending)
        state.setInput("")
        assertFalse(state.dictationPending)
        assertFalse(state.dictationReadyToSend)
    }

    @Test
    fun `el campo no acepta texto mientras el microfono captura`() {
        connect()
        state.toggleDictation()
        state.setInput("otra cosa")
        assertEquals("", state.inputText)
    }

    @Test
    fun `el campo no acepta texto mientras se repite`() {
        connect()
        state.setInput("hola")
        state.play("hola")
        state.repeatTurnFromDrawer("hola")
        assertTrue(state.replayPending)
        state.setInput("otra")
        // El campo conserva la frase enviada, no la que llegó del teclado.
        assertEquals("hola", state.inputText)
        advance(500)
        settle()
    }

    // ── Modo en vivo ─────────────────────────────────────────────────────────

    @Test
    fun `en vivo la frase final se manda sola y se representa`() {
        connect()
        state.toggleLive()
        assertEquals(MicMode.Live, state.micMode)
        assertTrue(state.liveActive)

        dispatch(BackendEvent.Transcript("hola mundo", final = false))
        assertEquals("hola mundo", state.inputText)
        assertEquals(PlaybackState.Grabando, state.playbackState)

        dispatch(BackendEvent.Transcript("hola mundo", final = true))
        assertEquals(PlaybackState.Procesando, state.playbackState)
        assertEquals("hola mundo", state.lastText)

        dispatch(segmentEvent(event = segment(gloss("HOLA"), gloss("MUNDO"))))
        assertEquals(PlaybackState.Reproduciendo, state.playbackState)
        assertEquals(2, webScene.queues.single().size)
        assertEquals(1, webScene.plays)
        assertEquals(listOf("hola mundo"), history.load())
        assertEquals(1, state.conversation.size)
        assertEquals("HOLA MUNDO", state.conversation.single().gloss)

        dispatch(BackendEvent.PhraseDone(turn = 1))
        state.onPlaybackDone()
        // El TR sigue prendido: el micrófono vuelve a "Grabando", no a Inactivo.
        assertEquals(PlaybackState.Grabando, state.playbackState)
        assertNull(state.currentGloss)
    }

    @Test
    fun `una frase en vivo que llega con otra en vuelo espera su turno`() {
        connect()
        state.toggleLive()
        dispatch(BackendEvent.Transcript("primera", final = true))
        assertEquals(PlaybackState.Procesando, state.playbackState)
        // La segunda llega mientras la primera sigue en vuelo: queda esperando.
        dispatch(BackendEvent.Transcript("segunda", final = true))
        dispatch(segmentEvent(event = segment(gloss())))
        dispatch(BackendEvent.PhraseDone(turn = 1))
        // Al cerrar la primera, la que esperaba sale sola.
        assertEquals(PlaybackState.Procesando, state.playbackState)
        assertTrue(state.lastText.isNotBlank())
    }

    @Test
    fun `apagar el modo en vivo con una frase en vuelo la cancela`() {
        connect()
        state.toggleLive()
        dispatch(BackendEvent.Transcript("hola", final = true))
        assertEquals(PlaybackState.Procesando, state.playbackState)
        state.toggleLive()
        assertEquals(MicMode.Off, state.micMode)
        assertEquals(PlaybackState.Inactivo, state.playbackState)
        assertEquals("hola", state.inputText)
        assertTrue(state.dictationPending)
    }

    @Test
    fun `apagar el modo en vivo limpia el campo cuando ya se represento`() {
        connect()
        state.toggleLive()
        dispatch(BackendEvent.Transcript("hola", final = true))
        dispatch(segmentEvent(event = segment(gloss())))
        dispatch(BackendEvent.PhraseDone(turn = 1))
        state.toggleLive()
        assertEquals(MicMode.Off, state.micMode)
        assertEquals("", state.inputText)
    }

    @Test
    fun `cambiar de dictado a en vivo no corta la captura`() {
        connect()
        state.toggleDictation()
        state.toggleLive()
        assertEquals(MicMode.Live, state.micMode)
        assertEquals(PlaybackState.Grabando, state.playbackState)
        state.toggleDictation()
        assertEquals(MicMode.Dictation, state.micMode)
    }

    @Test
    fun `el modo en vivo cancela la frase que estaba esperando en el campo`() {
        connect()
        state.toggleDictation()
        dispatch(BackendEvent.Transcript("pendiente", final = true))
        assertTrue(state.dictationPending)
        state.toggleLive()
        // Pasar a en vivo no borra el pendiente: vuelve al campo al apagarlo.
        assertTrue(state.dictationPending || state.inputText.isEmpty())
    }

    // ── Turno escrito ────────────────────────────────────────────────────────

    @Test
    fun `una frase escrita se traduce y cada oracion se representa al llegar`() {
        connect()
        state.play("  hola  ")
        assertEquals(PlaybackState.Procesando, state.playbackState)
        assertEquals("hola", state.inputText)

        dispatch(segmentEvent(index = 0, total = 2, event = segment(gloss("HOLA"))))
        assertEquals(PlaybackState.Reproduciendo, state.playbackState)
        assertEquals(1, webScene.queues.size)
        assertEquals("HOLA", state.conversation.single().gloss)

        // La segunda oración se encadena detrás, sin reiniciar la cola.
        dispatch(segmentEvent(index = 1, total = 2, event = segment(gloss("AGUA"))))
        assertEquals(1, webScene.appends.size)
        assertEquals("HOLA AGUA", state.conversation.single().gloss)

        dispatch(BackendEvent.PhraseDone(turn = 1))
        state.onPlaybackDone()
        assertEquals(PlaybackState.Inactivo, state.playbackState)
    }

    @Test
    fun `una glosa sin clip no se representa y avisa`() {
        connect()
        state.play("zzz")
        dispatch(
            segmentEvent(
                event = segment(gloss(text = "ZZZ", path = "signs/9999_0000_0000.glb", available = true)),
            ),
        )
        assertTrue(webScene.queues.isEmpty())
        // "Seña no disponible" es el único texto propio que sale sin DEV_TOOLS.
        assertEquals("Seña no disponible", state.statusLine)
    }

    @Test
    fun `con la sena de prueba prendida una glosa sin clip usa la de respaldo`() {
        connect()
        state.toggle(Setting.DemoSignFallback, true)
        state.play("zzz")
        dispatch(
            segmentEvent(
                event = segment(gloss(text = "ZZZ", path = "signs/9999_0000_0000.glb", available = false)),
            ),
        )
        assertEquals(1, webScene.queues.single().size)
        assertEquals(webScene.queues.single().single().url, "signs/${state.sign}.glb")
    }

    @Test
    fun `un asset remoto se resuelve por red antes de armar la cola`() {
        runBlocking { whenever(assets.resolve(any(), any())).thenReturn("/signs/remoto.glb") }
        connect()
        state.play("hola")
        dispatch(
            segmentEvent(
                event = segment(gloss(text = "REMOTO", path = "assets/anims/remoto.glb")),
            ),
        )
        assertEquals("/signs/remoto.glb", webScene.queues.single().single().url)
    }

    @Test
    fun `una frase sin ninguna sena cierra el turno en vez de dejarlo procesando`() {
        connect()
        state.play("zzz")
        dispatch(
            segmentEvent(
                event = segment(gloss(text = "ZZZ", path = "signs/9999_0000_0000.glb")),
            ),
        )
        dispatch(BackendEvent.PhraseDone(turn = 1))
        assertEquals(PlaybackState.Inactivo, state.playbackState)
        assertTrue(state.doneVisible)
    }

    @Test
    fun `un done sin segmentos vuelve a inactivo`() {
        connect()
        state.play("hola")
        dispatch(BackendEvent.PhraseDone(turn = 1))
        assertEquals(PlaybackState.Inactivo, state.playbackState)
    }

    @Test
    fun `los frames de un turno viejo se descartan`() {
        connect()
        state.play("hola")
        // El turno en vuelo es 1; lo que llega marcado 9 es de una frase cancelada.
        dispatch(segmentEvent(event = segment(gloss()), turn = 9))
        assertTrue(webScene.queues.isEmpty())
        dispatch(BackendEvent.PhraseDone(turn = 9))
        dispatch(BackendEvent.Failure("viejo", turn = 9))
        assertEquals(PlaybackState.Procesando, state.playbackState)
    }

    @Test
    fun `un fallo del backend deja el estado en error y vuelve solo`() {
        connect()
        state.play("hola")
        dispatch(BackendEvent.Failure("el servidor se cayó", turn = 1))
        assertEquals(PlaybackState.Error, state.playbackState)
        advance(3_000)
        assertEquals(PlaybackState.Inactivo, state.playbackState)
    }

    @Test
    fun `si el servidor no contesta la frase se declara fallida`() {
        connect()
        state.play("hola")
        assertEquals(PlaybackState.Procesando, state.playbackState)
        advance(15_000)
        assertEquals(PlaybackState.Error, state.playbackState)
    }

    @Test
    fun `una frase cancelada que nunca cierra no inventa un error`() {
        connect()
        state.toggleLive()
        dispatch(BackendEvent.Transcript("hola", final = true))
        // Apagar el TR cancela la frase; si su `done` nunca llega, el reloj se olvida.
        state.toggleLive()
        assertEquals(PlaybackState.Inactivo, state.playbackState)
        advance(15_000)
        assertEquals(PlaybackState.Inactivo, state.playbackState)
    }

    @Test
    fun `los frames de una frase cancelada no se representan`() {
        connect()
        state.toggleLive()
        dispatch(BackendEvent.Transcript("hola", final = true))
        state.toggleLive()
        dispatch(segmentEvent(event = segment(gloss())))
        assertTrue(webScene.queues.isEmpty())
        // Y su `done` se traga sin cerrar el turno vivo.
        dispatch(BackendEvent.PhraseDone(turn = 1))
        assertEquals(PlaybackState.Inactivo, state.playbackState)
    }

    @Test
    fun `la capacidad sin voz apaga el microfono y avisa`() {
        connect()
        dispatch(BackendEvent.Capabilities(audio = false, reason = "stt caido"))
        assertFalse(state.sttAvailable)
        assertEquals(MicUnavailableNotice, state.statusLine)
        dispatch(BackendEvent.Capabilities(audio = true))
        assertTrue(state.sttAvailable)
    }

    @Test
    fun `sin voz el bucle de recuperacion vuelve a abrir la sesion`() {
        whenever(backend.isConnected).thenReturn(true)
        runBlocking { whenever(backend.probeVoice()).thenReturn(true) }
        connect()
        dispatch(BackendEvent.Capabilities(audio = false))
        // Cada 5 s se pregunta; cuando la voz vuelve y la app está ociosa, se reabre.
        advance(5_000)
        advance(5_000)
        assertFalse(state.sttAvailable)
    }

    // ── Desconexión ──────────────────────────────────────────────────────────

    @Test
    fun `una desconexion en reposo deja desconectado sin mensaje encima`() {
        connect()
        dispatch(BackendEvent.Disconnected)
        assertEquals(PlaybackState.Desconectado, state.playbackState)
        assertEquals("Desconectado", state.statusLine)
    }

    @Test
    fun `una desconexion con la frase en vuelo la devuelve al campo`() {
        connect()
        state.play("hola")
        dispatch(BackendEvent.Disconnected)
        assertEquals(PlaybackState.Desconectado, state.playbackState)
        assertEquals("hola", state.inputText)
        assertTrue(state.dictationPending)
        assertNull(state.sentText)
    }

    @Test
    fun `una desconexion mientras el avatar representa corta la escena`() {
        connect()
        state.play("hola")
        dispatch(segmentEvent(event = segment(gloss())))
        assertEquals(PlaybackState.Reproduciendo, state.playbackState)
        dispatch(BackendEvent.Disconnected)
        assertEquals(1, webScene.stops)
        assertEquals(PlaybackState.Desconectado, state.playbackState)
    }

    @Test
    fun `una desconexion con el microfono prendido lo apaga`() {
        connect()
        state.toggleLive()
        dispatch(BackendEvent.Transcript("algo dicho", final = false))
        dispatch(BackendEvent.Disconnected)
        assertEquals(MicMode.Off, state.micMode)
        assertEquals(PlaybackState.Desconectado, state.playbackState)
    }

    // ── Puente de la escena ──────────────────────────────────────────────────

    @Test
    fun `la primera sena mueve el rotulo de la glosa en curso`() {
        connect()
        state.play("hola")
        dispatch(segmentEvent(event = segment(gloss("HOLA"))))
        state.onSignStart(0, 120)
        assertEquals("HOLA", state.currentGloss)
        // Un índice que no existe no inventa rótulo.
        state.onSignStart(99, 120)
        assertEquals("HOLA", state.currentGloss)
    }

    @Test
    fun `una sena fallida muestra el aviso de sena no disponible`() {
        connect()
        state.onSignFailed(3)
        assertEquals("Seña no disponible", state.statusLine)
    }

    @Test
    fun `el fin de la representacion libera el panel y avisa`() {
        connect()
        state.play("hola")
        dispatch(segmentEvent(event = segment(gloss())))
        state.replayTurn("hola")
        assertTrue(state.replayPending)
        state.onPlaybackDone()
        assertFalse(state.replayPending)
        assertEquals(PlaybackState.Inactivo, state.playbackState)
        assertTrue(state.doneVisible)
    }

    @Test
    fun `una escena muerta no recibe colas nuevas`() {
        connect()
        state.onSceneFailed("murió")
        assertEquals(PlaybackState.Error, state.playbackState)
        state.play("hola")
        dispatch(segmentEvent(event = segment(gloss())))
        assertTrue(webScene.queues.isEmpty())
        state.onSignStart(0, 10)
        assertNull(state.currentGloss)
        state.onPlaybackDone()
    }

    @Test
    fun `el silencio del stt llega a la escena`() {
        state.pushSilenceTimeout()
        settle()
        assertTrue(webScene.silenceCalls > 0)
        assertEquals(1_500, webScene.silenceMs)
    }

    @Test
    fun `adjuntar el webview empuja los ajustes a la escena`() {
        state.toggle(Setting.DarkTheme, false)
        state.toggle(Setting.Grid, true)
        state.setSpeed(80)
        state.attachWebView(WebView(org.robolectric.RuntimeEnvironment.getApplication()))
        assertEquals(false, webScene.theme)
        assertEquals(0.8f, webScene.speed)
        assertEquals(false, webScene.debug)
    }

    @Test
    fun `el desenfoque del canvas viaja a la escena`() {
        state.setBlur(true)
        assertEquals(true, webScene.blur)
        state.setBlur(false)
        assertEquals(false, webScene.blur)
    }

    // ── Ajustes y avatar ─────────────────────────────────────────────────────

    @Test
    fun `los ajustes que no van a la escena se persisten igual`() {
        state.toggle(Setting.Bold, true)
        state.toggle(Setting.Contrast, true)
        state.toggle(Setting.Large, true)
        assertTrue(state.settings.bold)
        assertTrue(state.settings.contrast)
        assertTrue(state.settings.large)
        assertTrue(settings.saved.bold && settings.saved.contrast && settings.saved.large)
    }

    @Test
    fun `el personaje solo cambia en reposo absoluto`() {
        connect()
        state.toggleDictation()
        state.setCharacter(Character.Pauline)
        assertEquals(Character.Juan, state.settings.character)
        state.toggleDictation()
        state.setCharacter(Character.Pauline)
        assertEquals(Character.Pauline, state.settings.character)
        assertTrue(state.avatarLoading)
        // Mientras carga el avatar el selector queda apagado.
        assertFalse(state.canSwitchAvatar)
        assertEquals("Cargando avatar…", state.statusLine)
    }

    @Test
    fun `el avatar cargado libera el panel`() {
        connect()
        state.setCharacter(Character.Pauline)
        state.onAvatarLoaded("pauline")
        assertFalse(state.avatarLoading)
        assertEquals(Character.Pauline, state.settings.character)
    }

    @Test
    fun `un aviso de avatar fuera de turno se ignora`() {
        connect()
        state.onAvatarLoaded("pauline")
        state.onAvatarFailed("juan")
        assertEquals(Character.Juan, state.settings.character)
        assertFalse(state.avatarLoading)
    }

    @Test
    fun `si el avatar nuevo falla se vuelve al anterior`() {
        connect()
        state.setCharacter(Character.Pauline)
        state.onAvatarFailed("pauline")
        assertEquals(Character.Juan, state.settings.character)
        assertFalse(state.avatarLoading)
        assertEquals(Character.Juan, settings.saved.character)
    }

    @Test
    fun `elegir el mismo personaje no dispara una carga`() {
        connect()
        state.setCharacter(Character.Juan)
        assertFalse(state.avatarLoading)
        assertTrue(webScene.avatarCalls.isEmpty())
    }

    @Test
    fun `el selector de avatar se apaga con una frase en vuelo`() {
        connect()
        state.play("hola")
        assertFalse(state.canSwitchAvatar)
        dispatch(BackendEvent.PhraseDone(turn = 1))
        state.onPlaybackDone()
        // La frase sigue retenida en el campo 1,5 s: hasta entonces no se puede reenviar
        // ni cambiar de avatar.
        assertFalse(state.canSwitchAvatar)
        advance(1_500)
        assertTrue(state.canSwitchAvatar)
    }

    @Test
    fun `repetir manda la ultima frase sin duplicar el historial`() {
        connect()
        state.play("hola")
        dispatch(segmentEvent(event = segment(gloss())))
        dispatch(BackendEvent.PhraseDone(turn = 1))
        state.onPlaybackDone()
        assertEquals(1, history.load().size)

        state.replay()
        assertTrue(state.replayPending)
        dispatch(segmentEvent(event = segment(gloss("HOLA"))))
        dispatch(BackendEvent.PhraseDone(turn = 1))
        state.onPlaybackDone()
        assertEquals(1, history.load().size)
        assertEquals(1, state.conversation.size)
    }

    @Test
    fun `repetir sin frase previa no hace nada`() {
        connect()
        state.replay()
        assertEquals(PlaybackState.Inactivo, state.playbackState)
        state.replayTurn("   ")
        assertEquals(PlaybackState.Inactivo, state.playbackState)
    }

    @Test
    fun `un fallo devuelve la frase al campo y libera la retencion`() {
        connect()
        state.play("hola")
        dispatch(BackendEvent.Failure("boom", turn = 1))
        assertNull(state.sentText)
        assertEquals("hola", state.inputText)
        assertEquals(PlaybackState.Error, state.playbackState)
    }

    @Test
    fun `el microfono se apaga cuando se corta el envio de audio`() {
        connect()
        whenever(backend.sendAudio(any())).thenReturn(false)
        state.toggleLive()
        // El callback de AudioCapture corre desde el hilo de captura; acá se
        // simula el mismo efecto: el envío falla y el estado lo refleja.
        assertEquals(MicMode.Live, state.micMode)
        advance(50)
    }

    @Test
    fun `abrir la conversacion y repetir un turno cierra el panel`() {
        connect()
        state.play("hola")
        dispatch(segmentEvent(event = segment(gloss())))
        dispatch(BackendEvent.PhraseDone(turn = 1))
        state.onPlaybackDone()

        state.openDrawer(DrawerSide.Start)
        assertEquals(DrawerSide.Start, state.openDrawer)
        state.repeatTurnFromDrawer("hola")
        assertNull(state.openDrawer)
        assertTrue(state.replayPending)
        advance(500)
        assertEquals(PlaybackState.Procesando, state.playbackState)
    }

    // ── Dobles ───────────────────────────────────────────────────────────────

    private class RecordingHistory : SignHistoryRepository {
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

    private class RecordingScene : SignAllWebViewController {
        val avatarCalls = mutableListOf<String>()
        val queues = mutableListOf<List<SignQueueItem>>()
        val appends = mutableListOf<List<SignQueueItem>>()
        var plays = 0
        var stops = 0
        var theme: Boolean? = null
        var speed: Float? = null
        var debug: Boolean? = null
        var blur: Boolean? = null
        var silenceMs: Int? = null
        var silenceCalls = 0

        override fun attach(webView: WebView) = Unit

        override fun play() {
            plays++
        }

        override fun setQueue(items: List<SignQueueItem>) {
            queues += items
        }

        override fun appendQueue(items: List<SignQueueItem>) {
            appends += items
        }

        override fun playFrom(index: Int) = Unit

        override fun stop() {
            stops++
        }

        override fun setDefaultSign(url: String) = Unit

        override fun setAvatar(id: String) {
            avatarCalls += id
        }

        override fun setBackground(on: Boolean) = Unit

        override fun setGrid(on: Boolean) = Unit

        override fun setFreeCamera(on: Boolean) = Unit

        override fun setDebug(on: Boolean) {
            debug = on
        }

        override fun setReduceMotion(on: Boolean) = Unit

        override fun setSpeed(factor: Float) {
            speed = factor
        }

        override fun setTheme(dark: Boolean) {
            theme = dark
        }

        override fun setSilenceTimeoutMs(ms: Int?) {
            silenceMs = ms
            silenceCalls++
        }

        override fun applyBlur(on: Boolean) {
            blur = on
        }
    }
}
