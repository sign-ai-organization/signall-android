package com.signai.webview.ui.state

import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.signai.webview.data.AudioCapture
import com.signai.webview.data.BackendHealth
import com.signai.webview.data.SettingsRepository
import com.signai.webview.data.SignAiWebSocketClient
import com.signai.webview.data.SignAssetRepository
import com.signai.webview.data.SignHistoryRepository
import com.signai.webview.domain.BackendEvent
import com.signai.webview.domain.Character
import com.signai.webview.domain.Setting
import com.signai.webview.domain.Settings
import com.signai.webview.domain.SignQueueItem
import com.signai.webview.domain.SignsCatalog
import com.signai.webview.domain.TranslationSegment
import com.signai.webview.ui.designsystem.components.DrawerSide
import com.signai.webview.ui.webview.SignAiWebViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

/** Wait for the drawer close animation before replaying a conversation turn (`CNV-6`). */
private const val DrawerCloseDelayMs = 500L

/**
 * How long to wait for the transcript after the mic is released before giving
 * up. Transcribing 8 s of audio locally takes a few seconds, so this is
 * generous; it exists so a lost transcript shows a message instead of leaving
 * the app in "Procesando..." forever.
 */
private const val TranscriptionTimeoutMs = 15_000L

/**
 * Cada cuánto mira el estado si la cola de la escena ya terminó de armarse.
 *
 * Es un fotograma: el caso que resuelve es "el `done` llegó antes de que la escena
 * tuviera la frase", que dura lo que tarda una descarga, y a esa resolución no le
 * hace falta ser fino.
 */
private const val QueueSettlePollMs = 16L

/**
 * Cuánto se espera, **en silencio**, a que el backend conteste una frase mandada.
 *
 * Es el hermano de [TranscriptionTimeoutMs] para el otro sentido del socket: aquel
 * protege la transcripción del micrófono, este protege la traducción de lo que el
 * usuario mandó. Antes no existía ninguno para esta, y esa es la razón de que la
 * app quedara congelada: con el `readTimeout` en 0 y sin latido, una frase que
 * sale y nadie contesta deja el estado en *Procesando* para siempre, con los cinco
 * controles del panel deshabilitados y sin ninguna salida — ni apagar el
 * micrófono, ni reiniciar el backend, ni esperar.
 *
 * Son los mismos 15 s que usa la transcripción, y 5 s por encima del tope que el
 * backend le pone al LLM (`llm_timeout_ms = 10000`), así que una frase válida
 * siempre tiene tiempo de contestarse. Y es un reloj de **silencio**, no de
 * duración: se rearma con cada frame que llega ([armTurnSilence]), de modo que una
 * frase larga con muchas oraciones no se mate a la mitad solo por tardar.
 *
 * El único número que no sale de un documento: el SRS dice *qué* señal mostrar
 * (`main.tex:726-773`) y ningún documento dice cuánto esperar. Queda anotado en
 * `docs/CONTRASTE-APPvsDOCUMENTOS.md` §6 #5, con el texto propuesto para el SRS.
 */
private const val TurnSilenceMs = 15_000L

/**
 * Reconnect backoff: the wait grows by a second per attempt and tops out at
 * [MaxReconnectWaitMs]. `RF-INT-COM-01` and the SRS (*Desconectado*,
 * `main.tex:761-772`) want it growing and **without a limit of attempts**, so
 * this caps the *wait* and never the number of tries: stopping would leave the
 * app dead until it was restarted by hand, which is the very thing A4 fixes.
 */
private const val ReconnectBaseMs = 1_000L
private const val MaxReconnectWaitMs = 5_000L

/** How long a transient message stays in the stage hint. */
private const val StatusOverrideMs = 3_000L

/**
 * Cuánto se deja a la vista la frase que la app ya mandó.
 *
 * El campo tiene que conservarla —es la frase que el avatar está representando— pero
 * durante este tiempo Traducir está deshabilitado: ofrecer el botón sería
 * invitar a reenviar lo que ya se envió. Al cumplirse, el texto se borra solo y
 * el panel vuelve a quedar libre.
 *
 * Corto a propósito, y a pedido del owner: medio minuto de texto viejo en el
 * campo se lee como un input trabado, y con 5 s el usuario llegaba a creer que
 * la app se había colgado. 1,5 s alcanza para ver que el texto se va solo, y la
 * frase no se pierde: ya está en el historial y en el panel de conversación.
 */
private const val SentTextHoldMs = 1_500L

private const val TAG = "SignAiState"

class MainScreenState(
    private val history: SignHistoryRepository,
    private val settingsStore: SettingsRepository,
    private val webView: SignAiWebViewController,
    private val backend: SignAiWebSocketClient,
    private val assets: SignAssetRepository,
    private val health: BackendHealth,
    val signs: List<String> = SignsCatalog.available,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Microphone capture. Built here rather than injected because the chunk
     * callback is this class's own `sendAudio`; `AudioRecord` needs no context.
     */
    private val audio = AudioCapture { chunk -> sendAudio(chunk) }

    /**
     * Client-side latency numbers, mirrored to logcat and readable from
     * `adb shell dumpsys`.
     *
     * The server measures its own leg; these are the numbers only the device
     * can produce — how long a fragment took to become queue items, and how
     * long a whole turn took from the first text to the last fragment. They are
     * what tells you whether the avatar is keeping up with the speaker.
     */
    private val metrics = SignAiMetrics()

    val historyList: SnapshotStateList<String> = mutableStateListOf<String>().apply {
        addAll(history.load())
    }

    /**
     * Session-scoped conversation shown in the left drawer: one turn per phrase
     * sent while the app is open (spec `CNV-4`). In-memory only, never persisted.
     */
    val conversation: SnapshotStateList<ConversationTurn> = mutableStateListOf()
    private var turnCounter = 0L

    var settings by mutableStateOf(settingsStore.load())
        private set

    /**
     * Se está cargando un avatar nuevo en la escena.
     *
     * El selector solo se habilita en reposo absoluto, así que mientras esto
     * está prendido no hay ninguna frase en vuelo; igual el panel entero queda
     * deshabilitado hasta que la escena avise `avatarLoaded:<id>`, para que no
     * se pueda pedir una traducción contra un modelo a medio construir.
     */
    var avatarLoading by mutableStateOf(false)
        private set

    /** Avatar que estaba puesto antes del cambio, para volver si la carga falla. */
    private var avatarBeforeSwitch: Character? = null

    /** Reloj del cambio de avatar, para el log de la carga. */
    private var avatarSwitchStartedAt = 0L

    /**
     * El selector de Personaje solo se puede tocar en reposo absoluto.
     *
     * Es la decisión del owner (2026-10-24): con una captura, una frase lista o
     * enviada, una traducción, cola o una repetición pendientes, el avatar no
     * cambia. *Grabando*, *Procesando* y *Reproduciendo* quedan afuera por el
     * estado; [dictationPending] cubre "frase lista", [sentText] cubre "frase
     * enviada", y [queuePlaying]/[replayPending]/[pendingText] cubren la cola y
     * el Repetir, que siguen vivos después del `done`.
     *
     * *Desconectado* y *Error* **sí** permiten el cambio: no hay ninguna
     * operación en curso, y bloquearlo ahí dejaría el selector inutilizable sin
     * backend, que es justo cuando uno quiere probar los avatares. El plan pedía
     * "únicamente en `Inactivo`"; se sigue la lista del owner y no esa
     * recomendación, porque la lista es la decisión y la recomendación no.
     * [sceneDead] lo deja apagado para siempre: un WebView sin proceso de render
     * no puede cargar nada.
     */
    val canSwitchAvatar: Boolean
        get() = !avatarLoading &&
            !sceneDead &&
            micMode == MicMode.Off &&
            !queuePlaying &&
            !replayPending &&
            pendingText == null &&
            !dictationPending &&
            sentText == null &&
            (playbackState == PlaybackState.Inactivo ||
                playbackState == PlaybackState.Desconectado ||
                playbackState == PlaybackState.Error)

    var sign by mutableStateOf(signs.firstOrNull() ?: SignsCatalog.DEFAULT)
        private set

    /**
     * Playback fallback for a gloss we have no animation for — either an LSC50
     * sign whose clip is missing (none today) or an unknown gloss. See
     * [Settings.demoSignFallback].
     *
     * Always the sign picked in the settings menu, so the demo playback follows
     * the same choice the user made instead of a hardcoded one.
     */
    private val fallbackSignUrl: String get() = SignsCatalog.assetUrl(sign)

    /**
 * The state machine proper: the seven states of the SRS, and nothing else.
 *
 * Everything the interface shows goes through [stateForUi], and [stateLabel] is
 * what the user reads.
 */
    var playbackState by mutableStateOf(PlaybackState.Inactivo)
        private set

    /**
     * "Reproducción completada" dejó de ser un estado y pasó a ser un flag.
     *
     * El SRS no la tiene entre los siete (`main.tex:726-773`) y antes ocupaba un
     * hueco del enum solo para decorar: [MainScreen.kt] la usa para mostrar el
     * aviso de frase representada. Es el mismo patrón que
     * [dictationReadyToSend], y el mismo motivo —el estado no dice "terminó" en el
     * SRS porque en modo en vivo nada termina: la cola se sigue llenando.
     */
    var doneVisible by mutableStateOf(false)
        private set

    /**
     * El estado que ve el usuario.
     *
     * Solo [PlaybackState.TextoListo] se deduce, y solo cuando la máquina está
     * [PlaybackState.Inactivo]: con el micrófono apagado, una frase en el campo y
     * nada retenido, el estado real es "inactivo" y el SRS quiere que lo que se
     * vea sea "Texto listo" (`main.tex:746`). Se deduce en vez de asignarse
     * porque depende de cuatro cosas a la vez —micrófono apagado, texto en el
     * campo, sin retención— y cualquiera de las tres lo deshace: que el estado
     * fuera "Texto listo" cuando la conexión se cayó taparía *Desconectado*, y
     * un estado que hay que acordarse de volver a poner es un estado que algún
     * día se queda.
     *
     * La lista de condiciones es [dictationReadyToSend], que ya la tenía.
     */
    val stateForUi: PlaybackState
        get() = if (playbackState == PlaybackState.Inactivo && dictationReadyToSend) {
            PlaybackState.TextoListo
        } else {
            playbackState
        }

    /**
     * La etiqueta del estado, que es lo que se lee arriba en el escenario.
     *
     * Sale de [stateForUi] y no de [playbackState], que es lo que hace que
     * *Texto listo* se vea: `statusLine` lee esta etiqueta, así que si leyera el
     * enum directamente el tercer estado del SRS no aparecería nunca.
     *
     * Sale del enum y no de acá una variante: el owner decidió el 2026-10-17 que
     * en pantalla se lea el nombre del estado, y el nombre es uno solo. Por eso
     * `Grabando` ya no distingue "Dictando..." de "Escuchando...": el modo sigue
     * en `micMode`, que es lo que muestran el ícono, el `stateDescription` del
     * botón y el log, y el indicador no se inventa textos propios.
     */
    val stateLabel: String
        get() = stateForUi.label

    /**
     * La escena tiene una cola de señas firmándose.
     *
     * Distinto de [playbackState], y la distinción no es un detalle. En modo
     * tiempo real la cola se llena más rápido de lo que el avatar la consume: en
     * una prueba de 30 s había 38 fragmentos en cola, así que "el último `done`
     * ya llegó" y "el avatar ya terminó" son cosas distintas hasta por medio
     * minuto. Confundirlas dejaba la interfaz diciendo "escuchando" con medio
     * minuto de señas por delante, y al apagar el micrófono eso se leía como "no
     * hay nada en curso": la app esperaba una transcripción que no iba a llegar
     * y todo el panel quedaba deshabilitado.
     *
     * La fuente de verdad es el puente `onPlaybackDone`, no el enum.
     */
    private var queuePlaying = false

    /**
     * Reloj de la animación: cuándo llegó la primera oración del turno, cuándo
     * arrancó de verdad el primer movimiento del muñeco, y cuántas señas van.
     *
     * Los tres relojes existen porque cada uno mide una etapa distinta y antes
     * faltaba una: `play()` se llama desde Kotlin **antes** de que el `.glb`
     * termine de bajar, así que el reloj de "representando" arrancaba con
     * milisegundos de carga que no son del avatar. El mensaje `signStart` de la
     * escena cierra ese hueco (ver [onSignStart]).
     */
    private var playbackStartedAt = 0L

    /** Cuándo llegó la primera oración del turno, para medir el arranque real. */
    private var firstSegmentAt = 0L

    /** Ya se midió el arranque de este turno: las siguientes oraciones no cuentan. */
    private var signStartReported = false

    /**
     * Marca el arranque de un tramo de animación.
     *
     * Sin argumentos a propósito: cuántas señas van no lo decide este reloj sino
     * [turnQueueItems], que lleva todo lo que se le entregó a la escena. Antes se
     * pasaba el tamaño del primer lote, y con la frase representándose oración por
     * oración ese número se quedaba corto.
     */
    private fun startPlaybackClock() {
        playbackStartedAt = SystemClock.elapsedRealtime()
    }

    /**
     * La escena avisó que el muñeco se está moviendo (`signStart:<i>:<jsMs>`).
     *
     * Mide el tramo que no tenía reloj en ningún lado: desde que llegó la
     * oración hasta que el avatar se mueve en pantalla. El backend cronometra
     * hasta mandar el `segment`; este es el número de esperar mirando un canvas
     * quieto, que es lo que la persona percibe.
     *
     * Solo la **primera** oración del turno cuenta. Las siguientes llegan con el
     * avatar ya sonando, y para esas lo que importa es cuánto tardó el backend,
     * que ya mide `rt_sentence_delay_ms`.
     */
    fun onSignStart(queueIndex: Int, jsMillis: Long) {
        // La escena está muerta: este mensaje llegó tarde y no mide nada.
        if (sceneDead) return
        val now = SystemClock.elapsedRealtime()
        // La escena se movió sin que hubiera una frase en vuelo: cola de un turno
        // muerto, o el avatar calentando al abrir. No es una muestra de nada.
        if (firstSegmentAt == 0L || turnSentAt == 0L) return
        val elapsedMs = now - firstSegmentAt
        Log.i(TAG, "signStart: oración→avatar $elapsedMs ms (seña $queueIndex, reloj JS $jsMillis ms)")
        if (signStartReported) return
        signStartReported = true
        metrics.onSignStart(elapsedMs)
        // El total del SRS. `oración→avatar` es solo el último tramo —de la
        // primera oración al primer movimiento—; este arranca donde lo hace el del
        // usuario: al mandar la frase. Es el número que se puede comparar con la
        // línea base, porque ahí no hay nada del teléfono ni del avatar.
        metrics.onFirstSign(now - turnSentAt)
    }

    /**
     * Le pasa a la escena el `SILENCE_TIMEOUT_MS` del STT, para el overlay de
     * depurar (decisión D-6).
     *
     * Se dispara cuando la escena está lista, no al arrancar la app: antes de
     * eso no hay WebView al que mandárselo. Es una lectura de red a `/health` y
     * no bloquea nada —si falla, el overlay dice "?"— así que no hace falta
     * mirar que la conexión esté arriba ni reintentar.
     */
    fun pushSilenceTimeout() {
        scope.launch {
            val ms = health.silenceTimeoutMs()
            webView.setSilenceTimeoutMs(ms)
            Log.i(TAG, "silencio del STT: ${ms?.let { "$it ms" } ?: "desconocido"}")
        }
    }

    /** Glosses of the sequence being played, shown in the stage hint while signing. */
    private var caption by mutableStateOf("")

    /** Transient message (errors) that replaces the state label in the hint. */
    private var statusOverride by mutableStateOf<String?>(null)

    private var statusJob: Job? = null

    /**
     * Which edge drawer is open (`EDG-15`). `null` = both closed. The drawer
     * open gesture calls back here, so a Repetir tap can close the left drawer
     * and start the sign only once the closing animation is over.
     *
     * Only one drawer can be open at a time (`EDG-19`): setting a side always
     * clears the other, so a tap on the inactive handle closes instead of
     * crossing over. Written on tap/drag-snap only (a handful of times per
     * interaction), not from an animation callback, to keep recomposition cheap.
     */
    var openDrawer: DrawerSide? by mutableStateOf(null)
        private set

    var lastText by mutableStateOf("")
        private set

    /**
     * True while a Repetir is in flight, from the tap to the end of the sign:
     * keeps the whole control panel disabled (`CTL-12`).
     *
     * Both entry points go through [requestTranslation] —the stage button and
     * the conversation drawer— and so does the release: [onPlaybackDone],
     * `fail()`, and the "la frase no produjo ninguna seña" case. It used to be
     * cleared by the first streamed fragment, which is when signing *starts*:
     * that released the panel halfway through the sign the owner had asked to
     * see it locked.
     */
    var replayPending by mutableStateOf(false)
        private set

    /** Current phrase in the control panel input (shared with the history sheet). */
    var inputText by mutableStateOf("")
        private set

    /** Text the backend understood, echoed back for the transcription bar (`RF-UI-02`). */
    var transcript by mutableStateOf("")
        private set

    private var connectJob: Job? = null

    /** Bounds the wait for a transcript after the mic is released. */
    private var processingJob: Job? = null

    /**
     * Red de seguridad de la frase mandada: [TurnSilenceMs] de silencio y se
     * declara el fallo. Se rearma con cada frame que llega, así que mide lo que
     * dice su nombre —que nadie habla— y no cuánto tarda la frase.
     */
    private var turnSilenceJob: Job? = null

    /**
     * Qué hacer con lo que siga llegando de una frase que el usuario canceló.
     *
     * Cancelar es **local**: el protocolo no tiene un frame para decirle al backend
     * "olvida esa frase", así que el backend sigue mandando las oraciones que le
     * faltan y su `done`, como si nada. Sin esto, las oraciones canceladas se
     * representarían igual detrás de la espalda del usuario y el `done` viejo
     * cerraría el turno siguiente.
     *
     * Los cuatro casos, porque el `done` no lleva con qué identificar la frase a la
     * que pertenece: solo se puede "tragar" el primero que llegue.
     */
    private enum class StaleTurn {
        /** No hay nada pendiente de una frase cancelada. */
        None,

        /**
         * Recién cancelada: se descartan las oraciones que falten y se traga su
         * `done`, porque todavía no salió ninguna frase nueva que pueda ser la
         * dueño de ese `done`.
         */
        SentencesAndDone,

        /**
         * Ya salió otra frase mientras se esperaba: sus oraciones se aceptan —
         * puede colarse alguna de la cancelada, que es un detalle visible—, pero
         * el `done` viejo se traga igual, porque si no cerraría el turno nuevo.
         */
        DoneOnly,
    }

    /** Lo que queda de la frase que el usuario canceló. Ver [StaleTurn]. */
    private var staleTurn = StaleTurn.None

    /**
     * La escena 3D dejó de existir: el navegador perdió su proceso de render, o la
     * página principal no cargó.
     *
     * Antes la app no tenía cómo enterarse de eso, y el resultado era un estado
     * *Reproduciendo* con el muñeco quieto para el resto de la sesión, sin salida.
     * Con el aviso ([onSceneFailed]) deja de fingir: no entrega colas nuevas, no mide
     * la primera seña y no vuelve a decir que terminó de representar.
     *
     * **No** se limpia sola: un WebView sin proceso de render no renderiza nunca
     * más, así que solo un WebView nuevo —o reiniciar la app— lo arregla. Que se
     * reinicie solo en vez de que la app muera es decisión del owner, y está anotada
     * en `docs/CONTRASTE-APPvsDOCUMENTOS.md` §9.
     */
    private var sceneDead = false

    /** Text of the turn in flight, so the conversation entry is added on result. */
    private var pendingText: String? = null

    /**
     * Segmentos de la frase en curso, por posición, y cuáles ya se reprezentaron.
     *
     * El backend manda una oración por `segment`, con `index` y `total` reales, y
     * **cada oración se representa apenas llega** (decisión D-1 de
     * `openspec/sessions/2026-10-16-latencia-y-decisiones.md`). Antes se juntaban
     * todas y la cola se armaba cuando `size >= total`, con el avatar parado
     * mientras tanto: medido, 569 ms parado con la primera seña ya disponible en
     * una frase fría de tres oraciones.
     *
     * El mapa se queda igual porque hace falta lo otro: el caption, la entrada de
     * conversación y `metrics.onTurn` son de la frase entera. Lo que cambió es
     * [flushedThrough]: una oración ya entregada a la escena no se vuelve a
     * entregar, así que llegar tarde no la representa dos veces.
     */
    private val pendingSegments = sortedMapOf<Int, TranslationSegment>()

    /** How many segments the phrase has; 0 until the first one says so. */
    private var pendingTotal = 0

    /**
     * Cuántos [appendQueue] están armando cola para este turno **ahora mismo**.
     *
     * `appendQueue` resuelve los assets en una coroutine —puede suspender bajando un
     * `.glb`— así que el `done` del backend llegafredo a veces antes de que la cola
     * esté entregada a la escena. Y [finishTurn] decide el destino del turno mirando
     * `queueStarted`, que en ese momento todavía valía `false`: el resultado era
     * "esa frase no produjo ninguna seña" con la frase traducida y a punto de
     * representarse, y en vivo la frase de reserva podía salir antes de que la
     * anterior entregara su cola.
     *
     * Con este contador, [finishTurn] espera a que la cola se acomode antes de
     * decidir. El techo es el mismo [TurnSilenceMs]: para entonces el reloj de
     * silencio ya declaró el fallo y no hay nada más que esperar.
     */
    private var appendsInFlight = 0

    /**
     * Cadena de entrega de la cola: cada tanda espera a que la anterior entregue.
     *
     * Los assets se resuelven **en paralelo** (bajar una seña no tiene por qué
     * esperar a que termine de bajar la anterior) pero se entregan **en orden de
     * oración**, porque el orden es de la frase y no del reloj de las descargas.
     * Ver [appendQueue].
     *
     * Se reinicia en [clearTurnState]: un turno nuevo no espera al anterior.
     */
    private var queueDeliveryGate = CompletableDeferred(Unit)

    /**
     * Índice de la última oración ya entregada a la escena; -1 = ninguna.
     *
     * Es lo que hace idempotente a [flushSegments]: la primera llamada se lleva
     * lo que había y sube el índice, y las siguientes encuentran la lista vacía.
     */
    private var flushedThrough = -1

    /**
     * La cola de este turno ya arrancó en la escena.
     *
     * Distingue el `setQueue` + `play()` del primer lote de los `appendQueue` de
     * las oraciones siguientes. La escena lleva su propio estado, pero el de
     * Kotlin es el que decide historial, caption y conversación, así que vive acá.
     */
    private var queueStarted = false

    /** Ítems ya entregados a la escena en este turno, para armar el caption. */
    private val turnQueueItems = mutableListOf<SignQueueItem>()

    /**
     * Posición en [conversation] de la entrada de este turno, o -1.
     *
     * Representando oración por oración la entrada se crea con la primera y se
     * completa con las demás; sin este índice habría que buscarla por texto en
     * cada oración, y dos frases iguales darían la misma entrada.
     */
    private var conversationTurnIndex = -1

    /** True when the turn in flight is a Repetir, which must not be re-recorded. */
    private var isReplay = false

    // ── Turno en curso ───────────────────────────────────────────────────────
    //
    // El backend divide la frase en oraciones y manda una `segment` por
    // oración, con `index` y `total` reales. No hay fragmentos: lo que hay son
    // oraciones, y **cada una se representa apenas llega** —el avatar no espera
    // a que esté la frase entera (D-1). El reloj del turno es lo único que se
    // lleva entre una frase y la siguiente.

    /** Wall clock of the current turn, for the timing logs and `metrics`. */
    private var turnStartedAt = SystemClock.elapsedRealtime()

    /**
     * Cuándo se mandó la frase del turno en curso, o `0` si no hay ninguna.
     *
     * Es el arranque del **tiempo hasta la primera seña** del SRS (`main.tex:848-852`),
     * y va aparte de [turnStartedAt] a propósito. Ese reloj se reinicia en
     * [resetTurn], que también llama [resumeListeningIfMicOn] al terminar cada
     * frase en modo en vivo; con un solo reloj, una frase cuya escena se moviera
     * después del `done` midió desde el reloj de la frase siguiente, o no midió.
     *
     * Además sirve de cerrojo: con `0` no hay turno en vuelo, así que un
     * `signStart` suelto —de una cola que ya murió— no inventa una muestra.
     */
    private var turnSentAt = 0L

    /**
     * La captura en curso —o la que el usuario acaba de cortar— es de dictado.
     *
     * Es lo que decide a dónde va cada transcripción, y tiene que existir porque
     * el `final` de lo dictado llega **después** de que el micrófono se apagó: el
     * usuario tocó el botón, la app mandó `end`, y recién ahí vuelve el texto.
     * Si la ruta se decidiera solo con [micMode], ese `final` caería en el camino
     * de en vivo y la frase se representaría sola, que es justo lo que RF-STT-03
     * prohíbe.
     *
     * Antes lo decidía un flag `dictation` que mandaba el backend. El backend ya
     * no lo manda —y no debería: adivinar el modo es la forma de equivocarse en
     * el sentido que representa lo que el usuario no confirmó— así que el que sabe es
     * esta app.
     */
    private var dictationAwaitingFinal = false

    /**
     * Whether anything at all came back during the current listening session.
     *
     * Set by the first transcript, partial or final. It is the difference
     * between "the user released the mic without speaking" and "we spoke and
     * never got a word back", which need opposite handling: the first goes back
     * to idle immediately, the second is a real failure worth reporting.
     *
     * Per **session**, not per utterance: with the mic left on, one silence at
     * the end of a phrase would otherwise look like "nothing was said".
     */
    private var heardAnythingThisSession = false

    /**
     * Cuál de los dos interruptores del micrófono está prendido, si alguno.
     *
     * Es un enum y no dos flags a propósito: los dos modos usan la misma
     * captura y el mismo socket, así que "los dos prendidos" es un estado
     * imposible y el que hay que dejar sin representar (ver [MicMode]).
     *
     * Deliberadamente aparte de [playbackState]. Antes un solo enum decía dos
     * cosas distintas —"¿está el mic prendido?" y "¿qué hace el muñeco?"— y eso
     * solo funcionaba porque el micro se apagaba en cada frase. En vivo el muñeco
     * representa con el micrófono prendido, así que los dos se superponen de verdad y
     * necesitan su propio estado.
     */
    var micMode by mutableStateOf(MicMode.Off)
        private set

    /** El micrófono está capturando, en cualquiera de los dos modos. */
    val micActive: Boolean get() = micMode != MicMode.Off

    /** El micrófono de la fila de arriba: dictado, solo transcribe. */
    val dictationActive: Boolean get() = micMode == MicMode.Dictation

    /** El interruptor `TR` de la fila de abajo: en vivo, cada frase sale sola. */
    val liveActive: Boolean get() = micMode == MicMode.Live

    /**
     * Texto del dictado, lo que el usuario dijo y todavía no mandó.
     *
     * Es lo que sobrevive a que se apague el micrófono: es lo que quedó escrito,
     * editable, a la espera de que el usuario confirme el envío (RF-STT-03). Si
     * después se prende el modo en vivo y se apaga, el campo vuelve a mostrar
     * esto: perder un dictado porque el usuario quiso probar el otro interruptor
     * sería la peor forma de que esto salga mal.
     *
     * Antes esto se llamaba `dictationBuffer` y acumula más de una frase, porque
     * el micrófono quedaba prendido entre ellas. Ya no: el dictado se cierra con
     * la frase (RF-STT-03 dice que la captura se detiene al detectar el
     * silencio), así que una sesión de dictado es una frase y este es el texto
     * que espera confirmación.
     */
    private var dictationBuffer = ""

    /**
     * Hay texto dictado en el campo a la espera de que el usuario confirme.
     *
     * Es el tercer estado de RF-UI-01 ("texto listo para enviar") y lo que
     * mantiene el texto en RNF-REL-04: mientras esté en `true` el campo sigue
     * editable y Traducir disponible, y una caída de red breve no lo borra.
     *
     * Se apaga cuando la frase sale —la retención de [sentText] pasa a tenerla en
     * pantalla— y cuando el campo queda vacío. Editar el texto **no** lo apaga:
     * sigue siendo lo que el usuario va a confirmar.
     */
    var dictationPending by mutableStateOf(false)
        private set

    /**
     * Tercer estado del botón de voz: hay una frase dictated que se puede mandar.
     *
     * Exige que el campo tenga algo: si el usuario borró lo que se transcribió, ya
     * no hay nada listo para enviar y el botón vuelve a ser el de empezar a dictar.
     *
     * Y exige que el micrófono esté apagado. Con el TR prendido el campo muestra
     * la transcripción en vivo, no lo que quedó pendiente: marcar "listo para
     * enviar" ahí sería señalar un texto que el usuario no está viendo. Lo que sí
     * se conserva es el pendiente —[dictationBuffer]—, y vuelve al campo cuando
     * el TR se apaga.
     */
    val dictationReadyToSend: Boolean
        get() = dictationPending && !micActive && inputText.isNotBlank() && !sentTextLocked

    /**
     * La frase que la app ya mandó, a la vista pero no mandable.
     *
     * El campo queda con lo que el avatar está representando siempre que la frase
     * salió sin que nadie tocara Traducir: un Traducir a mano y el Repetir del
     * escenario. Si Traducir volviera a estar habilitado ahí, el botón ofrecería
     * mandar otra vez lo que ya se mandó y el muñeco representaría la frase dos veces
     * seguidas por un solo toque.
     *
     * El modo en vivo **no** la usa, y es a propósito: ahí el micrófono está
     * prendido, así que Traducir ya está deshabilitado por `micActive` y la
     * retención solo cambiaría el texto del campo por el de la frase anterior
     * mientras el usuario sigue hablando. Apagar el TR tampoco la usa: deja el
     * campo limpio [stopCapture], porque para entonces lo que decía el campo ya
     * se representó y no hay nada que impedir reenviar.
     *
     * Se borra sola a los [SentTextHoldMs]. Nada se pierde: la frase ya quedó
     * en el historial y en el panel de conversación.
     */
    var sentText by mutableStateOf<String?>(null)
        private set

    /** El campo tiene el texto ya enviado: no se puede mandar otra vez. */
    val sentTextLocked: Boolean get() = sentText != null

    private var sentTextJob: Job? = null

    /**
     * Deja a la vista la frase que la app ya mandó, sin que se pueda reenviar.
     *
     * El campo conserva el texto y Traducir queda deshabilitado mientras dure.
     * Los dos cambios —el texto que se borra y el botón que vuelve— ocurren en
     * la misma asignación, así que el usuario no alcanza a ver el campo vacío
     * con el botón muerto, ni al revés: se va el texto y en el mismo fotograma
     * Traducir y el campo vuelven a estar utilizables.
     */
    private fun holdSentText(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        sentText = clean
        // El aviso dura lo mismo que el texto: si viviera más, se leería
        // "el texto se borra solo" con el texto ya borrado.
        //
        // Y solo cuando el avatar no está todavía en "Traduciendo": durante la
        // traducción el campo ya está bloqueado por `busy`, que se explica solo,
        // y pisar el estado con "Ya está representado." antes de que la frase ni se
        // haya traducido era una contradicción a la vista.
        if (playbackState != PlaybackState.Procesando) {
            showStatus("Ya está representado.", SentTextHoldMs)
        }
        Log.i(TAG, "texto ya enviado: retenido ${SentTextHoldMs}ms, Traducir bloqueado. \"$clean\"")
        sentTextJob?.cancel()
        sentTextJob = scope.launch {
            delay(SentTextHoldMs)
            // Solo si sigue intacto: si llegó otro texto al campo, ya no es el
            // mismo y no hay que borrarlo.
            if (sentText != clean || inputText != clean) return@launch
            // Las dos asignaciones juntas: el texto desaparece y el campo y
            // Traducir vuelven a estar libres en el mismo fotograma.
            inputText = ""
            transcript = ""
            sentText = null
            Log.i(TAG, "texto retenido borrado: Traducir y el campo vuelven a estar disponibles")
        }
    }

    /** Suelta la retención: llegó al campo un texto que no es el ya enviado. */
    private fun releaseSentText() {
        if (sentText == null) return
        Log.i(TAG, "texto retenido liberado: el campo tiene otro texto, ya es otra frase")
        sentTextJob?.cancel()
        sentTextJob = null
        sentText = null
    }

    /**
     * Whether the backend session is up.
     *
     * The mic button greys out without it, because tapping it otherwise produces
     * a status message and nothing else. Exposed as Compose state because the
     * connection can drop while the app is open (`RF-INT-COM-01`).
     */
    val backendConnected: Boolean get() = backend.isConnected

    /** What the stage hint shows: the glosses while signing, else state or message. */
    val statusLine: String
        get() = when {
            // Cambio de avatar en curso: es un estado de carga propio, no uno
            // de los siete del SRS. Mientras dura, el panel está deshabilitado.
            avatarLoading -> "Cargando avatar…"
            // Depuración primero: con el interruptor de Ajustes → Avanzado prendido
            // el indicador se usa como pantalla de diagnóstico y puede llevar
            // glosas y mensajes en detalle.
            settings.debug -> when {
                playbackState == PlaybackState.Reproduciendo && caption.isNotBlank() ->
                    "Reproduciendo · ${caption}"
                statusOverride != null -> statusOverride!!
                else -> stateLabel
            }
            // Fuera de depuración sale el nombre del estado y nada más. La única
            // excepción es `statusOverride`, que hoy solo se usa para
            // "Seña no disponible": el único texto propio que la tabla permite
            // sobre el indicador, en la fila *Reproduciendo*.
            statusOverride != null -> statusOverride!!
            else -> stateLabel
        }

    // ── Backend session ─────────────────────────────────────────────────────

    /**
     * Intentos de reconexión seguidos, para la espera creciente.
     *
     * Vive en la clase y no en el `launch` porque el reinicio lo hace
     * [BackendEvent.Connected], que es el restablecimiento —no el arranque— lo que
     * lo deja en cero, según el SRS.
     */
    private var reconnectAttempts = 0

    /**
     * Opens the translation session and keeps it open, reconnecting on failure.
     *
     * El SRS pide tres cosas y las tres están acá (`main.tex:761-772`): espera
     * creciente **hasta un máximo de 5 s**, **sin límite de intentos**, y el
     * contador **reiniciado** cuando la conexión vuelve (en `Connected`). Antes el
     * contador subía para siempre: después de unas horas de app abierta, la primera
     * caída esperaba 5 s en vez de 1 s.
     */
    fun connect() {
        connectJob?.cancel()
        connectJob = scope.launch {
            while (isActive) {
                if (playbackState == PlaybackState.Inactivo) {
                    // El SRS no distingue "conectando" de "sin conexión": son un
                    // estado solo, y su texto ya dice que se está reconectando.
                    playbackState = PlaybackState.Desconectado
                }
                backend.events().collect { event -> handleEvent(event) }
                // The flow only ends when the session dropped, so this is the
                // reconnect path: back off, then try again.
                //
                // *Sin condición*: antes solo se ponía *Desconectado* si el estado no
                // era Traduciendo ni Reproduciendo, así que una caída en mitad de una
                // frase dejaba el indicador diciendo "Procesando..." para siempre,
                // con el botón Traducir deshabilitado y el envío manual rechazado:
                // la app quedaba trabada y había que cerrarla a la fuerza.
                playbackState = PlaybackState.Desconectado
                reconnectAttempts++
                delay(
                    (ReconnectBaseMs * reconnectAttempts).coerceAtMost(MaxReconnectWaitMs),
                )
            }
        }
    }

    /**
     * Devuelve la interfaz a "escuchando" o "dictando" cuando el mic sigue
     * abierto, y la libera cuando ya no.
     *
     * Una frase terminó y el avatar va a terminar de representarla, pero el mic sigue
     * prendido: el siguiente `done` tiene que devolver el estado a la etiqueta
     * del modo que está activo, o el botón se vería apagado mientras está
     * capturando, y el usuario no sabría si tiene que tocarlo.
     *
     * También reinicia el reloj del turno. Con el mic prendido, una sesión son
     * varias frases y cada una mide por separado: el tiempo desde que terminó
     * la anterior hasta que terminó esta.
     */
    private fun resumeListeningIfMicOn() {
        if (!micActive) {
            // No hay a quién volver a escuchar, y esa es la otra mitad de la
            // misma pregunta: si el avatar sigue con cola se la deja terminar —
            // el puente `onPlaybackDone` la cierra —; si no, el turno terminó y la
            // interfaz tiene que quedar libre. Sin esto se quedaba en
            // "Procesando..." con todo el panel deshabilitado.
            // "Reproducción completada" no es un estado del SRS: es el aviso de que la
            // frase terminó, y se apaga solo cuando empieza otra cosa.
            if (queuePlaying) playbackState = PlaybackState.Reproduciendo
            else doneVisible = true
            return
        }
        resetTurn()
        playbackState = PlaybackState.Grabando
    }

    /** Una oración traducida: se representa apenas llega, sin esperar a las demás. */
    private fun onWholePhraseSegment(event: BackendEvent.Segment) {
        // Oración de una frase que el usuario canceló: se descarta. El backend no
        // sabe que la frase ya no interesa y sigue mandando lo que le falta.
        if (staleTurn == StaleTurn.SentencesAndDone) {
            Log.i(TAG, "oración de una frase cancelada: se descarta")
            return
        }
        // Algo volvió: el reloj de silencio de la frase que esté en vuelo se
        // rearma. Es lo que separa "el backend tarda" de "el backend no contesta".
        armTurnSilence()
        transcript = pendingText ?: transcript
        pendingTotal = maxOf(pendingTotal, event.total)
        pendingSegments[event.index] = event.segment
        // El reloj del arranque se prende con la **primera** oración del turno, no
        // con la primera que se representa: es lo que separa "el backend tardó"
        // de "el teléfono tardó".
        if (firstSegmentAt == 0L) firstSegmentAt = SystemClock.elapsedRealtime()
        // Cada oración entra a la cola por separado (D-1). `total` ya no decide
        // nada: el avatar representa lo que haya, y lo que llegue después se
        // encadena detrás con `appendQueue`.
        flushSegments()
    }

    /**
     * Resolves one sentence's glosses to queue items.
     *
     * Suspende: resolver una glosa puede ser una descarga de un asset remoto. Por
     * eso todas las oraciones de una frase se resuelven juntas y en orden, en
     * [appendQueue], y la cola nunca se arma a mitad de camino.
     *
     * Una glosa que el catálogo no tiene no se representa (RF-CAT-03) salvo que
     * [Settings.demoSignFallback] esté prendido. Ese interruptor es lo que hace
     * que una glosa sin clip (hoy ninguna) muestre algo en vez de "Seña no
     * disponible": el catálogo la anuncia, pero no hay clip, así que la cola cae
     * en la única seña que viene dentro del APK.
     *
     * Devuelve **qué glosas quedaron afuera** además de la cola, porque el SRS
     * (`main.tex:201`, `:753`) pide informar "Seña no disponible" en vez de
     * mandar al avatar una instrucción inválida — y avisarlo por glosa, no solo
     * cuando una oración entera no produjo nada, que era lo que pasaba antes.
     */
    private suspend fun queueItemsFor(segment: TranslationSegment): ResolvedSentence {
        val items = mutableListOf<SignQueueItem>()
        val missing = mutableListOf<String>()
        for (gloss in segment.glosses) {
            // Primero se pregunta si el path es una de las animaciones que ya
            // vienen dentro del APK. Son same-origin por construcción, así que
            // descargarlas sería un 404 garantizado que caería en el fallback de
            // abajo — y el fallback es la seña por defecto, o sea que todas las
            // glosas responderían con el mismo movimiento.
            //
            // Un `signs/*.glb` que **no** tenemos (una glosa anunciada sin clip;
            // hoy no hay ninguna) es igual de indescargable: es nuestro propio
            // layout, así que tampoco lo sirve nadie por HTTP. Se saltea el viaje
            // de red y se va derecho al fallback. Cualquier otra cosa —un asset
            // remoto de verdad— sí baja por la red.
            val local = SignsCatalog.localAssetOrNull(gloss.glbPath)
            val url = when {
                local != null -> local
                SignsCatalog.isOwnAssetPath(gloss.glbPath) -> null
                gloss.available -> assets.resolve(gloss.text, gloss.glbPath)
                else -> null
            }
            when {
                url != null -> items += SignQueueItem(
                    gloss = gloss.text,
                    type = segment.type,
                    url = url,
                    available = true,
                )

                settings.demoSignFallback -> items += SignQueueItem(
                    gloss = gloss.text,
                    type = segment.type,
                    url = fallbackSignUrl,
                    available = gloss.available,
                )

                else -> {
                    // Sin seña y sin fallback: no se representa, pero se cuenta para que
                    // `sin_seña` diga la verdad y se anota para avisar.
                    metrics.onUnavailableGloss()
                    missing += gloss.text
                }
            }
        }
        return ResolvedSentence(items, missing)
    }

    /**
     * Una oración ya resuelta: lo que va a la cola y lo que quedó afuera.
     *
     * Antes [queueItemsFor] devolvía solo la cola, y las glosas que no tenían
     * animación desaparecían: solo se contaban en `sin_seña` y el texto salía
     * únicamente cuando una oración entera no produjo nada. Devolver las dos
     * listas es lo que permite avisar por glosa.
     */
    private data class ResolvedSentence(
        val items: List<SignQueueItem>,
        val missing: List<String>,
    )

    private fun handleEvent(event: BackendEvent) {
        when (event) {
            is BackendEvent.Connected -> {
                // El contador de reconexión se reinicia **acá**, no cuando se
                // pierde la sesión: es el restablecimiento el que lo reinicia
                // (`main.tex:761-772`, "momento en el que el contador se reinicia").
                // Antes nunca volvía a cero, así que después de unas horas de app
                // abierta la primera caída esperaba el tope en vez de 1 s.
                reconnectAttempts = 0
                if (playbackState == PlaybackState.Desconectado) {
                    playbackState = PlaybackState.Inactivo
                }
            }

            is BackendEvent.Transcript -> {
                // Un `final` vacío es una respuesta, no una frase perdida: el STT
                // cerró el utterance sobre audio que su filtro de descarte no
                // encontró. Descartarlo dejaría el micrófono creyendo que la
                // frase sigue abierta y "Procesando..." aguantaría hasta el
                // timeout, que después reportaría como fallida una frase que
                // nunca existió. No escribe nada en el campo: un `final` vacío
                // **no** puede vaciar lo que el usuario escribió a mano.
                if (event.text.isBlank()) {
                    if (event.final) onEmptyFinal()
                    return
                }
                // Cualquier cosa que vuelva prueba que el viaje de ida y vuelta
                // funciona, así que la red de seguridad de "no llegó la
                // transcripción" ya no hace falta.
                processingJob?.cancel()
                // Y también la de "no llegó la traducción": algo contesta.
                armTurnSilence()
                heardAnythingThisSession = true
                // Dictado y en vivo parten del mismo sitio —los mismos chunks y
                // el mismo STT— y se separan acá. Lo decide esta app, no el
                // backend: el `final` de lo dictado llega después de que el
                // usuario apagó el micrófono, así que el flag tiene que
                // sobrevivir al apagado ([dictationAwaitingFinal]).
                if (micMode == MicMode.Dictation || dictationAwaitingFinal) {
                    onDictationTranscript(event)
                } else {
                    onLiveTranscript(event)
                }
            }

            is BackendEvent.Segment -> onWholePhraseSegment(event)

            is BackendEvent.PhraseDone -> finishTurn()

            is BackendEvent.Failure -> fail(event.message)

            is BackendEvent.Disconnected -> onDisconnected()
        }
    }

    /**
     * Se perdió la sesión con el servidor. Es el estado *Desconectado* del SRS
     * (`main.tex:761-772`), y el texto que define su comportamiento es:
     *
     * > Se perdió la conexión con el servidor, lo que puede ocurrir en cualquier
     * > estado. El avatar vuelve a reposo, el indicador de estados lo indica y el
     * > sistema intenta reconectarse. […] El texto pendiente no se pierde. Si la
     * > conexión se pierde mientras una frase se está traduciendo y el avatar
     * > todavía no ha ejecutado ninguna seña de ella, esa frase se cancela y su
     * > texto vuelve a la barra de transcripción, editable, para que el usuario
     * > pueda mandarla de nuevo. Si la pérdida ocurre cuando el avatar ya está
     * > representando, la representación se interrumpe y el indicador vuelve a
     * > *Inactivo*.
     *
     * Son dos casos y el texto pendiente se trata distinto en cada uno, por lo
     * mismo que dice el documento de contraste §7: en el primero el usuario
     * **nunca vio** la frase representada, así que tiene que poder mandarla otra
     * vez; en el segundo ya salió del campo y se representa a medias, así que
     * devolverla al campo ofrecería un reenvío de algo que ya se mandó.
     *
     * Antes esto era `Unit`: la desconexión no hacía nada, y el síntoma era una app
     * trabada con el estado clavado, el botón Traducir deshabilitado y el envío
     * manual rechazado. Sin salida hasta cerrar la app a la fuerza.
     *
     * **El micrófono se apaga** (decisión del owner, 2026-10-17). El SRS dice que
     * en modo TR el micrófono permanece encendido (`main.tex:709`), así que esto
     * hay que documentarlo allá: cortar la captura es más honesto que dejar el
     * micrófono prendido grabando hacia un socket muerto, y le dice al usuario de
     * una vez que tiene que volver a prenderlo. Con el TR apagado, lo que se dijo
     * en vivo y no llegó a salir vuelve al campo para que lo mande a mano —"el
     * texto pendiente no se pierde"—.
     */
    private fun onDisconnected() {
        // Lo primero, antes de tocar nada: si hay un turno en vuelo, esta es la
        // última vez que se puede describir. Después `clearTurnState` borra la
        // prueba de que lo hubo.
        val interruptingSign = queuePlaying || playbackState == PlaybackState.Reproduciendo
        val cancellingPhrase = !interruptingSign && pendingText != null

        Log.i(
            TAG,
            "sesión perdida: interrumpiendo=$interruptingSign cancelando=$cancellingPhrase " +
                "micrófono=${micMode.name}",
        )

        if (interruptingSign) {
            // El avatar a reposo, de verdad: sin esto la interfaz volvía a
            // inactiva mientras el canvas seguía con la seña adelante.
            webView.stop()
            queuePlaying = false
            caption = ""
            turnQueueItems.clear()
        }

        if (micActive) {
            // Corte directo y no por `changeMicMode`: el modo saliente
            // mandaría un `end` por un socket que ya no existe, y `stopCapture`
            // dejaría el estado en Reproduciendo si había cola.
            audio.stop()
            micMode = MicMode.Off
            processingJob?.cancel()
        }

        // Lo que el usuario dijo y todavía no salió vuelve al campo. Es la misma
        // regla que RNF-REL-04: el texto pendiente no se pierde.
        val pendingLive = livePhraseBacklog.trim()
        livePhraseBacklog = ""
        if (pendingLive.isNotBlank()) {
            inputText = pendingLive
            transcript = pendingLive
            releaseSentText()
            dictationBuffer = pendingLive
            dictationPending = true
        }

        // El `final` de un dictado ya no va a llegar, así que el enrutado se cae.
        // El texto ya transcrito **no** se borra: sigue en `dictationBuffer`.
        dictationAwaitingFinal = false
        pendingText = null
        replayPending = false
        // La sesión se perdió: no va a llegar el `done` de nada, así que lo que
        // quedara de una frase cancelada también se cae, con el reloj.
        staleTurn = StaleTurn.None
        turnSilenceJob?.cancel()
        clearTurnState()

        if (cancellingPhrase) {
            // El usuario nunca vio esta frase representada: la retención se suelta
            // para que el campo quede editable y Traducir vuelva a estar
            // disponible en cuanto la conexión se restablezca.
            releaseSentText()
        }

        doneVisible = false
        // El estado se queda en *Desconectado* y **no** hay mensaje encima.
        //
        // Antes ponía `Inactivo` + un aviso de 3 s ("Se perdió la conexión… Se está
        // reconectando."), y como el reintento es cada 5 s los dos textos se
        // turnaban sin parar: la etiqueta de la tabla se veía 2 s, el aviso 3 s, y
        // vuelta a empezar. Un aviso que se enciende y se apaga cada 5 s no
        // informa nada, y el texto de la tabla quedaba pisado la mitad del tiempo.
        //
        // Ahora manda la etiqueta y no parpadea: *Desconectado* dice "Sin conexión
        // con el servidor" y sigue diciendo eso en cada intento, hasta que la
        // conexión vuelve.
        playbackState = PlaybackState.Desconectado
    }

    /**
     * Un `final` sin palabras.
     *
     * Llega cuando el STT cerró un utterance sobre ruido, o cuando el usuario
     * soltó el micrófono sin haber hablado. En los dos casos no hay frase que
     * representar y lo único que hay que hacer es **cerrar lo que estaba abierto**:
     *
     * - Si venía un dictado, la captura se apaga y el texto que sí se haya
     *   transcrito queda esperando confirmación.
     * - Si venía en vivo, el micrófono sigue prendido y no pasó nada.
     * - Si no había nada, no se toca nada: el campo conserva lo que el usuario
     *   escribió.
     */
    private fun onEmptyFinal() {
        processingJob?.cancel()
        Log.i(TAG, "final vacío: el utterance se cerró sin palabras")
        if (dictationAwaitingFinal || dictationActive) {
            dictationAwaitingFinal = false
            // Lo que haya en el campo es del dictado —a esta altura solo puede
            // serlo—, y el `final` vacío no lo borra: llegó un filtro que se comió
            // el audio, no una decisión del usuario (RNF-REL-04).
            dictationPending = inputText.isNotBlank()
            // El silencio ya cerró la frase, así que la captura se va sin `end`:
            // no hay utterance abierto que cerrar y mandarlo solo agrega un viaje.
            // Y `cancelInFlight = false`: acá el micrófono no lo apagó el usuario,
            // lo cerró el silencio, así que una traducción que esté en vuelo se
            // sigue esperando en vez de tirarse.
            if (dictationActive) stopDictation(endIsRedundant = true, cancelInFlight = false)
            playbackState = if (micActive) PlaybackState.Grabando else PlaybackState.Inactivo
        }
    }

    /**
     * Una transcripción en modo en vivo: se muestra mientras habla y, cuando la
     * frase se cierra, sale sola.
     *
     * El interruptor `TR` es la confirmación: el usuario lo prendió y lo que
     * quiere es que lo que habla se firme frase por frase. Por eso la frase se
     * manda como `text` —que es lo único que el backend traduce— y por eso no
     * hay ningún paso de "decidir" como en el dictado.
     *
     * El micrófono **no** se apaga en el `final`: es la excepción que hace TR
     * distinto del resto. Un `final` solo dice "esta frase se cerró"; la
     * siguiente empieza sola con el próximo audio.
     */
    private fun onLiveTranscript(event: BackendEvent.Transcript) {
        transcript = event.text
        inputText = event.text
        lastText = event.text
        if (!event.final) {
            // Parcial: solo se ve. `pendingText` lo pone el envío, no el texto.
            return
        }
        // La frase se manda sola. Si ya hay una en vuelo —el LLM tardar más que la
        // pausa de 1,5 s— queda esperando en un lugar, no se pierde: RNF-REL-01 no
        // es solo para el STT.
        queueLivePhrase(event.text)
    }

    /**
     * Una frase en vivo esperando hueco para salir.
     *
     * Un solo lugar, a propósito. El caso real es que la frase anterior siga
     * traduciéndose: con 1,5 s de pausa entre frases y ~0,8 s de traducción, es
     * raro pero pasa, y en ese caso lo que el usuario acaba de decir importa más
     * que la frase anterior. Con más de uno sería una cola que hay que vaciar, y
     * una cola que hay que vaciar es una cola que se pierde en algún `fail()`.
     */
    private var livePhraseBacklog = ""

    private fun queueLivePhrase(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (sendLivePhrase(clean)) {
            livePhraseBacklog = ""
            return
        }
        livePhraseBacklog = joinWords(livePhraseBacklog, clean)
        Log.i(TAG, "frase en vivo en espera (hay una en vuelo): $clean")
    }

    /**
     * Manda una frase en vivo. Devuelve false si no se pudo y hay que esperar.
     *
     * Es [requestTranslation] con dos diferencias, y las dos importan: no exige
     * apagar el micrófono —el modo en vivo se caracteriza por tenerlo prendido— y
     * no deja el texto retenido en el campo, porque Traducir ya está
     * deshabilitado mientras el micrófono captura y la retención solo haría que
     * el campo mostrara la frase anterior mientras el usuario sigue hablando.
     */
    private fun sendLivePhrase(text: String): Boolean {
        if (playbackState == PlaybackState.Procesando || pendingText != null) return false
        if (!backend.sendText(text)) {
            fail("No hay conexión con el servidor.")
            return false
        }
        pendingText = text
        isReplay = false
        // Un turno anterior no puede filtrarse a este.
        clearTurnState()
        // Si lo que sale es justamente lo que estaba esperando confirmación, ya no
        // está esperando: se mandó.
        //
        // Sin esto, dictar "X", pasar al TR y decir "X" otra vez dejaba el mismo
        // texto en los dos lados: el avatar lo representaba y, al apagar el TR,
        // `stopCapture` lo devolvía al campo como pendiente —marcado *Texto listo*,
        // con Traducir pidiendo mandarlo otra vez por una frase que ya se había
        // representado. El pendiente sobrevive solo cuando es **otro** texto: ese
        // sí es suyo y sin mandar.
        if (dictationBuffer.trim() == text.trim()) {
            Log.i(TAG, "el texto pendiente era esta misma frase: ya no está esperando")
            dictationBuffer = ""
            dictationPending = false
        }
        // Arranca **después** de limpiar: `clearTurnState` borra el reloj del turno
        // anterior, y el SRS mide desde que esta frase sale, no desde la anterior.
        turnSentAt = SystemClock.elapsedRealtime()
        playbackState = PlaybackState.Procesando
        // Nadie va a contestar un `done` viejo: a partir de ahora el `done` que
        // llegue es de esta frase. Ver [StaleTurn].
        if (staleTurn == StaleTurn.SentencesAndDone) {
            Log.i(TAG, "había una frase cancelada sin cerrar: se espera su fin antes de cerrar esta")
            staleTurn = StaleTurn.DoneOnly
        }
        armTurnSilence()
        Log.i(TAG, "en vivo → envío: $text")
        return true
    }

    /**
     * Una transcripción en modo dictado: va al input y a ningún otro lado.
     *
     * Acá no se arma cola ni se toca la escena: el resultado del dictado es
     * **texto**, y qué hacer con ese texto —representarlo, corregirlo, borrarlo— es
     * del usuario. Lo que llega es la hipótesis creciente de la frase que el
     * STT tiene abierta, así que se muestra en vivo pero solo se suma al
     * acumulado cuando llega su `final`.
     *
     * Cuando llega el `final` la frase está cerrada y **aca termina el
     * dictado**: la captura se apaga y el texto queda a la espera de que el
     * usuario confirme el envío (RF-STT-03, y el tercer estado de RF-UI-01).
     * Antes el `final` solo Guardaba el texto y quien disparaba el envío era
     * apagar el micrófono —"apagar es mandar"— y no había ningún instante entre
     * "se detuvo la captura" y "ya se mandó". Ese instante es el requisito.
     *
     * También funciona cuando el micrófono ya se apagó a mano: ese `final` es
     * el texto que el usuario estaba esperando, y es lo único que le faltaba
     * para poder apretar Traducir.
     */
    private fun onDictationTranscript(event: BackendEvent.Transcript) {
        val live = joinWords(dictationBuffer, event.text)
        // Solo palabras nuevas en el campo. Un `final` vacío no trae ninguna, y
        // escribir "" encima borraría lo que el usuario había tipeado antes de
        // prender el micrófono y no dijo nada: texto que nunca mandó y que no
        // tiene otra forma de recuperarse.
        if (live.isNotBlank()) {
            inputText = live
            transcript = live
        }
        if (!event.final) return

        dictationBuffer = live
        dictationAwaitingFinal = false
        // Un `final` vacío ya no llega acá —lo maneja [onEmptyFinal]—, así que
        // esto es una frase con palabras. Se cuenta igual para que el número de
        // frases dictadas siga siendo el de frases, no el de transcripciones.
        metrics.onDictationUtterance(event.text)
        // El silencio cerró la frase, así que la captura se va sin mandar `end`:
        // no hay utterance abierto que cerrar. Y `cancelInFlight = false` porque acá
        // el apagado no lo pidió el usuario: lo cerró el silencio, así que una
        // traducción en vuelo se sigue esperando en vez de tirarse.
        if (dictationActive) stopDictation(endIsRedundant = true, cancelInFlight = false)
        // El texto queda en el campo esperando confirmación. No se manda nada:
        // el envío es del usuario (RF-STT-03) y el que lo dispara es Traducir.
        dictationPending = live.isNotBlank()
        playbackState = PlaybackState.Inactivo
        // El aviso va en el hint del escenario y no en el campo: con la frase
        // escrita arriba, un placeholder no se ve nunca, que es justo cuando
        // hace falta. El hint es el lugar donde ya viven los mensajes
        // transitorios ("Ya está representado.").
        if (dictationPending) {
            showStatus("Texto listo. Toca Traducir para enviarlo.")
        } else {
            showStatus("No se escuchó nada.")
        }
        Log.i(
            TAG,
            "dictado cerrado: ${event.text.length} caracteres a la espera de confirmación",
        )
    }

    private fun joinWords(first: String, second: String): String =
        listOf(first, second)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    /**
     * Entrega a la escena las oraciones que todavía no se representararon.
     *
     * Se llama con cada `segment` y otra vez con el `done`, así que tiene que
     * ser idempotente: la primera llamada se lleva lo pendiente y sube
     * [flushedThrough], y las siguientes encuentran la lista vacía. También
     * descarta lo que haya quedado de una frase abandonada, para que un turno
     * que murió a la mitad no se filtre al siguiente.
     *
     * Que no espere a `total` es la decisión D-1. Lo que obliga a esperar ya no
     * aplica: la razón de GLS-1 —"para no ejecutar media frase"— venía del
     * camino por fragmentos, donde el backend mandaba palabras sueltas a mitad de
     * frase. Con oraciones, un `segment` **es** una oración completa, y esperar a
     * las demás solo deja al avatar parado con la primera seña ya en la mano
     * (medido: 569 ms con una frase fría de tres oraciones).
     */
    private fun flushSegments() {
        val fresh = pendingSegments.filterKeys { it > flushedThrough }.values.toList()
        if (fresh.isEmpty()) return
        flushedThrough = pendingSegments.keys.max()
        appendQueue(fresh)
    }

    /**
     * Sends a phrase to the backend. [replay] marks a Repetir, which must not
     * be recorded again as a new turn.
     *
     * Es el camino de todo lo que el usuario confirma: el Traducir del campo con
     * una frase escrita, el Traducir del campo con lo que dictó —el tercer estado
     * de RF-UI-01—, y el Repetir. El modo en vivo no pasa por acá: manda solo,
     * sin intervención del usuario, y lo hace en [sendLivePhrase].
     */
    private fun requestTranslation(text: String, replay: Boolean = false) {
        // El botón ya está deshabilitado mientras se escucha, pero `play` también
        // entra por la tecla Enter y por el "Repetir" del escenario. Texto y voz
        // van por el mismo socket: intercalados, el `index` de las oraciones deja
        // de corresponder al orden en que llegaron y el avatar representaría la frase
        // al revés. Son dos formas de mandar, no dos a la vez.
        if (micActive) {
            showStatus("Apagá el micrófono o el TR para mandar una frase escrita.")
            return
        }
        if (playbackState == PlaybackState.Procesando ||
            playbackState == PlaybackState.Procesando ||
            playbackState == PlaybackState.Desconectado ||
            playbackState == PlaybackState.Desconectado
        ) {
            return
        }
        // `Signing` no bloquea: el panel deja al usuario mandar otra frase con el
        // avatar representando la anterior, y la escena reemplaza la cola. Si acá
        // también se rechazara, el botón Traducir se vería habilitado y no haría
        // nada, que es peor que deshabilitarlo.
        if (!backend.sendText(text)) {
            fail("No hay conexión con el servidor.")
            return
        }
        pendingText = text
        lastText = text
        isReplay = replay
        // El panel entero queda deshabilitado desde el toque hasta que la seña
        // termina (`CTL-12`), para los dos Repetir: el del escenario y el de un
        // turno del panel de conversación. Se prende **acá** y no en
        // `replayTurn` porque arriba hay cuatro salidas tempranas —micrófono
        // prendido, frase en vuelo, sin conexión— y desde cualquiera de ellas el
        // panel se quedaría deshabilitado para siempre, sin Repetir que lo
        // vuelva a prender. `fail()` y `onPlaybackDone()` lo apagan.
        if (replay) replayPending = true
        inputText = text
        transcript = text
        statusOverride = null
        // The phrase left the field: it is not waiting for confirmation any more.
        dictationPending = false
        dictationBuffer = ""
        // Nothing of a previous phrase may survive into this one.
        clearTurnState()
        // El SRS mide el "tiempo hasta la primera seña" desde que el texto se manda
        // a traducir (`main.tex:848-852`), que es este momento.
        turnSentAt = SystemClock.elapsedRealtime()
        playbackState = PlaybackState.Procesando
        // Nadie va a contestar un `done` viejo: a partir de ahora el `done` que
        // llegue es de esta frase. Ver [StaleTurn].
        if (staleTurn == StaleTurn.SentencesAndDone) {
            Log.i(TAG, "había una frase cancelada sin cerrar: se espera su fin antes de cerrar esta")
            staleTurn = StaleTurn.DoneOnly
        }
        // Y el reloj: si en [TurnSilenceMs] no vuelve nada, esto es un fallo y no
        // una espera. Antes no había nada para una frase mandada, y por eso la app
        // se quedaba congelada.
        armTurnSilence()
        // Toda frase que sale de acá queda retenida en el campo: el Traducir con
        // lo dictado, el Traducir a mano y el Repetir. Sin esto, Traducir y
        // Repetir dejaban la frase en el campo con el botón habilitado y el avatar
        // firmaba la misma frase dos veces por un solo toque.
        holdSentText(text)
    }

    /**
     * Reinicia el reloj del turno y lo que el turno anterior dejó juntando.
     *
     * Se llama al empezar cada frase y en el fallo, para que un turno que murió a
     * la mitad no se filtre en el siguiente.
     *
     * [SignAiMetrics] **no** se reinicia acá: con el micrófono prendido una
     * sesión son varias frases, y los percentiles sirven más sobre la sesión
     * entera que por oración. Se reinician cuando se prende el micrófono.
     */
    private fun resetTurn() {
        turnStartedAt = SystemClock.elapsedRealtime()
        firstSegmentAt = 0L
        signStartReported = false
    }

    /**
     * Limpia lo que juntó el turno anterior: segmentos, cola entregada y conversación.
     *
     * Va junto con [resetTurn] y no dentro, porque no siempre que arranca un reloj
     * hay una frase nueva: `resumeListeningIfMicOn` también reinicia el reloj al
     * terminar una. Lo que no puede pasar es que las oraciones de un turno muerto
     * aparezcan en la cola del siguiente, así que esto se llama en los dos sitios.
     */
    private fun clearTurnState() {
        pendingSegments.clear()
        pendingTotal = 0
        flushedThrough = -1
        queueStarted = false
        // La cadena de entrega es por turno: el que empieza no espera al anterior,
        // que puede todavía estar bajando una seña que ya no importa. **Con valor**,
        // o sea ya completada: la primera tanda tiene que poder pasar de una.
        queueDeliveryGate = CompletableDeferred(Unit)
        turnQueueItems.clear()
        conversationTurnIndex = -1
        turnSentAt = 0L
        resetTurn()
    }

    private fun fail(message: String) {
        Log.i(TAG, "fallo: $message")
        pendingText = null
        // Y con ella lo que quedara de una frase cancelada: el fallo es de la
        // sesión, así que lo que venga después es de la sesión nueva.
        staleTurn = StaleTurn.None
        turnSilenceJob?.cancel()
        // The phrase this was building for is dead: its partial segments must
        // not be picked up by the next phrase.
        clearTurnState()
        // Cut the capture too, not just the flag: with the mic off in the UI but
        // `AudioRecord` still running, the next tap would find a recorder
        // already open and the app would look like it were listening while the
        // button says it is off.
        audio.stop()
        micMode = MicMode.Off
        // El texto dictado a la espera **no** se borra (RNF-REL-04): sigue en el
        // campo, editable, y Traducir vuelve a estar disponible cuando la
        // conexión se restablece. Lo que sí se cae es el enrutado de un `final`
        // que ya no va a llegar, y la retención del texto ya representado, que no llegó
        // a representarse y tiene que poder mandarse otra vez.
        dictationAwaitingFinal = false
        releaseSentText()
        // Y si lo que se cayó era un Repetir, el panel estaba deshabilitado
        // desde el toque esperando esta seña: sin esto se quedaba muerto, sin
        // un Repetir que lo vuelva a prender.
        replayPending = false
        queuePlaying = false
        doneVisible = false
        resetTurn()
        caption = ""
        // *Error* es un estado del SRS (`main.tex:726-773`) y antes no existía: los
        // fallos eran un mensaje de 3 s sobre la etiqueta de otro estado, así que
        // el indicador decía "Listo para traducir" mientras la app acababa de
        // fallar. El estado se va solo a [StatusOverrideMs] porque el mensaje se
        // va, y volver a Inactivo sin esperar sería mentir el doble.
        playbackState = PlaybackState.Error
        showStatus(message)
        armErrorReturn()
    }

    /**
     * Devuelve la interfaz a *Inactivo* cuando termina el aviso de un fallo.
     *
     * Va junto a [showStatus] porque las dos cosas duran lo mismo ([StatusOverrideMs])
     * y tienen que caerse juntas: si el estado volviera antes, el mensaje quedaría
     * colgado sobre "Listo para traducir"; si volviera después, el indicador
     * seguiría diciendo *Error* con la pantalla ya limpia.
     */
    private fun armErrorReturn() {
        errorReturnJob?.cancel()
        errorReturnJob = scope.launch {
            delay(StatusOverrideMs)
            // Solo si el estado sigue siendo Error: si en medio de los 3 s se mandó
            // una frase, se prende el micrófono o reconectó, lo que hay que
            // mostrar es eso y no un fallo viejo.
            if (playbackState == PlaybackState.Error) playbackState = PlaybackState.Inactivo
        }
    }

    private var errorReturnJob: Job? = null

    /**
     * Un aviso de 3 s en el indicador, **solo de depuración**.
     *
     * Ninguno de los textos que se producen acá está en la tabla
     * `tab:estados-ui` del SRS: "Ya está representado.", "No se escuchó nada.",
     * "Sin conexión: no puedo escuchar." y compañía son detalle de qué pasó, no
     * el estado en el que está la app. Con la depuración apagada —que es como
     * vive la app— el indicador dice únicamente el nombre del estado, que es lo
     * que pidió el owner el 2026-10-17.
     *
     * Los textos siguen existiendo, y ahí es donde se los lee: el log
     * (`SignAiState`). Por eso cada llamada guarda su mensaje en el log antes de
     * decidir si se escribe o no.
     *
     * El único texto propio que sí sale sin depuración es
     * "Seña no disponible", y no pasa por acá: lo pone [showMissingSign] porque la
     * tabla lo prescribe en la fila *Reproduciendo*.
     */
private fun showStatus(message: String, ms: Long = StatusOverrideMs) {
        Log.i(TAG, "aviso (no va al indicador): $message")
        if (!settings.debug) return
        writeStatusOverride(message, ms)
    }

    /**
     * "Seña no disponible": el texto alternativo de *Reproduciendo*.
     *
     * Sale **sin** depuración porque es lo único que la tabla pone en pantalla
     * además del nombre del estado (`main.tex:753`, fila *Reproduciendo*). Por
     * eso no pasa por [showStatus], que es el camino de los avisos de depuración.
     */
    private fun showMissingSign() {
        writeStatusOverride("Seña no disponible", StatusOverrideMs)
    }

    /** Escribe el texto transitorio y lo retira solo. [showStatus] / [showMissingSign]. */
    private fun writeStatusOverride(message: String, ms: Long) {
        statusOverride = message
        statusJob?.cancel()
        statusJob = scope.launch {
            delay(ms)
            statusOverride = null
        }
    }

    /**
     * Resolves the glosses to assets and hands the queue to the scene.
     *
     * A gloss the catalog doesn't know is not played (RF-CAT-03), unless
     * [Settings.demoSignFallback] is on. That switch is what makes a sign whose
     * clip is missing (none today) show something instead of "Seña no
     * disponible": the catalog announces it, but we have no clip for it, so the
     * queue falls back to the single sign baked into the APK.
     */
    private fun appendQueue(segments: List<TranslationSegment>) {
        // La escena no existe: entregarle una cola sería mandar instrucciones a un
        // canvas muerto, y el estado quedaría diciendo *Reproduciendo* sin que haya
        // nadie representando nada.
        if (sceneDead) {
            Log.i(TAG, "oración lista pero la escena 3D está muerta: no se entrega")
            return
        }
        val text = pendingText.orEmpty()
        // Cada tanda espera su turno de entrega, no su turno de resolución: los
        // assets se bajan en paralelo (que es lo que hace que una frase larga no
        // espere una descarga detrás de otra) pero **se entregan en orden de oración**.
        //
        // Sin esto la frase se representaba al revés en cuanto una oración tardaba
        // más en resolver que la siguiente —medido: una oración cuya seña tarda 2 s
        // en bajarse, con otra que llega y resuelve al instante: la segunda se
        // representaba primero—, y era peor todavía antes de esta cadena: las dos
        // salían como "la primera", las dos hacían `setQueue` y la segunda **pisaba**
        // la cola que la primera ya estaba representando, así que la frase se
        // cortaba a mitad sin que nada lo dijera.
        val myTurn = CompletableDeferred<Unit>()
        val previousTurn = queueDeliveryGate
        queueDeliveryGate = myTurn
        val job = scope.launch {
            val resolved = segments.map { queueItemsFor(it) }
            previousTurn.await()
            val items = resolved.flatMap { it.items }
            val first = !queueStarted

            if (first) {
                turnQueueItems.clear()
                // A replay is not a new turn: the phrase is already in the history
                // and already has a conversation entry, so recording it again would
                // duplicate both (`CNV-5`).
                if (text.isNotBlank() && !isReplay) {
                    syncHistory(history.record(text))
                    conversation.add(
                        ConversationTurn(
                            id = turnCounter++,
                            text = text,
                            gloss = formatGlosses(pendingSegments.values.toList()),
                        ),
                    )
                    conversationTurnIndex = conversation.size - 1
                }
            } else if (conversationTurnIndex >= 0) {
                // La entrada de conversación del turno se **completa** con las
                // oraciones que van llegando. Antes se armaba de una sola vez con
                // la frase entera; ahora la primera oración arranca la cola, así
                // que sin esto el panel quedaría mostrando solo la primera mitad
                // de lo que el avatar ya está representando.
                val turn = conversation[conversationTurnIndex]
                conversation[conversationTurnIndex] =
                    turn.copy(gloss = formatGlosses(pendingSegments.values.toList()))
            }

            turnQueueItems += items
            caption = turnQueueItems.joinToString(" · ") { it.gloss }.ifBlank { "Seña no disponible" }

            // Aviso por glosa, que es lo que pide el SRS (`main.tex:201` y `:753`):
            // "Seña no disponible" en vez de mandar al avatar una instrucción
            // inválida. Antes solo aparecía el texto cuando una oración entera no
            // produjo nada, así que una frase con tres glosas de cinco y dos
            // representables se saltaba en silencio lo que le faltaba.
            //
            // Este es el **único** texto propio que sale en el indicador sin
            // depuración, porque es el único que la tabla prescribe: la fila
            // *Reproduciendo* dice "Si alguna palabra no está en el catálogo, la
            // pantalla muestra el mensaje ``Seña no disponible''". Es el texto
            // alternativo de *Reproduciendo*, no un estado aparte, y por eso va
            // por [showMissingSign] y no por [showStatus]. Las glosas que faltaron
            // quedan en el log (`SignAiState`), que es donde se mira al depurar.
            val missing = resolved.flatMap { it.missing }
            if (missing.isNotEmpty()) {
                showMissingSign()
                Log.i(TAG, "glosas sin animación, avisadas: ${missing.joinToString(", ")}")
            }

            if (items.isEmpty()) {
                // Una oración que no produjo ninguna seña no es el fin del turno:
                // las siguientes pueden sí, así que acá no se cierra nada. El aviso
                // de arriba ya dijo cuáles fueron, y acá solo se corta.
                //
                // Y `queueStarted` **no** se prende: la escena no recibió nada, así
                // que la cola todavía no es suya. Antes sí se prendía, y eso
                // rompía dos cosas:
                //
                // - Si todas las oraciones del turno salían vacías, la app quedaba
                //   en "Procesando" para siempre: la escena nunca recibió la cola,
                //   nunca sonó y nunca avisó que terminó, así que el `done` del
                //   backend veía la cola "arrancada" y no cerraba el turno.
                // - Si la **primera** oración salía vacía y la segunda no, la
                //   segunda entraba por `appendQueue` sin que nadie hubiera llamado
                //   `setQueue` ni `play()`: la frase no se representaba entera.
                //
                // Con esto, la segunda oración sigue siendo la primera de verdad:
                // `setQueue` + `play()`, y la cola entera se representa.
                return@launch
            }

            if (first) {
                queueStarted = true
                webView.setQueue(items)
                webView.play()
                startPlaybackClock()
                queuePlaying = true
            } else {
                // La escena decide sola si hay que arrancar: `appendQueue` vuelve
                // a llamar `playFrom` si la cola ya se había vaciado mientras esta
                // oración se traducía (ver `app.js`). O viene sonando o arranca
                // después de este comando, así que en los dos casos queda
                // representando.
                webView.appendQueue(items)
                // El reloj solo se reinicia si venía de estar parado. Si venía
                // sonando, reiniciarlo haría que `representado:` midiera solo el
                // último tramo de la frase.
                if (!queuePlaying) startPlaybackClock()
                queuePlaying = true
            }
            playbackState = PlaybackState.Reproduciendo
        }
        // El contador que [finishTurn] mira antes de decidir el destino del turno, y
        // el final de la cadena de entrega. Va por fuera del `launch` para que el
        // cuerpo de arriba no se reescriba con un `try/finally` que no hace falta.
        appendsInFlight++
        scope.launch {
            job.join()
            appendsInFlight--
            // La cadena no puede quedar colgada: si esta tanda no llegó a entregar
            // —falló, o se canceló—, la siguiente tiene que poder seguir igual.
            myTurn.complete(Unit)
        }
    }

    /**
     * El backend cerró la frase (`done`): el turno está completo.
     *
     * Acá es donde se mide la frase entera —`metrics.onTurn` va del envío del
     * usuario a la última oración de vuelta— y donde se suelta la frase en vivo
     * que estuviera esperando. No antes: si se soltara con la primera oración,
     * en vivo podría mandar la frase siguiente mientras la anterior todavía se
     * está traduciendo, y dos `index` de oraciones intercalados hacen que el
     * avatar represente la frase al revés.
     */
    private fun finishTurn() {
        // `done` de una frase cancelada. Se traga sin tocar el turno vivo: si se
        // aplicara lo de abajo, cerraría la frase que el usuario mandó después.
        if (staleTurn != StaleTurn.None) {
            val keepTheLiveTurn = staleTurn == StaleTurn.DoneOnly
            staleTurn = StaleTurn.None
            Log.i(TAG, "fin de una frase cancelada: se descarta (turno vivo intacto=$keepTheLiveTurn)")
            if (!keepTheLiveTurn) {
                pendingText = null
                clearTurnState()
            }
            // Acá llegó algo, así que el reloj de silencio se rearma en vez de
            // cancelarse: si quedó una frase en vuelo, su propio reloj tiene que
            // seguir corriendo. Sin nada en vuelo, el reloj no dispara nada.
            armTurnSilence()
            // La frase que estaba esperando salida ya no tiene a nadie adelante, así
            // que sale ahora —igual que en el camino normal—.
            releaseLiveBacklog()
            return
        }

        // Hay oraciones todavía armando cola: el `done` llegó antes de que la escena
        // tuviera la frase. Decidir ahora sería decidir sobre un estado que todavía
        // no es el real, así que se espera a que se acomode y se vuelve a entrar
        // acá. Sin esto el turno se cerraba con `queueStarted` en `false` y decía
        // "no produjo ninguna seña" sobre una frase que sí se iba a representar.
        if (appendsInFlight > 0) {
            finishTurnWhenQueueSettles()
            return
        }

        val segments = pendingSegments.values.toList()
        // La frase en vuelo se guarda **antes** de limpiar `pendingText`: más abajo
        // se usa para saber si este `done` cierra un turno de verdad. Antes se
        // miraba `pendingText` después de haberlo puesto en null, así que la rama
        // de "la frase no produjo ninguna seña" nunca se tomaba y la app se
        // quedaba en "Procesando" para siempre.
        val fraseEnVuelo = pendingText
        if (segments.isNotEmpty()) {
            metrics.onTurn(
                totalMs = SystemClock.elapsedRealtime() - turnStartedAt,
                sentenceCount = segments.size,
                glossCount = segments.sumOf { it.glosses.size },
            )
            Log.i(TAG, "resumen: ${metrics.snapshot()}")
            pendingText = null
        }

        if (!queueStarted) {
            // Un `done` sin nada detrás. Cuando hay una frase en vuelo
            // (`pendingText` solo se pone cuando el backend se comprometió con una) quiere
            // decir que la frase no produjo ninguna seña, y sin esto el turno
            // se quedaría en "Procesando" para siempre. Sin frase en vuelo es un
            // `done` que llegó tarde o por segunda vez: la cola ya arrancó y no
            // hay nada que informar, así que cerrar acá mataría el turno.
            if (fraseEnVuelo != null) {
                pendingText = null
                if (micActive) {
                    showStatus("Esa frase no produjo ninguna seña.")
                    resumeListeningIfMicOn()
                } else {
                    // El estado también se cierra: sin esto la app se quedaba en
                    // "Procesando" para siempre, con el Traducir deshabilitado,
                    // cuando la frase no tenía ninguna seña que representar.
                    playbackState = PlaybackState.Inactivo
                    doneVisible = true
                    showStatus("Esa frase no produjo ninguna seña.")
                }
            }
        }

        // Hay una frase en espera de salida (el TR habló más rápido que el LLM).
        // Sale ahora que esta ya no está en vuelo.
        releaseLiveBacklog()
    }

    /**
     * El `done` llegó antes de que la cola estuviera lista: se espera y se reintenta
     * [finishTurn].
     *
     * El techo es [TurnSilenceMs] y no infinito a propósito: si un asset se queda
     * colgado, para entonces el reloj de silencio ya llamó [fail] y el turno ya está
     * cerrado, así que seguir esperando solo sería dejar trabajo colgando.
     */
    private fun finishTurnWhenQueueSettles() {
        Log.i(TAG, "el fin de la frase llegó con la cola todavía armándose: se espera")
        scope.launch {
            val deadline = SystemClock.elapsedRealtime() + TurnSilenceMs
            while (appendsInFlight > 0 && SystemClock.elapsedRealtime() < deadline) {
                delay(QueueSettlePollMs)
            }
            // El fallo o la desconexión ya cerraron el turno: no se toca más.
            if (playbackState == PlaybackState.Error ||
                playbackState == PlaybackState.Desconectado
            ) {
                return@launch
            }
            if (appendsInFlight > 0) Log.w(TAG, "la cola nunca terminó de armarse: se cierra igual")
            finishTurn()
        }
    }

    /**
     * Manda la frase en vivo que quedó esperando, si hay alguna.
     *
     * Se llama cuando la frase anterior dejó de estar en vuelo, que es lo único
     * que la bloqueaba. Un solo lugar de espera, así que "la anterior" es una.
     */
    private fun releaseLiveBacklog() {
        val waiting = livePhraseBacklog.trim()
        if (waiting.isEmpty()) return
        livePhraseBacklog = ""
        if (!sendLivePhrase(waiting)) {
            // Sigue sin poder salir: no hay dónde esperarlo, y una frase que no
            // se manda sin que el usuario lo decida es exactamente lo que
            // RF-STT-03 prohíbe. Se muestra para que la pueda mandar a mano.
            showStatus("No se pudo enviar la última frase. Toca Traducir.")
        }
    }

    private fun formatGlosses(segments: List<TranslationSegment>): String =
        segments.flatMap { it.glosses }.joinToString(" ") { it.text }

    // ── Micrófono ───────────────────────────────────────────────────────────
    //
    // Dos interruptores sobre una sola captura (spec `STA-16`/`STA-17`):
    //
    // - 🎤 **dictado** — se toca, se habla, se toca de nuevo. El texto se
    //   acumula en el input y el muñeco no se mueve hasta que se aprieta
    //   Traducir.
    // - ◢◢ **tiempo real** — se toca, el muñeco representa mientras se habla, se toca
    //   de nuevo para apagar.
    //
    // Ninguno de los dos es mantener apretado, y no pueden estar prendidos
    // juntos: comparten el `AudioRecord` y el WebSocket.

    /** El micrófono de la fila de arriba: dictado. */
    fun toggleDictation() =
        changeMicMode(if (dictationActive) MicMode.Off else MicMode.Dictation)

    /** El interruptor `TR` de la fila de abajo: en vivo. */
    fun toggleLive() =
        changeMicMode(if (liveActive) MicMode.Off else MicMode.Live)

    /**
     * Todo cambio de estado del micrófono pasa por acá.
     *
     * Son cuatro casos y no cinco porque cambiar de un modo al otro **no**
     * reinicia la captura: `AudioRecord` tarda cientos de ms en abrir y el
     * usuario ya estaba hablando. Se manda un `end` para que el STT cierre el
     * utterance del modo que se está dejando.
     */
    private fun changeMicMode(mode: MicMode) {
        val previous = micMode
        if (mode == previous) return

        if (!backend.isConnected) {
            if (mode != MicMode.Off) showStatus("Sin conexión: no puedo escuchar.")
            return
        }

        when {
            previous == MicMode.Off -> startCapture(mode)
            mode == MicMode.Off -> stopCapture(previous)
            else -> switchMode(previous, mode)
        }
    }

    /** Enciende la captura en un modo. */
    private fun startCapture(mode: MicMode) {
        statusOverride = null
        transcript = ""
        // El campo pasa a mostrar la transcripción en vivo, así que arranca
        // vacío: si quedara la frase anterior se leería como parte de lo que
        // está diciendo.
        inputText = ""
        // Y la retención de lo ya representado se cae con ella: la frase anterior ya
        // no está en el campo, así que no hay nada que proteger del reenvío.
        releaseSentText()
        // Prender el micrófono con texto dictado esperando **lo descarta**, y es
        // una cancelación explícita del usuario: tocó el micrófono para dictar de
        // nuevo, no para mandar lo anterior. RNF-REL-04 solo permite perder el
        // texto si el usuario lo borra o cancela a propósito, y esto es lo segundo.
        //
        // En el modo en vivo el texto NO se descarta: el campo pasa a mostrar la
        // transcripción en vivo y lo que quedó pendiente vuelve cuando el
        // interruptor se apaga ([stopCapture]). Ahí tocar el micrófono no es
        // "empezar de nuevo", es prender otro modo.
        if (mode == MicMode.Dictation) {
            dictationBuffer = ""
            dictationPending = false
        }
        micMode = mode
        playbackState = PlaybackState.Grabando
        // The turn clock starts when the mic opens, so the turn total includes the
        // silence before the first word — which is the number a user perceives.
        resetTurn()
        heardAnythingThisSession = false
        metrics.reset()
        Log.i(TAG, "micrófono prendido en modo ${mode.name}")

        audio.start(scope)
        if (!audio.isRecording) {
            // The device refused to open the mic (usually a missing permission).
            micMode = MicMode.Off
            playbackState = PlaybackState.Inactivo
            showStatus("No se pudo abrir el micrófono.")
        }
    }

    /** Cambia de modo sin cortar la captura. */
    private fun switchMode(from: MicMode, to: MicMode) {
        // El `end` va primero: cierra el utterance del modo que se deja. Lo que
        // Contestó después decide el modo al que va, y para eso está
        // `dictationAwaitingFinal`, que sobrevive al apagado.
        backend.sendEnd()
        Log.i(TAG, "micrófono: ${from.name} -> ${to.name}")

        micMode = to
        // Un cambio de modo entre los dos interruptores no borra nada de lo que el
        // usuario dijo: el texto pendiente sigue en `dictationBuffer` y la frase en
        // vivo que esperaba salida se manda sola con el interruptor que la pidió.
        dictationAwaitingFinal = false
        livePhraseBacklog = ""
        if (to == MicMode.Dictation) {
            // El campo pasa a ser la pantalla del dictado. Tocar el micrófono para
            // cambiar a este modo sí es empezar a dictar de nuevo, así que acá el
            // texto pendiente se va: es la cancelación explícita del usuario, la
            // misma que hay en `startCapture`.
            inputText = ""
            transcript = ""
            dictationBuffer = ""
            dictationPending = false
            releaseSentText()
        }
        resetTurn()
        heardAnythingThisSession = false
        metrics.reset()
        playbackState = PlaybackState.Grabando
    }

    /** Apaga el micrófono por completo. */
    private fun stopCapture(previous: MicMode) {
        Log.i(TAG, "micrófono apagado (venía de ${previous.name})")

        if (previous == MicMode.Dictation) {
            // El dictado tiene su propio cierre porque necesita saber si hay un
            // `final` esperando, y el `end` lo manda él.
            stopDictation()
            return
        }

        audio.stop()
        micMode = MicMode.Off
        // El STT cierra una frase cuando oye silencio, pero la captura se cortó
        // justo antes de que ese silencio llegara, así que hay que cerrarla a la
        // fuerza: si no, el `final` con lo que el usuario dijo nunca llega.
        backend.sendEnd()

        // El interruptor es también el botón de parar (decisión del owner): si hay
        // una frase esperando respuesta, apagarla la cancela. Sin esto, apagar el
        // TR con una frase en vuelo no sacaba a la app de *Procesando* —el apagado
        // solo apagaba el micrófono— y el botón de parar no paraba.
        val inFlight = pendingText
        if (inFlight != null) {
            cancelPhraseByMicOff(inFlight)
            return
        }

        // Apagar el TR deja el campo **limpio**.
        //
        // Lo que había ahí era la transcripción en vivo de lo que el micrófono
        // estaba oyendo, y para cuando se apaga el interruptor eso ya se mandó y
        // se representó: dejarlo puesto tapaba el campo con una frase que nadie puede
        // volver a mandar, y con el TR apagado Traducir vuelve a estar
        // disponible, así que el botón invitaba a reenviar lo que el avatar
        // acababa de representar. No hace falta retenerse para evitar eso —no hay nada
        // que retener si no hay texto— y el campo queda como el de un turno
        // recién empezado.
        //
        // Lo único que se conserva es lo que el usuario dijo **y no se mandó**: el
        // texto de un dictado anterior que seguía esperando confirmación. Ese no
        // es residuo de la sesión en vivo, es suyo y sin mandar, así que vuelve
        // al campo con su estado de "esperando confirmación" intacto.
        if (dictationBuffer.isNotBlank()) {
            inputText = dictationBuffer
            transcript = dictationBuffer
            dictationPending = true
        } else {
            inputText = ""
            transcript = ""
            releaseSentText()
            Log.i(TAG, "campo limpio: el TR se apagó y lo que había ya se representó")
        }

        // Nothing came back at all: the user turned the mic off without saying
        // anything (or in a room so quiet the VAD never fired). Going to
        // "Procesando..." here is fifteen seconds of waiting for a transcript
        // that does not exist, ending in an error about a failure that did not
        // happen. Back to Idle with a plain message instead.
        if (!heardAnythingThisSession) {
            Log.i(TAG, "el micrófono se apagó sin transcripción: no hay nada que traducir")
            resetTurn()
            playbackState = PlaybackState.Inactivo
            showStatus("No se escuchó nada.")
            return
        }

        // Aquí no hay nada que esperar. La captura se cortó, el `end` ya cerró el
        // utterance, y si no volvió nada fue porque no quedaba nada pendiente:
        // quedarse en "Procesando..." y el avatar representando lo que le sobraba era
        // el estado real, no una espera.
        //
        // La cola manda sobre el enum: el `done` de la última frase llega mucho
        // antes de que el avatar termine de representarla, así que el `playbackState`
        // ya diría "escuchando" con señas por delante. Con la cola como fuente de
        // verdad, apagar el micro devuelve la interfaz enseguida y el
        // `onPlaybackDone` prende el aviso de representación completada al terminar.
        if (queuePlaying) playbackState = PlaybackState.Reproduciendo
        else doneVisible = true
        processingJob?.cancel()
    }

    /**
     * El dictado se apagó. **Apagar no es mandar**: el texto espera.
     *
     * El `final` del STT es lo único que dice que la frase terminó de transcribirse,
     * así que hay que esperarlo: mientras no llegue, el campo muestra la hipótesis
     * que se está formando y el texto todavía puede cambiar. Cuando llegue, la
     * frase queda a la espera de que el usuario confirme el envío (RF-STT-03), que
     * es el paso que antes no existía.
     *
     * [endIsRedundant] es `true` cuando el `final` ya llegó —el silencio cerró la
     * frase—: ahí no hay utterance abierto que cerrar y mandarlo solo sumaría un
     * viaje de ida y vuelta y un `final` vacío de ruido.
     */
    private fun stopDictation(
        endIsRedundant: Boolean = false,
        cancelInFlight: Boolean = true,
    ) {
        audio.stop()
        micMode = MicMode.Off
        // Queda pendiente de `final`: aunque el usuario apague el micrófono sin
        // hablar, el STT responde siempre, y ese `final` tiene que seguir yendo al
        // camino del dictado. Con el silencio ya cerrado no hay nada que esperar.
        dictationAwaitingFinal = !endIsRedundant
        if (!endIsRedundant) backend.sendEnd()
        // Igual que en el TR, apagar el micrófono con una frase esperando la
        // cancela. Solo cuando el apagado es del usuario: cuando lo que cerró el
        // dictado fue el silencio, la frase en vuelo sigue siendo esperada y
        // cancelarla sería tirar una traducción que va a llegar.
        //
        // Y acá el texto **no** vuelve al campo: en dictado el campo es del
        // `final` que está por llegar y pisarlo perdería lo que el usuario acaba de
        // decir, que es más nuevo. La frase cancelada sigue en `lastText`, o sea
        // que el "Repetir" del escenario la puede volver a mandar.
        if (cancelInFlight) {
            pendingText?.let { cancelPhraseByMicOff(it, returnTextToField = false) }
        }
        Log.i(
            TAG,
            "dictado detenido${if (endIsRedundant) " (el silencio ya lo había cerrado)" else ""}: " +
                "esperando el final de la frase",
        )

        if (!heardAnythingThisSession) {
            Log.i(TAG, "el dictado se apagó sin transcripción")
            dictationAwaitingFinal = false
            resetTurn()
            playbackState = PlaybackState.Inactivo
            showStatus("No se escuchó nada.")
            return
        }

        playbackState = PlaybackState.Procesando
        armTranscriptionTimeout()
    }

    /**
     * Red de seguridad de "Procesando...": si en 15 s no volvió nada, es un
     * fallo (STT caído, audio perdido) y "Procesando..." tiene que ser una
     * espera, no un cuelgue.
     *
     * Lo que se pierde en ese caso es el `final`, no el texto: lo que se alcanzó a
     * transcribir ya está en el campo, así que el usuario puede mandarlo a mano.
     */
    private fun armTranscriptionTimeout() {
        processingJob?.cancel()
        processingJob = scope.launch {
            delay(TranscriptionTimeoutMs)
            if (playbackState == PlaybackState.Procesando && micMode == MicMode.Off) {
                dictationAwaitingFinal = false
                dictationPending = dictationBuffer.isNotBlank()
                fail("No llegó el final de la transcripción.")
            }
        }
    }

    /**
     * Red de seguridad de la frase mandada, en el otro sentido del socket.
     *
     * [armTranscriptionTimeout] cubre la transcripción; esta cubre la traducción.
     * Se arma cuando sale una frase ([requestTranslation], [sendLivePhrase]) y
     * **se rearma con cada frame que vuelve**: una oración, una transcripción, un
     * `done`. Por eso es un reloj de silencio y no un reloj de duración — una
     * frase de diez oraciones puede tardar treinta segundos y no es un fallo— y por
     * eso una frase que el backend contesta a medias no se declara muerta solo por
     * tardar.
     *
     * Cuando se cumple, [fail] hace todo lo que hace falta: corta el micrófono,
     * suelta el texto retenido para que el campo quede editable, apaga el panel de
     * un Repetir, muestra *Error* y vuelve a *Inactivo* a los 3 s. El usuario
     * siempre tiene salida.
     *
     * Lo que se pierde es la traducción, no el texto: la frase está en el campo y
     * se puede mandar otra vez.
     */
    private fun armTurnSilence() {
        turnSilenceJob?.cancel()
        turnSilenceJob = scope.launch {
            delay(TurnSilenceMs)
            val liveTurn = pendingText != null
            val discarding = staleTurn == StaleTurn.SentencesAndDone
            // Solo hay algo que esperar si hay una frase en vuelo, o si lo que se
            // canceló todavía no trajo su `done`. Cualquier otro estado significa
            // que algo ya lo resolvió: *Error* y *Desconectado* limpian los dos.
            if (!liveTurn && !discarding) return@launch
            if (!liveTurn) {
                // El `done` de una frase **cancelada** nunca llegó. Eso no es un
                // fallo que haya que mostrar: el usuario canceló, la app ya está en
                // *Inactivo* con el texto en el campo, y decir "el servidor no
                // respondió" quince segundos después de una cancelación sería un
                // error inventado. Lo único que hay que hacer es **olvidar el
                // descarte**: a partir de ahora el `done` que llegue es de una frase
                // nueva, y dejarlo prendido se tragaría el correcto.
                //
                // La carrera que queda —que el `done` viejo llegue después y cierre
                // el turno de la frase nueva— es el precio de que el protocolo no
                // permita cancelar una frase en el backend. Está anotada en
                // `../sign-ai-backend/docs/CONTRASTE-BACKENDvsDOCUMENTOS.md`.
                Log.i(TAG, "el fin de la frase cancelada nunca llegó: se olvida el descarte")
                staleTurn = StaleTurn.None
                return@launch
            }
            fail("El servidor no respondió.")
        }
    }

    /**
     * El usuario apagó el micrófono o el TR con una frase esperando respuesta.
     *
     * **Decisión del owner, 2026-10-17**: apagar el interruptor es el botón de
     * parar. Antes no sacaba a la app de *Procesando* —el apagado del TR no
     * escribía el estado, solo apagaba el micrófono—, así que el botón de parar no
     * paraba y la única red era el reloj de 15 s.
     *
     * Lo que se cancela es **local** ([StaleTurn]): el backend no tiene forma de
     * saber que la frase ya no interesa y sigue mandando lo que le falta.
     *
     * Los dos finales, igual que en [onDisconnected] y por el mismo motivo: si el
     * avatar ya estaba representando, el usuario **vio** parte de la frase, y
     * devolverla al campo ofrecería un reenvío de algo que ya se mandó. Si no había
     * ni una seña en pantalla, la frase no se vio nunca y tiene que poder mandarse
     * otra vez —RNF-REL-04: el texto que el usuario confirmó no se pierde—.
     *
     * [returnTextToField] es `false` en el dictado: ahí el campo es del `final` que
     * está por llegar y devolver ahí la frase cancelada perdería lo que el usuario
     * acaba de decir, que es más nuevo. La cancelada sigue en `lastText`.
     */
    private fun cancelPhraseByMicOff(text: String, returnTextToField: Boolean = true) {
        val interruptingSign = queuePlaying || playbackState == PlaybackState.Reproduciendo
        Log.i(
            TAG,
            "frase cancelada por el apagado del micrófono (seña en curso=$interruptingSign): \"$text\"",
        )

        if (interruptingSign) {
            // El avatar a reposo, como en la desconexión: sin esto el estado
            // volvía a inactivo con el canvas todavía en la seña a medias.
            webView.stop()
            queuePlaying = false
            caption = ""
            turnQueueItems.clear()
        }

        pendingText = null
        replayPending = false
        doneVisible = false
        clearTurnState()
        // Lo que el backend siga mandando de esta frase se descarta, y su `done` se
        // traga. El reloj **no** se cancela: es lo que garantiza que esto no se
        // quede esperando para siempre si ese `done` nunca llega.
        staleTurn = StaleTurn.SentencesAndDone
        armTurnSilence()
        processingJob?.cancel()

        releaseSentText()
        if (interruptingSign) {
            // Lo que ya se estaba representando no vuelve. Un dictado anterior que
            // siguiera esperando confirmación sí, como en el apagado normal.
            if (dictationBuffer.isNotBlank()) {
                inputText = dictationBuffer
                transcript = dictationBuffer
                dictationPending = true
            }
            playbackState = PlaybackState.Inactivo
            showStatus("Representación interrumpida.")
            return
        }
        if (!returnTextToField) {
            // El campo es de otro dueño —el `final` que está por llegar—, así que
            // la frase cancelada no vuelve a él. Queda en `lastText`.
            playbackState = PlaybackState.Inactivo
            showStatus("Frase cancelada.")
            return
        }

        // El usuario nunca la vio: la frase vuelve al campo, editable, con el
        // estado de "esperando confirmación" de una frase dictada —que es lo que
        // Traducir y el aviso de abajo ya saben mostrar.
        inputText = text
        transcript = text
        dictationBuffer = text
        dictationPending = true
        playbackState = PlaybackState.Inactivo
        showStatus("Frase cancelada. Toca Traducir para mandarla.")
    }

    /** Mic blocked by the user (RF-UI-04: say so, the app can't work without it). */
    fun onMicPermissionDenied() {
        micMode = MicMode.Off
        playbackState = PlaybackState.Inactivo
        showStatus("Sin permiso de micrófono la app no puede traducir voz.")
    }

    private fun sendAudio(chunk: ByteArray) {
        if (!backend.sendAudio(chunk)) {
            // Si la sesión ya se perdió, el aviso lo pone [onDisconnected] y el
            // estado ya está en *Desconectado*. Fallar otra vez acá arrancaba un
            // estado *Error* con su mensaje encima en medio de la reconexión, que
            // es el mismo parpadeo que se quitó del otro lado.
            if (playbackState != PlaybackState.Desconectado) {
                changeMicMode(MicMode.Off)
                fail("Se cortó la conexión mientras escuchaba.")
            }
        }
    }

    /** Repeats the last translated phrase (stage button). */
    fun replay() {
        if (lastText.isNotBlank()) replayTurn(lastText)
    }

    /** Repeats a specific conversation turn without adding a new turn (`CNV-5`). */
    fun replayTurn(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        requestTranslation(clean, replay = true)
    }

    /**
     * Repetir from the conversation drawer (`CNV-6`): closes the drawer, waits
     * `DrawerCloseDelayMs` so the closing animation is done, then replays that
     * turn. While the wait and the playback last the control panel is disabled
     * (`replayPending`).
     */
    fun repeatTurnFromDrawer(text: String) {
        openDrawer = null
        replayPending = true
        scope.launch {
            delay(DrawerCloseDelayMs)
            replayTurn(text)
        }
    }

    fun openDrawer(side: DrawerSide) {
        openDrawer = side
    }

    fun closeDrawer() {
        openDrawer = null
    }

    /**
     * Immediate, synchronous claim for the drawer gesture (spec `EDG-19`).
     *
     * `openDrawer` is reactive: a second finger lands before the state change has
     * recomposed the other tab, so two tabs could be pressed "at the same time"
     * and both open. This flag is read and written on the spot, in the touch
     * handler, so the second gesture is rejected in the same frame. Not Compose
     * state on purpose: it is a lock, it must not trigger recomposition.
     */
    private val drawerGestureOwner = AtomicReference<DrawerSide?>(null)

    /** Returns true when [side] may start a drawer gesture right now. */
    fun claimDrawerGesture(side: DrawerSide): Boolean =
        drawerGestureOwner.compareAndSet(null, side) ||
            drawerGestureOwner.get() == side

    /** Releases the lock if [side] still owns it. */
    fun releaseDrawerGesture(side: DrawerSide) {
        drawerGestureOwner.compareAndSet(side, null)
    }

    fun setInput(text: String) {
        // Con el micrófono prendido el campo está en `readOnly`, pero un teclado
        // que ya estaba abierto puede entregar texto igual. Se ignora en vez de
        // pisar la transcripción en vivo, que es lo que el campo está mostrando.
        // Con un Repetir en vuelo pasa lo mismo: el campo muestra la frase que se
        // está representando y un teclado olvidado la pisaría, soltando la retención
        // antes de tiempo.
        if (micActive || replayPending) return
        // El campo está en `readOnly` mientras el texto retenido está a la vista,
        // así que acá solo puede llegar un teclado que ya estaba abierto. Ese
        // texto es otra frase, no la que la app ya mandó, y por lo tanto no es un
        // reenvío: la retención se suelta.
        if (text != sentText) releaseSentText()
        inputText = text
        // Si el usuario borra lo que se transcribió, ya no hay nada listo para
        // enviar y el botón de voz tiene que volver al estado de "empezar a
        // dictar" (RF-UI-01). Editarlo sin vaciarlo no lo apaga: sigue siendo la
        // frase que va a confirmar.
        if (text.isBlank()) dictationPending = false
    }

    fun attachWebView(webView: WebView) {
        this.webView.attach(webView)
        // Best-effort; the bridge may not be ready yet, so this is repeated on load.
        syncWebSettings()
    }

    /** Pushes the current persisted settings to the WebView bridge. */
    fun syncWebSettings() {
        webView.setDebug(settings.debug)
        webView.setFreeCamera(settings.freeCam)
        webView.setGrid(settings.grid)
        webView.setBackground(settings.bg)
        webView.setTheme(settings.darkTheme)
        webView.setSpeed(settings.speed / 100f)
        webView.setReduceMotion(settings.reduceMotion)
    }

    /**
     * Sends a phrase: the backend translates it, the scene plays the returned
     * sequence, and `done` is driven by the real `onPlaybackDone` bridge event
     * (spec `STA-7`/`STA-8`/`CNV-4`).
     */
    fun play(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        requestTranslation(clean)
    }

    /**
     * La escena 3D dejó de existir: la página principal no cargó, o el navegador
     * perdió su proceso de render (`onReceivedError` / `onRenderProcessGone`).
     *
     * Antes la app no tenía este aviso, y el síntoma era el peor de la lista de
     * casos: el estado se quedaba en *Reproduciendo* con el muñeco quieto y sin
     * ninguna forma de salir.
     *
     * Lo que hace es un [fail]: se corta el micrófono (de nada sirve seguir
     * escuchando si no hay a quién mostrarle la frase), el texto vuelve al campo
     * editable, el panel se libera, y el estado dice *Error* y vuelve a *Inactivo*
     * a los 3 s. Y [sceneDead] queda prendido para que la app **no siga fingiendo**
     * que representa: no entrega colas nuevas ni vuelve a anunciar que una seña
     * empezó.
     *
     * Solo avisa una vez: el error de página y el de render pueden llegar juntos, y
     * dos *Error* seguidos son dos parpadeos.
     */
    fun onSceneFailed(reason: String) {
        if (sceneDead) return
        sceneDead = true
        Log.e(TAG, "la escena 3D dejó de funcionar: $reason")
        fail(reason)
    }

/**
     * Una seña de la frase no se pudo descargar y la escena la saltó
     * (`signFailed:<i>` del puente, `notifySignFailed` en `app.js`).
     *
     * Antes eso cortaba la frase entera en silencio: la escena paraba todo y
     * avisaba que el turno había terminado, y las demás señas de la frase no se
     * representaban nunca. Ahora la escena sigue, y lo único que falta es esta seña.
     *
     * En pantalla sale "Seña no disponible" —[showMissingSign]—, que es lo único
     * que la tabla de estados del SRS permite decir sobre el indicador cuando una
     * glosa no se puede representar (`main.tex:201` y `:753`). El detalle de cuál
     * fue queda en el log, como las glosas que el catálogo no tiene.
     */
    fun onSignFailed(queueIndex: Int) {
        Log.i(TAG, "la escena saltó la seña $queueIndex: no se pudo cargar su animación")
        showMissingSign()
    }

    /** Called by the JS bridge (`onPlaybackDone`, spec `BRG-7`/`STA-8`). */
    fun onPlaybackDone() {
        // Mensaje de una escena que ya está muerta: no hay reproducción que cerrar.
        if (sceneDead) return
        if (queuePlaying) {
            queuePlaying = false
            if (playbackStartedAt > 0L) {
                metrics.onPlayback(
                    totalMs = SystemClock.elapsedRealtime() - playbackStartedAt,
                    signCount = turnQueueItems.size,
                )
                playbackStartedAt = 0L
            }
            // El SRS tiene un solo estado de grabación, así que el enum no dice si se
            // está dictando o escuchando: eso lo pone [stateLabel].
            playbackState = PlaybackState.Grabando
            // El micrófono apagado no tiene a quién volver: el turno terminó y la
            // interfaz queda libre. El aviso de "reproducción completada" es un flag
            // (`doneVisible`), no un estado: el SRS no lo tiene entre los siete, y
            // con la cola del TR llena la app termina frases sin parar.
            if (micMode == MicMode.Off) {
                playbackState = PlaybackState.Inactivo
                // Si además hay un dictado esperando confirmación, el campo es lo
                // que hay que mirar y no hay nada que Completado todavía.
                if (!dictationPending) doneVisible = true
            }
        }
        caption = ""
        // The control panel stays disabled until the sign ends (`CNV-7`); clear
        // the flag here so it is released even while a drawer is closing.
        replayPending = false
    }

    fun toggle(setting: Setting, on: Boolean) {
        val updated = when (setting) {
            Setting.Debug -> settings.copy(debug = on)
            Setting.FreeCam -> settings.copy(freeCam = on)
            Setting.Grid -> settings.copy(grid = on)
            Setting.Background -> settings.copy(bg = on)
            Setting.Bold -> settings.copy(bold = on)
            Setting.Contrast -> settings.copy(contrast = on)
            Setting.Large -> settings.copy(large = on)
            Setting.ReduceMotion -> settings.copy(reduceMotion = on)
            Setting.DarkTheme -> settings.copy(darkTheme = on)
            Setting.DemoSignFallback -> settings.copy(demoSignFallback = on)
        }
        persist(updated)
        when (setting) {
            Setting.Debug -> webView.setDebug(on)
            Setting.FreeCam -> webView.setFreeCamera(on)
            Setting.Grid -> webView.setGrid(on)
            Setting.Background -> webView.setBackground(on)
            Setting.DarkTheme -> webView.setTheme(on)
            // La fundición entre señas es movimiento, y el ajuste que lo apaga
            // tiene que llegar a la escena además de a Compose: ahí es donde se
            // decide entre fundir y cortar.
            Setting.ReduceMotion -> webView.setReduceMotion(on)
            else -> Unit
        }
    }

    /**
     * Cambia el avatar en escena, solo en reposo absoluto.
     *
     * La selección se persiste **antes** de cargar para que el próximo arranque
     * abra directamente con el avatar elegido, y se revierte si la escena avisa
     * `avatarFailed:<id>`. La escena no borra el modelo anterior hasta que el
     * nuevo terminó de parsearse, así que un fallo deja al usuario con el
     * avatar que ya tenía.
     */
    fun setCharacter(character: Character) {
        if (character == settings.character) return
        if (!canSwitchAvatar) {
            showStatus("Para cambiar de avatar, dejá la app en reposo.")
            return
        }
        val previous = settings.character
        avatarBeforeSwitch = previous
        avatarSwitchStartedAt = SystemClock.elapsedRealtime()
        avatarLoading = true
        persist(settings.copy(character = character))
        webView.setAvatar(character.id)
        Log.i(TAG, "cambio de avatar: ${previous.id} -> ${character.id}")
    }

    /**
     * La escena terminó de cargar el avatar pedido (`avatarLoaded:<id>`).
     *
     * Se ignoran los avisos que no correspondan al avatar que el usuario tiene
     * seleccionado: un `avatarLoaded` viejo no debe destrabar un cambio nuevo.
     */
    fun onAvatarLoaded(id: String) {
        if (!avatarLoading || id != settings.character.id) {
            Log.i(TAG, "avatar cargado ($id) fuera de turno: se ignora")
            return
        }
        val elapsed = SystemClock.elapsedRealtime() - avatarSwitchStartedAt
        avatarLoading = false
        avatarBeforeSwitch = null
        Log.i(TAG, "avatar listo: $id en $elapsed ms")
        showStatus("Avatar cargado.")
    }

    /**
     * La escena no pudo cargar el avatar pedido (`avatarFailed:<id>`).
     *
     * El modelo anterior sigue en pantalla porque la escena lo descarta recién
     * cuando el nuevo está listo, así que alcanza con volver la preferencia y
     * liberar el panel. Si la falla es del avatar **inicial**, no hay anterior:
     * la escena ya avisa por su cuenta y el splash se destapa por timeout.
     */
    fun onAvatarFailed(id: String) {
        if (!avatarLoading || id != settings.character.id) {
            Log.w(TAG, "fallo de avatar ($id) fuera de turno: se ignora")
            return
        }
        val elapsed = SystemClock.elapsedRealtime() - avatarSwitchStartedAt
        val previous = avatarBeforeSwitch ?: Character.Juan
        avatarLoading = false
        avatarBeforeSwitch = null
        Log.e(TAG, "no se pudo cargar el avatar $id en $elapsed ms: se vuelve a ${previous.id}")
        persist(settings.copy(character = previous))
        showStatus("No se pudo cargar ese avatar. Se mantiene el anterior.")
    }

    fun setSpeed(speed: Int) {
        val clamped = speed.coerceIn(10, 100)
        persist(settings.copy(speed = clamped))
        webView.setSpeed(clamped / 100f)
    }

    fun setInterfaceSize(size: Int) {
        persist(settings.copy(interfaceSize = size.coerceIn(0, 100)))
    }

    private fun persist(newSettings: Settings) {
        settings = newSettings
        settingsStore.save(newSettings)
    }

    /**
     * Picks the sign shown when the catalog has no asset for a gloss (the demo
     * fallback), and the one the scene opens on.
     *
     * Telling the scene matters because it loads a sign when the page is ready:
     * the default is baked into `app.js`, so without this the very first
     * playback would use the old one no matter what the menu says.
     */
    fun pickSign(id: String) {
        if (id == sign) return
        sign = id
        webView.setDefaultSign(SignsCatalog.assetUrl(id))
    }

    fun setBlur(enabled: Boolean) {
        webView.applyBlur(enabled)
    }

    private fun syncHistory(list: List<String>) {
        historyList.clear()
        historyList.addAll(list)
    }

    init {
        connect()
        Log.i(TAG, "backend session starting")
    }
}
