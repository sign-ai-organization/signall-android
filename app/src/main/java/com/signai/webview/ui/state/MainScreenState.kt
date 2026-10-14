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
     */
    private var heardAnythingThisTurn = false

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
            playbackState = PlaybackState.Done
            showStatus("Esa frase no produjo ninguna seña.")
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
                heardAnythingThisTurn = true
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
                    // The backend closed the segment, so this utterance is
                    // over: stop recording even if the user never toggled the
                    // mic off. Anything after this belongs to the next phrase.
                    audio.stop()
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
                playbackState = PlaybackState.Done
                showStatus("Esa frase no produjo ninguna seña.")
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
     */
    private fun resetStreamingTurn() {
        streamedSegments.clear()
        streamingGlosses.clear()
        turnStartedAt = SystemClock.elapsedRealtime()
        metrics.reset()
    }

    private fun fail(message: String) {
        pendingText = null
        // The phrase this was building for is dead: its partial segments must
        // not be picked up by the next phrase.
        pendingSegments.clear()
        pendingTotal = 0
        streamingTurn = false
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

    /**
     * Microphone capture (spec `CTL-5`/`STA-9`, paso 5 del plan). While
     * `listening` the raw PCM16 chunks go out on the same session as the typed
     * phrases; the backend transcribes them and the final transcript drives the
     * same path a typed phrase takes.
     *
     * The prototype's auto-fill is gone: it only made the app look like it was
     * listening while nothing was captured.
     */
    fun toggleMic() {
        if (playbackState == PlaybackState.Listening) {
            stopListening()
            return
        }
        if (playbackState == PlaybackState.Translating || playbackState == PlaybackState.Signing) {
            return
        }
        if (!backend.isConnected) {
            showStatus("Sin conexión: no puedo escuchar.")
            return
        }
        statusOverride = null
        transcript = ""
        playbackState = PlaybackState.Listening
        // The turn clock starts when the mic opens, so `rt_turn_total_ms`
        // includes the silence before the first word — which is the number a
        // user actually perceives.
        streamingTurn = false
        resetStreamingTurn()
        heardAnythingThisTurn = false
        audio.start(scope)
        if (!audio.isRecording) {
            // The device refused to open the mic (usually a missing permission).
            playbackState = PlaybackState.Idle
            showStatus("No se pudo abrir el micrófono.")
        }
    }

    /** Mic blocked by the user (RF-UI-04: say so, the app can't work without it). */
    fun onMicPermissionDenied() {
        playbackState = PlaybackState.Idle
        showStatus("Sin permiso de micrófono la app no puede traducir voz.")
    }

    private fun stopListening() {
        audio.stop()
        // The transcriber closes a segment by hearing silence. Stopping the
        // capture means that silence never arrives, so the segment has to be
        // closed explicitly — otherwise the app waits for a transcript that
        // will never come.
        backend.sendEnd()

        // Nothing came back at all: the user released the mic without saying
        // anything (or in a room so quiet the VAD never fired). Going to
        // "Procesando..." here is fifteen seconds of waiting for a transcript
        // that does not exist, ending in an error about a failure that did not
        // happen. Back to Idle with a plain message instead.
        if (!heardAnythingThisTurn) {
            Log.i(TAG, "el micrófono se soltó sin transcripción: no hay nada que traducir")
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
        // already signing by the time the user is allowed to release the mic, so
        // by this point a transcript has almost certainly landed. It stays
        // because "already signing" is not a guarantee.
        processingJob?.cancel()
        processingJob = scope.launch {
            delay(TranscriptionTimeoutMs)
            if (playbackState == PlaybackState.Processing) {
                fail("No llegó la transcripción. Probá de nuevo.")
            }
        }
    }

    private fun sendAudio(chunk: ByteArray) {
        if (!backend.sendAudio(chunk)) {
            stopListening()
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
            playbackState = PlaybackState.Done
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
