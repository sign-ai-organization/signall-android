package com.signai.webview.ui.state

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.signai.webview.data.SignHistoryRepository
import com.signai.webview.domain.SignsCatalog
import com.signai.webview.ui.webview.SignAiWebViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Prototype timing: `translating` lasts 850 ms before `signing` (spec `STA-7`). */
private const val TranslatingMs = 850L
private const val ListeningFillMs = 1_500L
private const val ProcessingMs = 800L

class MainScreenState(
    private val history: SignHistoryRepository,
    private val webView: SignAiWebViewController,
    val signs: List<String> = SignsCatalog.available,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val historyList: SnapshotStateList<String> = mutableStateListOf<String>().apply {
        addAll(history.load())
    }

    var settings by mutableStateOf(Settings())
        private set

    var sign by mutableStateOf(signs.firstOrNull().orEmpty())
        private set

    var playbackState by mutableStateOf(PlaybackState.Idle)
        private set

    var lastText by mutableStateOf("")
        private set

    /** Current phrase in the control panel input (shared with the history sheet). */
    var inputText by mutableStateOf("")
        private set

    /**
     * Mic placeholder (spec `CTL-5`/`STA-9`, `OM-4`): toggles `listening` and,
     * while active, auto-fills a demo phrase and runs `processing → idle`.
     */
    fun toggleMic() {
        if (playbackState == PlaybackState.Listening) {
            playbackState = PlaybackState.Idle
            return
        }
        if (playbackState == PlaybackState.Translating || playbackState == PlaybackState.Signing) {
            return
        }
        playbackState = PlaybackState.Listening
        scope.launch {
            delay(ListeningFillMs)
            if (playbackState != PlaybackState.Listening) return@launch
            inputText = "Quiero ir a la universidad"
            playbackState = PlaybackState.Processing
            delay(ProcessingMs)
            if (playbackState == PlaybackState.Processing) playbackState = PlaybackState.Idle
        }
    }

    /** Repeats the last translated phrase (spec `STG-4`/`CNV-5`). */
    fun replay() {
        if (lastText.isNotBlank()) play(lastText)
    }

    fun setInput(text: String) {
        inputText = text
    }

    fun attachWebView(webView: WebView) {
        this.webView.attach(webView)
        this.webView.setSpeed(settings.speed / 100f)
    }

    /**
     * Sends a phrase: `translating → signing` (850 ms) and then `done` once the
     * JS bridge reports the playback ended (spec `STA-7`/`STA-8`).
     */
    fun play(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (playbackState == PlaybackState.Translating || playbackState == PlaybackState.Signing) return
        lastText = clean
        inputText = clean
        syncHistory(history.record(clean))
        playbackState = PlaybackState.Translating
        webView.play()
        scope.launch {
            delay(TranslatingMs)
            if (playbackState == PlaybackState.Translating) playbackState = PlaybackState.Signing
        }
    }

    /** Called by the JS bridge (`onPlaybackDone`, spec `BRG-7`/`STA-8`). */
    fun onPlaybackDone() {
        if (playbackState == PlaybackState.Translating || playbackState == PlaybackState.Signing) {
            playbackState = PlaybackState.Done
        }
    }

    fun toggle(setting: Setting, on: Boolean) {
        settings = when (setting) {
            Setting.Debug -> settings.copy(debug = on)
            Setting.FreeCam -> settings.copy(freeCam = on)
            Setting.Grid -> settings.copy(grid = on)
            Setting.Background -> settings.copy(bg = on)
            Setting.Bold -> settings.copy(bold = on)
            Setting.Contrast -> settings.copy(contrast = on)
            Setting.Large -> settings.copy(large = on)
            Setting.ReduceMotion -> settings.copy(reduceMotion = on)
            Setting.DarkTheme -> settings.copy(darkTheme = on)
        }
        when (setting) {
            Setting.Debug -> webView.setDebug(on)
            Setting.FreeCam -> webView.setFreeCamera(on)
            Setting.Grid -> webView.setGrid(on)
            Setting.Background -> webView.setBackground(on)
            else -> Unit
        }
    }

    fun setCharacter(character: Character) {
        settings = settings.copy(character = character)
    }

    fun setSpeed(speed: Int) {
        val clamped = speed.coerceIn(10, 100)
        settings = settings.copy(speed = clamped)
        webView.setSpeed(clamped / 100f)
    }

    fun setInterfaceSize(size: Int) {
        settings = settings.copy(interfaceSize = size.coerceIn(0, 100))
    }

    fun pickSign(id: String) {
        sign = id
    }

    fun setBlur(enabled: Boolean) {
        webView.applyBlur(enabled)
    }

    private fun syncHistory(list: List<String>) {
        historyList.clear()
        historyList.addAll(list)
    }
}
