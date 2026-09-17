package com.signai.webview.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.signai.webview.ui.designsystem.components.Surface
import com.signai.webview.ui.designsystem.components.progressindicators.CircularProgressIndicator
import com.signai.webview.ui.theme.SignAiTheme

// Full-screen loading screen shown while the WebView finishes loading.
@Composable
fun SignAiSplash(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = SignAiTheme.colors.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = SignAiTheme.colors.primary)
        }
    }
}
