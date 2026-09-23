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

class MainScreenState(
    private val history: SignHistoryRepository,
    private val webView: SignAiWebViewController,
    val signs: List<String> = SignsCatalog.available,
) {
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

    /** Mic placeholder (spec `CTL-5`/`STA-9`): toggles the listening feedback. */
    fun toggleMic() {
        playbackState =
            if (playbackState == PlaybackState.Listening) PlaybackState.Idle else PlaybackState.Listening
    }

    /** Repeats the last translated phrase (spec `STG-4`/`CNV-5`). */
    fun replay() {
        if (lastText.isNotBlank()) play(lastText)
    }

    fun attachWebView(webView: WebView) {
        this.webView.attach(webView)
    }

    fun play(text: String) {
        lastText = text
        syncHistory(history.record(text))
        webView.play()
    }

    fun toggle(setting: Setting, on: Boolean) {
        settings = when (setting) {
            Setting.Debug -> settings.copy(debug = on)
            Setting.FreeCam -> settings.copy(freeCam = on)
            Setting.Grid -> settings.copy(grid = on)
            Setting.Background -> settings.copy(bg = on)
        }
        when (setting) {
            Setting.Debug -> webView.setDebug(on)
            Setting.FreeCam -> webView.setFreeCamera(on)
            Setting.Grid -> webView.setGrid(on)
            Setting.Background -> webView.setBackground(on)
        }
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
