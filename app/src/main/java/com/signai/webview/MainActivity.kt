package com.signai.webview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.signai.webview.data.SharedPrefsSignHistoryRepository
import com.signai.webview.ui.MainScreen
import com.signai.webview.ui.state.MainScreenState
import com.signai.webview.ui.theme.SignAiTheme
import com.signai.webview.ui.webview.WebViewSignAiController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val state = MainScreenState(
            history = SharedPrefsSignHistoryRepository(this),
            webView = WebViewSignAiController(),
        )
        setContent {
            SignAiTheme {
                MainScreen(state)
            }
        }
    }
}
