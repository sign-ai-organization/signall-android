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

/** Reconnect backoff, multiplied by the attempt count (`RF-INT-COM-01`). */
private const val ReconnectBaseMs = 1_000L
private const val MaxReconnectAttempts = 5

/** How long a transient message stays in the stage hint. */
private const val StatusOverrideMs = 3_000L

private const val TAG = "SignAiState"

class MainScreenState(
    private val history: SignHistoryRepository,
    private val settingsStore: SettingsRepository,
    private val webView: SignAiWebViewController,
    private val backend: SignAiWebSocketClient,
    private val assets: SignAssetRepository,
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

    var playbackState by mutableStateOf(PlaybackState.Idle)
        private set

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
     * True from a conversation Repetir tap until the replayed sign ends: keeps
     * the control panel disabled (`CNV-7`).
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
     * Segments of the phrase being translated, keyed by their position.
     *
     * The backend streams one segment per sentence and they all belong to the
     * same phrase, so they are collected here instead of being played as they
     * arrive: the avatar signs the phrase as one queue, and a sentence that
     * needs an extra LLM round trip cannot leave it signing half a phrase.
     */
    private val pendingSegments = sortedMapOf<Int, TranslationSegment>()

    /** How many segments the phrase has; 0 until the first one says so. */
    private var pendingTotal = 0

    /** True when the turn in flight is a Repetir, which must not be re-recorded. */
    private var isReplay = false

    // ── Modo fragmentado (tiempo real) ───────────────────────────────────────
    // El backend ya no espera a tener la frase entera: manda un fragmento
    // apenas el transcriber confirma que esas palabras se dijeron. Estos campos
    // son el estado del turno en curso; se limpian en el `done`.
    private var streamingTurn = false
    private val streamedSegments = mutableListOf<TranslationSegment>()
    private val streamingGlosses = mutableListOf<String>()

    /** Wall clock of the current turn, for the timing logs and `metrics`. */
    private var turnStartedAt = SystemClock.elapsedRealtime()

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
     * cosas distintas —"¿está el mic prendido?" y "¿qué hace el muñeco?"— y
     * eso solo funcionaba porque el micro se apagaba en cada frase. Ahora el
     * muñeco firma con el micrófono prendido, así que los dos se superponen de
     * verdad y necesitan su propio estado.
     */
    var micMode by mutableStateOf(MicMode.Off)
        private set

    /** El micrófono está capturando, en cualquiera de los dos modos. */
    val micActive: Boolean get() = micMode != MicMode.Off

    /** El micrófono de la fila de arriba: dictado, solo transcribe. */
    val dictationActive: Boolean get() = micMode == MicMode.Dictation

    /** El interruptor `TR` de la fila de abajo: firma mientras se habla. */
    val realtimeActive: Boolean get() = micMode == MicMode.Realtime

    /**
     * Texto acumulado del dictado en curso.
     *
     * El STT cierra cada frase cuando oye silencio, así que una sesión de
     * dictado son varias transcripciones `final`. Si solo se conservara la
     * última, dictar "holá" / "cómo estás" con una pausa en el medio perdería
     * la primera frase.
     *
     * Sobrevive a que se apague el micrófono: es lo que quedó escrito, y si
     * después se prende el modo tiempo real el campo vuelve a mostrar esto al
     * apagarlo. Perder un dictado de tres frases porque el usuario quiso probar
     * el tiempo real sería la peor forma de que esto salga mal.
     */
    private var dictationBuffer = ""

    /**
     * Whether the backend session is up.
     *
     * The mic button greys out without it, because tapping it otherwise produces
     * a status message and nothing else. Exposed as Compose state because the
     * connection can drop while the app is open (`RF-INT-COM-01`).
     */
    val backendConnected: Boolean get() = backend.isConnected

    /**
     * Keeps streamed fragments in the order the backend sent them.
     *
     * Resolving a gloss may suspend (a remote asset has to be downloaded), and
     * without this two fragments racing would append out of order and the avatar
     * would sign the phrase backwards.
     */
    private val streamMutex = Mutex()

    /** What the stage hint shows: the glosses while signing, else state or message. */
    val statusLine: String
        get() = when {
            statusOverride != null -> statusOverride!!
            playbackState == PlaybackState.Signing && caption.isNotBlank() -> caption
            else -> playbackState.label
        }

    // ── Backend session ─────────────────────────────────────────────────────

    /** Opens the translation session and keeps it open, reconnecting on failure. */
    fun connect() {
        connectJob?.cancel()
        connectJob = scope.launch {
            var attempt = 0
            while (isActive) {
                if (playbackState == PlaybackState.Idle) {
                    playbackState = PlaybackState.Connecting
                }
                backend.events().collect { event -> handleEvent(event) }
                // The flow only ends when the session dropped, so this is the
                // reconnect path: back off, then try again.
                if (playbackState != PlaybackState.Translating &&
                    playbackState != PlaybackState.Signing
                ) {
                    playbackState = PlaybackState.Offline
                }
                attempt++
                delay(ReconnectBaseMs * attempt.coerceAtMost(MaxReconnectAttempts))
            }
        }
    }

    /**
     * A streamed fragment: sign it now, do not wait for the rest of the phrase.
     *
     * This is the whole point of the real-time mode. The backend sends a
     * fragment as soon as the transcriber confirmed those words had been said,
     * so by the time it arrives the person may already be on their second word.
     * The queue is appended to instead of replaced, and playback starts from the
     * first fragment's index, which is what lets the avatar sign the phrase
     * while it is still being spoken.
     */
    private fun onStreamedSegment(event: BackendEvent.Segment) {
        val startedAt = SystemClock.elapsedRealtime()
        streamingTurn = true
        streamedSegments += event.segment
        streamingGlosses += event.segment.glosses.map { it.text }

        // Resolving a remote asset is a suspending download, and the queue is
        // order-dependent: appending fragment 2 before fragment 1 would sign the
        // phrase backwards. The mutex makes the fragments queue up in the order
        // the backend sent them.
        scope.launch {
            streamMutex.withLock {
                val items = queueItemsFor(event.segment)
                if (items.isEmpty()) {
                    // A fragment of words the catalog does not cover. Not an error:
                    // the turn keeps going and the next fragment may resolve to
                    // real signs.
                    metrics.onUnavailableGloss()
                    Log.i(TAG, "fragmento ${event.index} sin señas disponibles: ${event.sourceText}")
                    return@withLock
                }

                if (playbackState != PlaybackState.Signing) {
                    // First fragment: nothing is playing yet, so this starts the
                    // queue.
                    webView.setQueue(items)
                    webView.playFrom(0)
                    caption = items.joinToString(" · ") { it.gloss }
                } else {
                    // Mid-playback: append. `playFrom` is deliberately not
                    // called, the scene advances on its own as the queue grows.
                    webView.appendQueue(items)
                    caption = (caption + " · " + items.joinToString(" · ") { it.gloss }).trim('·', ' ')
                }
                playbackState = PlaybackState.Signing
                replayPending = false
                metrics.onFragment(SystemClock.elapsedRealtime() - startedAt, items.size)
            }
        }
    }

    /**
     * Closes a streamed turn once the backend says `done`.
     *
     * The avatar keeps signing whatever is left in the queue: `done` means the
     * backend stopped sending, not that the animation ended. Recording the turn
     * happens here rather than per fragment, because only now is the phrase
     * complete and only now is it worth a history entry.
     */
    private fun finishStreamedTurn() {
        val text = pendingText.orEmpty()
        val segments = streamedSegments.toList()

        if (segments.isEmpty()) {
            streamingTurn = false
            pendingText = null
            if (micActive) {
                // Nada que firmar, pero el mic sigue prendido: se sigue
                // escuchando, no terminó nada.
                showStatus("Esa frase no produjo ninguna seña.")
                resumeListeningIfMicOn()
            } else {
                playbackState = PlaybackState.Done
                showStatus("Esa frase no produjo ninguna seña.")
            }
            return
        }

        if (text.isNotBlank() && !isReplay) {
            syncHistory(history.record(text))
            conversation.add(
                ConversationTurn(
                    id = turnCounter++,
                    text = text,
                    gloss = formatGlosses(segments),
                ),
            )
        }

        metrics.onTurn(
            totalMs = SystemClock.elapsedRealtime() - turnStartedAt,
            fragmentCount = segments.size,
            glossCount = streamingGlosses.size,
        )
        Log.i(TAG, "resumen: ${metrics.snapshot()}")

        streamingTurn = false
        streamedSegments.clear()
        streamingGlosses.clear()
        pendingText = null
        resumeListeningIfMicOn()
    }

    /**
     * Devuelve la interfaz a "escuchando" o "dictando" cuando el mic sigue
     * abierto.
     *
     * Una frase terminó y el avatar va a terminar de firmarla, pero el mic sigue
     * prendido: el siguiente `done` tiene que devolver el estado a la etiqueta
     * del modo que está activo, o el botón se vería apagado mientras está
     * capturando, y el usuario no sabría si tiene que tocarlo.
     *
     * También reinicia el reloj del turno. Con el mic prendido, una sesión son
     * varias frases y cada una mide por separado: el tiempo desde que terminó
     * la anterior hasta que terminó esta.
     */
    private fun resumeListeningIfMicOn() {
        if (!micActive) return
        resetStreamingTurn()
        playbackState =
            if (realtimeActive) PlaybackState.Listening else PlaybackState.Dictating
    }

    /** Whole-phrase mode: collect every sentence, then sign the phrase as a unit. */
    private fun onWholePhraseSegment(event: BackendEvent.Segment) {
        transcript = pendingText ?: transcript
        pendingTotal = maxOf(pendingTotal, event.total)
        pendingSegments[event.index] = event.segment
        // Play as soon as the phrase is whole. `done` only arrives after
        // the last segment, so once every segment is in there is nothing
        // left to wait for — and a phrase the backend never closes with
        // `done` still gets signed.
        if (pendingTotal > 0 && pendingSegments.size >= pendingTotal) flushSegments()
    }

    /** Resolves one fragment's glosses to queue items, same rules as a whole phrase. */
    private suspend fun queueItemsFor(segment: TranslationSegment): List<SignQueueItem> {
        val items = mutableListOf<SignQueueItem>()
        for (gloss in segment.glosses) {
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
                // Unknown gloss, no fallback: not queued. See startQueue for why.
            }
        }
        return items
    }

    private fun handleEvent(event: BackendEvent) {
        when (event) {
            is BackendEvent.Connected -> {
                if (playbackState == PlaybackState.Connecting ||
                    playbackState == PlaybackState.Offline
                ) {
                    playbackState = PlaybackState.Idle
                }
            }

            is BackendEvent.Transcript -> {
                if (event.text.isBlank()) return
                // Anything coming back means the round trip is alive, so the
                // "no transcript arrived" guard is no longer needed.
                processingJob?.cancel()
                heardAnythingThisSession = true
                // Dictado y tiempo real parten exactamente del mismo sitio —los
                // mismos chunks de audio y el mismo STT— y se separan acá: en
                // dictado el texto es el resultado, en tiempo real es el material
                // del que salen las señas.
                //
                // Manda el backend, no el estado local: el `final` de lo dictado
                // llega después de que el usuario apagó el micrófono, y para
                // entonces esta app ya cree que no está dictando.
                if (event.dictation) {
                    onDictationTranscript(event)
                    return
                }
                transcript = event.text
                inputText = event.text
                // The utterance starts at the *first* text, not at the final:
                // in streamed mode the avatar is already signing by then, and
                // `pendingText` has to exist before the first fragment so the
                // turn is recorded against the right phrase.
                if (pendingText == null) {
                    pendingText = event.text
                    lastText = event.text
                }
                if (event.final) {
                    // El micrófono **no** se apaga acá.
                    //
                    // Antes lo hacía, y obligaba a tocar de nuevo para seguir
                    // hablando: una frase por pulsación. Con el modo tiempo real
                    // eso es al revés de lo que se quiere, porque el STT abre
                    // solo el siguiente utterance en cuanto hears silencio y el
                    // avatar puede seguir firmando sin que nadie toque nada.
                    // Un `final` solo dice "esta frase se cerró"; la siguiente
                    // empieza sola con el próximo audio.
                    if (!streamingTurn) playbackState = PlaybackState.Translating
                }
            }

            is BackendEvent.Segment ->
                if (event.stream) {
                    onStreamedSegment(event)
                } else {
                    onWholePhraseSegment(event)
                }

            is BackendEvent.PhraseDone -> {
                // En modo fragmentado la cola ya se fue firmando; acá solo queda
                // cerrar el turno. En modo frase completa, `flushSegments` arma la
                // cola de una.
                if (streamingTurn) finishStreamedTurn() else flushSegments()
            }

            is BackendEvent.Failure -> fail(event.message)

            is BackendEvent.Disconnected -> Unit
        }
    }

    /**
     * Una transcripción en modo dictado: va al input y a ningún otro lado.
     *
     * Acá no se arma cola ni se toca la escena: el resultado del dictado es
     * **texto**, y qué hacer con ese texto —firmarlo, corregirlo, borrarlo— es
     * del usuario. Lo que llega es la hipótesis creciente de la frase que el
     * STT tiene abierta, así que se muestra en vivo pero solo se suma al
     * acumulado cuando llega su `final`.
     *
     * También funciona cuando el dictado ya se apagó: ese `final` es el texto
     * que el usuario estaba esperando, y es lo único que le faltaba para
     * apretar Traducir.
     */
    private fun onDictationTranscript(event: BackendEvent.Transcript) {
        val live = joinWords(dictationBuffer, event.text)
        inputText = live
        transcript = live
        if (!event.final) return

        dictationBuffer = live
        metrics.onDictationUtterance(event.text)
        // Con el micrófono ya apagado no hay nada más que esperar: el texto
        // quedó en el input. Si sigue prendido, el estado es el del modo y no
        // hay que tocarlo.
        if (!dictationActive) playbackState = PlaybackState.Idle
    }

    private fun joinWords(first: String, second: String): String =
        listOf(first, second)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    /**
     * Turns the collected segments into a playback queue and starts signing.
     *
     * Reached both when the last segment lands and when `done` does, so it has
     * to be idempotent: the first caller takes the segments and every later one
     * finds the map empty. It also drops anything left over from an abandoned
     * phrase, so a failed turn cannot leak into the next one.
     */
    private fun flushSegments() {
        val segments = pendingSegments.values.toList()
        pendingSegments.clear()
        pendingTotal = 0

        if (segments.isEmpty()) {
            // A `done` with nothing behind it. When a phrase really is in flight
            // (`pendingText` is only set once the backend has committed to one)
            // it means the phrase produced no segments at all, and without this
            // the turn would sit in "Traduciendo" forever. Without a phrase in
            // flight it is a `done` that arrived late or twice — the queue
            // already started — so there is nothing to report and ending the
            // current turn here would kill it.
            if (pendingText != null) {
                pendingText = null
                if (micActive) {
                    showStatus("Esa frase no produjo ninguna seña.")
                    resumeListeningIfMicOn()
                } else {
                    playbackState = PlaybackState.Done
                    showStatus("Esa frase no produjo ninguna seña.")
                }
            }
            return
        }
        startQueue(segments)
    }

    /**
     * Sends a phrase to the backend. [replay] marks a Repetir, which must not
     * be recorded again as a new turn.
     */
    private fun requestTranslation(text: String, replay: Boolean = false) {
        // El botón ya está deshabilitado mientras se escucha, pero `play` también
        // entra por la tecla Enter y por el "Repetir" del escenario. Texto y voz
        // van por el mismo socket y el backend no puede saber de quién es cada
        // fragmento: intercalados, el avatar firmaría la frase al revés.
        if (micActive) {
            showStatus("Apagá el micrófono o el TR para mandar una frase escrita.")
            return
        }
        if (playbackState == PlaybackState.Translating ||
            playbackState == PlaybackState.Signing ||
            playbackState == PlaybackState.Connecting ||
            playbackState == PlaybackState.Offline
        ) {
            return
        }
        if (!backend.sendText(text)) {
            fail("No hay conexión con el servidor.")
            return
        }
        pendingText = text
        lastText = text
        isReplay = replay
        inputText = text
        transcript = text
        statusOverride = null
        // Nothing of a previous phrase may survive into this one.
        pendingSegments.clear()
        pendingTotal = 0
        resetStreamingTurn()
        playbackState = PlaybackState.Translating
    }

    /**
     * Clears everything a streamed turn accumulated.
     *
     * Called at the start of every new phrase and on failure, so a turn that
     * died halfway cannot leak its fragments into the next one. `streamingTurn`
     * itself is left alone by [resetStreamingTurn] because the caller decides
     * whether a turn is starting or ending.
     *
     * [SignAiMetrics] is deliberately **not** reset here: with the mic left on,
     * one session is many turns, and the percentiles are more useful across the
     * whole session than per sentence. They reset when the mic is switched on.
     */
    private fun resetStreamingTurn() {
        streamedSegments.clear()
        streamingGlosses.clear()
        turnStartedAt = SystemClock.elapsedRealtime()
    }

    private fun fail(message: String) {
        Log.i(TAG, "fallo: $message")
        pendingText = null
        // The phrase this was building for is dead: its partial segments must
        // not be picked up by the next phrase.
        pendingSegments.clear()
        pendingTotal = 0
        streamingTurn = false
        // Cut the capture too, not just the flag: with the mic off in the UI but
        // `AudioRecord` still running, the next tap would find a recorder
        // already open and the app would look like it were listening while the
        // button says it is off.
        audio.stop()
        micMode = MicMode.Off
        resetStreamingTurn()
        caption = ""
        playbackState = PlaybackState.Idle
        showStatus(message)
    }

    private fun showStatus(message: String) {
        statusOverride = message
        statusJob?.cancel()
        statusJob = scope.launch {
            delay(StatusOverrideMs)
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
    private fun startQueue(segments: List<TranslationSegment>) {
        val text = pendingText.orEmpty()
        scope.launch {
            val queue = mutableListOf<SignQueueItem>()
            for (segment in segments) {
                for (gloss in segment.glosses) {
                    // First ask whether the path is one of the animations baked
                    // into the APK. They are same-origin by construction, so
                    // downloading them would be a guaranteed 404 that lands us
                    // in the fallback below — and the fallback is the default
                    // sign, so every gloss would answer with the same movement.
                    //
                    // A `signs/*.glb` we do NOT ship (the catalog announces all
                    // 50 glosses but only 32 have an export) is just as
                    // undownloadable: it is our own layout, so nobody is
                    // serving it over HTTP either. Skip the round trip and go
                    // straight to the fallback. Anything else — a real remote
                    // asset — still goes down the download path.
                    val local = SignsCatalog.localAssetOrNull(gloss.glbPath)
                    val url = when {
                        local != null -> local
                        SignsCatalog.isOwnAssetPath(gloss.glbPath) -> null
                        gloss.available -> assets.resolve(gloss.text, gloss.glbPath)
                        else -> null
                    }
                    when {
                        url != null -> queue += SignQueueItem(
                            gloss = gloss.text,
                            type = segment.type,
                            url = url,
                            available = true,
                        )

                        settings.demoSignFallback -> queue += SignQueueItem(
                            gloss = gloss.text,
                            type = segment.type,
                            url = fallbackSignUrl,
                            available = gloss.available,
                        )
                        // Unknown gloss, no fallback: reported in the caption.
                    }
                }
            }

            // A replay is not a new turn: the phrase is already in the history
            // and already has a conversation entry, so recording it again would
            // duplicate both (`CNV-5`).
            if (text.isNotBlank() && !isReplay) {
                syncHistory(history.record(text))
                conversation.add(
                    ConversationTurn(
                        id = turnCounter++,
                        text = text,
                        gloss = formatGlosses(segments),
                    ),
                )
            }
            pendingText = null
            caption = queue.joinToString(" · ") { it.gloss }.ifBlank { "Seña no disponible" }
            showStatus(
                if (queue.isEmpty()) {
                    "Ninguna seña de esta frase está en el catálogo todavía"
                } else {
                    ""
                },
            )

            if (queue.isEmpty()) {
                playbackState = PlaybackState.Done
                replayPending = false
                return@launch
            }

            webView.setQueue(queue)
            webView.play()
            playbackState = PlaybackState.Signing
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
    // - ◢◢ **tiempo real** — se toca, el muñeco firma mientras se habla, se toca
    //   de nuevo para apagar.
    //
    // Ninguno de los dos es mantener apretado, y no pueden estar prendidos
    // juntos: comparten el `AudioRecord` y el WebSocket.

    /** El micrófono de la fila de arriba: dictado. */
    fun toggleDictation() =
        changeMicMode(if (dictationActive) MicMode.Off else MicMode.Dictation)

    /** El interruptor `TR` de la fila de abajo: tiempo real. */
    fun toggleRealtime() =
        changeMicMode(if (realtimeActive) MicMode.Off else MicMode.Realtime)

    /**
     * Todo cambio de estado del micrófono pasa por acá.
     *
     * Son cuatro casos y no cinco porque cambiar de un modo al otro **no**
     * reinicia la captura: `AudioRecord` tarda cientos de ms en abrir y el
     * usuario ya estaba hablando. Se manda un `end` para que el backend cierre
     * el utterance del modo que se está dejando y se le avisa el modo nuevo;
     * el `end` va primero a propósito, porque el backend termina ese utterance
     * con el modo con el que empezó.
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
        if (mode == MicMode.Dictation) dictationBuffer = ""
        micMode = mode
        backend.sendMode(realtime = mode == MicMode.Realtime)
        playbackState = labelFor(mode)
        // The turn clock starts when the mic opens, so `rt_turn_total_ms`
        // includes the silence before the first word — which is the number a
        // user actually perceives.
        streamingTurn = false
        resetStreamingTurn()
        heardAnythingThisSession = false
        metrics.reset()
        Log.i(TAG, "micrófono prendido en modo ${mode.name}")

        audio.start(scope)
        if (!audio.isRecording) {
            // The device refused to open the mic (usually a missing permission).
            micMode = MicMode.Off
            playbackState = PlaybackState.Idle
            showStatus("No se pudo abrir el micrófono.")
        }
    }

    /** Cambia de modo sin cortar la captura. */
    private fun switchMode(from: MicMode, to: MicMode) {
        // El `end` va primero: cierra el utterance del modo que se deja y el
        // backend lo termina con ese mismo modo, así que la última frase de
        // tiempo real se firma igual y lo dictado no se firma solo.
        backend.sendEnd()
        backend.sendMode(realtime = to == MicMode.Realtime)
        Log.i(TAG, "micrófono: ${from.name} -> ${to.name}")

        micMode = to
        if (to == MicMode.Dictation) {
            // El campo pasa a ser la pantalla del dictado.
            inputText = ""
            transcript = ""
            dictationBuffer = ""
        }
        streamingTurn = false
        resetStreamingTurn()
        heardAnythingThisSession = false
        metrics.reset()
        playbackState = labelFor(to)
    }

    /** Apaga el micrófono por completo. */
    private fun stopCapture(previous: MicMode) {
        Log.i(TAG, "micrófono apagado (venía de ${previous.name})")
        audio.stop()
        micMode = MicMode.Off
        // El STT cierra una frase cuando oye silencio, pero la captura se cortó
        // justo antes de que ese silencio llegara, así que hay que cerrarla a la
        // fuerza: si no, el `final` con lo que el usuario dijo nunca llega.
        backend.sendEnd()

        // Con TR apagado, lo que quedó en el campo es lo último que firmaba el
        // muñeco. Si hubo dictado, lo que el usuario escribió tiene más
        // interest: es lo único que puede volver a mandar.
        if (previous == MicMode.Realtime && dictationBuffer.isNotBlank()) {
            inputText = dictationBuffer
            transcript = dictationBuffer
        }

        // Nothing came back at all: the user turned the mic off without saying
        // anything (or in a room so quiet the VAD never fired). Going to
        // "Procesando..." here is fifteen seconds of waiting for a transcript
        // that does not exist, ending in an error about a failure that did not
        // happen. Back to Idle with a plain message instead.
        if (!heardAnythingThisSession) {
            Log.i(TAG, "el micrófono se apagó sin transcripción: no hay nada que traducir")
            streamingTurn = false
            resetStreamingTurn()
            playbackState = PlaybackState.Idle
            showStatus("No se escuchó nada.")
            return
        }

        playbackState = PlaybackState.Processing
        // Safety net: no transcript is a failure mode (STT down, audio lost),
        // and "Procesando..." has to be a wait, not a hang.
        //
        // In streamed mode this is much less likely than it was: the avatar is
        // already signing by the time the user turns the mic off, so by this
        // point a transcript has almost certainly landed. It stays because
        // "already signing" is not a guarantee.
        processingJob?.cancel()
        processingJob = scope.launch {
            delay(TranscriptionTimeoutMs)
            if (playbackState == PlaybackState.Processing && micMode == MicMode.Off) {
                fail("No llegó la transcripción. Probá de nuevo.")
            }
        }
    }

    /** La etiqueta de la pista para un modo: lo que el usuario está haciendo. */
    private fun labelFor(mode: MicMode): PlaybackState =
        if (mode == MicMode.Realtime) PlaybackState.Listening else PlaybackState.Dictating

    /** Mic blocked by the user (RF-UI-04: say so, the app can't work without it). */
    fun onMicPermissionDenied() {
        micMode = MicMode.Off
        playbackState = PlaybackState.Idle
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
        if (playbackState == PlaybackState.Translating || playbackState == PlaybackState.Signing) return
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
        if (micActive) return
        inputText = text
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
    }

    /**
     * Sends a phrase: the backend translates it, the scene plays the returned
     * sequence, and `done` is driven by the real `onPlaybackDone` bridge event
     * (spec `STA-7`/`STA-8`/`CNV-4`).
     */
    fun play(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (playbackState == PlaybackState.Translating || playbackState == PlaybackState.Signing) return
        requestTranslation(clean)
    }

    /** Called by the JS bridge (`onPlaybackDone`, spec `BRG-7`/`STA-8`). */
    fun onPlaybackDone() {
        if (playbackState == PlaybackState.Translating || playbackState == PlaybackState.Signing) {
            // Con el micrófono prendido no "terminó todo": terminó esta seña y el
            // usuario sigue hablando. Volver a `Listening` es lo que deja el
            // botón de micrófono mostrando que sigue abierto.
            playbackState =
                when (micMode) {
                    MicMode.Realtime -> PlaybackState.Listening
                    MicMode.Dictation -> PlaybackState.Dictating
                    MicMode.Off -> PlaybackState.Done
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
