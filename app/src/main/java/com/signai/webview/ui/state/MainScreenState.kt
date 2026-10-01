package com.signai.webview.ui.state

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
     * Playback fallback while the backend's catalog is still empty — see
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

    /** True when the turn in flight is a Repetir, which must not be re-recorded. */
    private var isReplay = false

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
                transcript = event.text
                inputText = event.text
                // A final transcript means the backend heard a whole utterance.
                // The translation follows on its own, but this is the phrase
                // the turn has to be recorded with: a typed one already set
                // `pendingText`, a spoken one is only known now.
                if (event.final && pendingText == null) {
                    pendingText = event.text
                    // `lastText` is what Repetir replays. A typed phrase set it
                    // when it was sent; a spoken one is only known now, and
                    // without this the button appeared after a mic phrase and
                    // did nothing.
                    lastText = event.text
                    // The backend closed the segment, so this utterance is
                    // over: stop recording even if the user never toggled the
                    // mic off. Anything after this belongs to the next phrase.
                    audio.stop()
                    playbackState = PlaybackState.Translating
                }
            }

            is BackendEvent.Translation -> {
                transcript = pendingText ?: transcript
                startQueue(event.segments)
            }

            is BackendEvent.Failure -> fail(event.message)

            is BackendEvent.Disconnected -> Unit
        }
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
        playbackState = PlaybackState.Translating
    }

    private fun fail(message: String) {
        pendingText = null
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
     * [Settings.demoSignFallback] is on: while the catalog is empty every gloss
     * is unknown, and that switch keeps the multi-sign queue visible using the
     * single sign baked into the APK. It is a development crutch to be turned
     * off once the backend serves real animations.
     */
    private fun startQueue(segments: List<TranslationSegment>) {
        val text = pendingText.orEmpty()
        scope.launch {
            val queue = mutableListOf<SignQueueItem>()
            for (segment in segments) {
                for (gloss in segment.glosses) {
                    val url = if (gloss.available) {
                        assets.resolve(gloss.text, gloss.glbPath)
                    } else {
                        null
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
        playbackState = PlaybackState.Processing
        // Safety net: no transcript is a failure mode (STT down, audio lost),
        // and "Procesando..." has to be a wait, not a hang.
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
