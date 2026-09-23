package com.signai.webview.ui.webview

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

@Composable
fun AvatarWebView(
    modifier: Modifier = Modifier,
    onReady: (WebView) -> Unit,
    onProgress: (Int) -> Unit = {},
    onLoaded: () -> Unit = {},
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = {
            WebView(it).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        onProgress(newProgress)
                    }

                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                        android.util.Log.d(
                            "SignAIWeb",
                            "${message.messageLevel()}: ${message.message()} @${message.lineNumber()}",
                        )
                        return true
                    }
                }
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()
                webViewClient = object : WebViewClientCompat() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        onLoaded()
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: android.webkit.WebResourceRequest,
                    ): android.webkit.WebResourceResponse? {
                        val resp = assetLoader.shouldInterceptRequest(request.url) ?: return null
                        val headers = resp.responseHeaders ?: mutableMapOf()
                        headers["Cache-Control"] = "no-store"
                        resp.responseHeaders = headers
                        return resp
                    }
                }
                loadUrl("https://appassets.androidplatform.net/assets/web/index.html")
                onReady(this)
            }
        },
    )
}
