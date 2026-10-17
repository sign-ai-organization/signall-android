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

    var sign by mutableStateOf(signs.firstOrNull() ?: SignsCatalog.DEFAULT)
        private set

    /**
     * Playback fallback for a gloss we have no animation for — either one of
     * the 18 LSC50 signs still unexported, or an unknown gloss. See
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
     * [PlaybackState.Grabando] es el único cuyo texto depende de algo más que del
     * estado: el SRS tiene un solo estado de grabación, así que el enum no puede
     * decir si se está dictando o escuchando. Eso lo dice la etiqueta
     * (`main.tex:709` y el mapa de §7 del documento de contraste), y el usuario
     * sigue viendo en qué modo está.
     */
    val stateLabel: String
        get() = when (stateForUi) {
            PlaybackState.Grabando ->
                if (micMode == MicMode.Live) "Escuchando..." else "Dictando..."
            else -> stateForUi.label
        }

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
            statusOverride != null -> statusOverride!!
            playbackState == PlaybackState.Reproduciendo && caption.isNotBlank() -> caption
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
     * que las 18 señas de LSC50 sin animación exportada (0032–0049) muestren
     * algo en vez de "Seña no disponible": el catálogo las anuncia, pero no hay
     * clip, así que la cola cae en la única seña que viene dentro del APK.
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
            // Un `signs/*.glb` que **no** tenemos (el catálogo anuncia las 50
            // glosas pero solo 32 tienen export) es igual de indescargable: es
            // nuestro propio layout, así que tampoco lo sirve nadie por HTTP. Se
            // saltea el viaje de red y se va derecho al fallback. Cualquier otra
            // cosa —un asset remoto de verdad— sí baja por la red.
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
        clearTurnState()

        if (cancellingPhrase) {
            // El usuario nunca vio esta frase representada: la retención se suelta
            // para que el campo quede editable y Traducir vuelva a estar
            // disponible en cuanto la conexión se restablezca.
            releaseSentText()
        }

        doneVisible = false
        playbackState = PlaybackState.Inactivo
        showStatus("Se perdió la conexión con el servidor. Se está reconectando.")
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
            if (dictationActive) stopDictation(endIsRedundant = true)
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
        // no hay utterance abierto que cerrar.
        if (dictationActive) stopDictation(endIsRedundant = true)
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
        turnQueueItems.clear()
        conversationTurnIndex = -1
        turnSentAt = 0L
        resetTurn()
    }

    private fun fail(message: String) {
        Log.i(TAG, "fallo: $message")
        pendingText = null
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

    private fun showStatus(message: String, ms: Long = StatusOverrideMs) {
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
     * [Settings.demoSignFallback] is on. That switch is what makes the 18 LSC50
     * signs whose animation is not exported yet (0032–0049) show something
     * instead of "Seña no disponible": the catalog announces them, but we have
     * no clip for them, so the queue falls back to the single sign baked into
     * the APK.
     */
    private fun appendQueue(segments: List<TranslationSegment>) {
        val text = pendingText.orEmpty()
        val first = !queueStarted
        scope.launch {
            val resolved = segments.map { queueItemsFor(it) }
            val items = resolved.flatMap { it.items }

            if (first) {
                queueStarted = true
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
            // El texto es **exactamente** el del SRS, sin la lista de glosas: el
            // hint es de una línea y el aviso se lee de reojo, mientras el avatar
            // representa. Cuáles fueron queda en el log (`SignAiState`), que es
            // donde se mira cuando se depura.
            val missing = resolved.flatMap { it.missing }
            if (missing.isNotEmpty()) {
                showStatus("Seña no disponible")
                Log.i(TAG, "glosas sin animación, avisadas: ${missing.joinToString(", ")}")
            }

            if (items.isEmpty()) {
                // Una oración que no produjo ninguna seña no es el fin del turno:
                // las siguientes pueden sí, así que acá no se cierra nada. El aviso
                // de arriba ya dijo cuáles fueron, y acá solo se corta.
                return@launch
            }

            if (first) {
                webView.setQueue(items)
                webView.play()
                startPlaybackClock()
                queuePlaying = true
            } else {
                // La escena decide sola si hay que arrancar: `appendQueue` vuelve
                // a llamar `playFrom` si la cola ya se había vaciado mientras esta
                // oración se traducía (ver `app.js`). O viene sonando o arranca
                // después de este comando, así que en los dos casos queda
                // representing.
                webView.appendQueue(items)
                // El reloj solo se reinicia si venía de estar parado. Si venía
                // sonando, reiniciarlo haría que `representado:` midiera solo el
                // último tramo de la frase.
                if (!queuePlaying) startPlaybackClock()
                queuePlaying = true
            }
            playbackState = PlaybackState.Reproduciendo
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
        val segments = pendingSegments.values.toList()
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
            // decir que la frase no produjo ninguna oración, y sin esto el turno
            // se quedaría en "Traduciendo" para siempre. Sin frase en vuelo es un
            // `done` que llegó tarde o por segunda vez: la cola ya arrancó y no
            // hay nada que informar, así que cerrar acá mataría el turno.
            if (pendingText != null) {
                pendingText = null
                if (micActive) {
                    showStatus("Esa frase no produjo ninguna seña.")
                    resumeListeningIfMicOn()
                } else {
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
    private fun stopDictation(endIsRedundant: Boolean = false) {
        audio.stop()
        micMode = MicMode.Off
        // Queda pendiente de `final`: aunque el usuario apague el micrófono sin
        // hablar, el STT responde siempre, y ese `final` tiene que seguir yendo al
        // camino del dictado. Con el silencio ya cerrado no hay nada que esperar.
        dictationAwaitingFinal = !endIsRedundant
        if (!endIsRedundant) backend.sendEnd()
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

    /** Mic blocked by the user (RF-UI-04: say so, the app can't work without it). */
    fun onMicPermissionDenied() {
        micMode = MicMode.Off
        playbackState = PlaybackState.Inactivo
        showStatus("Sin permiso de micrófono la app no puede traducir voz.")
    }

    private fun sendAudio(chunk: ByteArray) {
        if (!backend.sendAudio(chunk)) {
            changeMicMode(MicMode.Off)
            fail("Se cortó la conexión mientras escuchaba.")
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

    /** Called by the JS bridge (`onPlaybackDone`, spec `BRG-7`/`STA-8`). */
    fun onPlaybackDone() {
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

    fun setCharacter(character: Character) {
        persist(settings.copy(character = character))
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
