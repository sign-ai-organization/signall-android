package com.signai.webview

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList as SnapshotList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.width
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.signai.webview.ui.designsystem.components.Checkbox as SignAiCheckbox
import com.signai.webview.ui.designsystem.components.CheckboxColors
import com.signai.webview.ui.designsystem.components.SignAiEdgeDrawer
import com.signai.webview.ui.designsystem.components.SignAiDropdown
import com.signai.webview.ui.designsystem.components.SignAiDropdownMenuItem
import com.signai.webview.ui.designsystem.components.SignAiIcon
import com.signai.webview.ui.designsystem.components.IconButton as SignAiIconButton
import com.signai.webview.ui.designsystem.components.IconButtonVariant
import com.signai.webview.ui.designsystem.components.Surface as SignAiSurface
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.components.textfield.OutlinedTextField as SignAiOutlinedTextField
import com.signai.webview.ui.designsystem.components.textfield.OutlinedTextFieldDefaults as SignAiOutlinedTextFieldDefaults
import com.signai.webview.ui.icons.SignAiIcons
import com.signai.webview.ui.theme.SignAiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SignAiTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val history = remember {
        mutableStateListOf<String>().apply {
            val saved = context.getSharedPreferences("signai", Context.MODE_PRIVATE)
                .getString("history", "") ?: ""
            if (saved.isNotEmpty()) addAll(saved.split("\n"))
        }
    }
    var showSettings by remember { mutableStateOf(false) }
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    var debugOn by remember { mutableStateOf(false) }
    var freeCamOn by remember { mutableStateOf(false) }
    var gridOn by remember { mutableStateOf(true) }
    var bgOn by remember { mutableStateOf(false) }
    var sign by remember { mutableStateOf("0000_0000_0000") }

    LaunchedEffect(showSettings) {
        webViewRef.value?.let { wv ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                wv.setRenderEffect(
                    if (showSettings) RenderEffect.createBlurEffect(16f, 16f, Shader.TileMode.CLAMP)
                    else null
                )
            }
        }
    }

    val play: (String) -> Unit = { text ->
        if (text.isNotBlank()) {
            if (!(history.take(5).contains(text))) {
                history.remove(text)
                history.add(0, text)
                while (history.size > 20) history.removeAt(history.size - 1)
            }
        }
        context.getSharedPreferences("signai", Context.MODE_PRIVATE)
            .edit().putString("history", history.joinToString("\n")).apply()
        webViewRef.value?.evaluateJavascript("window.SignAI?.play()", null)
    }
    val sendJs: (String) -> Unit = { js ->
        webViewRef.value?.evaluateJavascript(js, null)
    }
    SignAiSurface(
        modifier = Modifier.fillMaxSize(),
        color = SignAiTheme.colors.bg,
    ) {
        SignAiEdgeDrawer(
            isOpen = showSettings,
            onOpenChange = { showSettings = it },
            drawerWidth = 300.dp,
            edgeStripBottomExclusion = bottomBarHeight,
            content = {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        AvatarWebView(Modifier.fillMaxSize(), onReady = { webViewRef.value = it })
                    }
                    Box(
                        Modifier.onSizeChanged {
                            bottomBarHeight = with(density) { it.height.toDp() }
                        },
                    ) {
                        BottomBar(
                            history = history,
                            onReplay = { play(it) },
                            onSend = { play(it) },
                            onMic = { play("mic") },
                        )
                    }
                }
            },
            drawerContent = {
                SettingsDrawerContent(
                    sendJs = sendJs,
                    debugOn = debugOn, onDebug = { debugOn = it },
                    freeCamOn = freeCamOn, onFreeCam = { freeCamOn = it },
                    gridOn = gridOn, onGrid = { gridOn = it },
                    bgOn = bgOn, onBg = { bgOn = it },
                    sign = sign, onSign = { sign = it },
                )
            },
        )
    }
}

private fun jsToggle(prop: String, on: Boolean) =
    "window.SignAI?.${prop}(!!${on})"

@Composable
private fun SettingsDrawerContent(
    sendJs: (String) -> Unit,
    debugOn: Boolean, onDebug: (Boolean) -> Unit,
    freeCamOn: Boolean, onFreeCam: (Boolean) -> Unit,
    gridOn: Boolean, onGrid: (Boolean) -> Unit,
    bgOn: Boolean, onBg: (Boolean) -> Unit,
    sign: String, onSign: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SignAiText(
            text = "Configuración",
            style = SignAiTheme.typography.titleMedium,
            color = SignAiTheme.colors.textMain,
        )
        Spacer(Modifier.height(8.dp))

        SettingCheck("Modo debug", debugOn) {
            onDebug(it)
            sendJs(jsToggle("setDebug", it))
        }
        SettingCheck("Cámara libre (orbitar + zoom sin límite)", freeCamOn) {
            onFreeCam(it)
            sendJs(jsToggle("setFreeCamera", it))
        }
        SettingCheck("Suelo (grilla)", gridOn) {
            onGrid(it)
            sendJs(jsToggle("setGrid", it))
        }
        SettingCheck("Fondo bosque", bgOn) {
            onBg(it)
            sendJs(jsToggle("setBackground", it))
        }

        Spacer(Modifier.height(8.dp))
        var expandedSign by remember { mutableStateOf(false) }
        val signs = listOf("0000_0000_0000")
        SignAiDropdown(
            expanded = expandedSign,
            onDismissRequest = { expandedSign = false },
            maxHeight = 200.dp,
            trigger = {
                SignAiSurface(
                    onClick = { expandedSign = !expandedSign },
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, SignAiTheme.colors.inputBorder),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        SignAiText(
                            text = "Seña: $sign",
                            color = SignAiTheme.colors.textMain,
                            maxLines = 1,
                        )
                    }
                }
            },
        ) {
            signs.forEach { s ->
                SignAiDropdownMenuItem(
                    text = if (s == sign) "✓ $s" else s,
                    onClick = {
                        expandedSign = false
                        onSign(s)
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignAiCheckbox(checked = checked, onCheckedChange = onChange, colors = signAiCheckboxColors())
        SignAiText(
            text = label,
            color = SignAiTheme.colors.textMain,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun signAiCheckboxColors(): CheckboxColors = CheckboxColors(
    checkedCheckmarkColor = Color.White,
    uncheckedCheckmarkColor = Color.Transparent,
    checkedBoxColor = SignAiTheme.colors.primary,
    uncheckedBoxColor = Color.Transparent,
    disabledCheckedBoxColor = SignAiTheme.colors.border,
    disabledUncheckedBoxColor = Color.Transparent,
    disabledIndeterminateBoxColor = SignAiTheme.colors.border,
    checkedBorderColor = SignAiTheme.colors.primary,
    uncheckedBorderColor = SignAiTheme.colors.border,
    disabledBorderColor = SignAiTheme.colors.border,
    disabledUncheckedBorderColor = SignAiTheme.colors.border,
    disabledIndeterminateBorderColor = SignAiTheme.colors.border,
)

@Composable
private fun AvatarWebView(modifier: Modifier = Modifier, onReady: (WebView) -> Unit) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            WebView(it).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                        android.util.Log.d("SignAIWeb", "${message.messageLevel()}: ${message.message()} @${message.lineNumber()}")
                        return true
                    }
                }
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()
                webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
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

@Composable
private fun BottomBar(
    history: SnapshotList<String>,
    onReplay: (String) -> Unit,
    onSend: (String) -> Unit = {},
    onMic: () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SignAiOutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    SignAiText(
                        text = "Versión: ${BuildConfig.VERSION_NAME}",
                        color = SignAiTheme.colors.textMain,
                    )
                },
                modifier = Modifier.weight(1f),
                colors = SignAiOutlinedTextFieldDefaults.colors().copy(
                    focusedTextColor = SignAiTheme.colors.textMain,
                    unfocusedTextColor = SignAiTheme.colors.textMain,
                    cursorColor = SignAiTheme.colors.primary,
                    focusedOutlineColor = SignAiTheme.colors.primary,
                    unfocusedOutlineColor = SignAiTheme.colors.inputBorder,
                    focusedContainerColor = SignAiTheme.colors.input,
                    unfocusedContainerColor = SignAiTheme.colors.input,
                ),
            )
            HistoryMenuButton(
                history = history,
                onReplay = { text ->
                    keyboard?.hide()
                    onReplay(text)
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val shape = RoundedCornerShape(6.dp)
            SignAiSurface(
                onClick = onMic,
                shape = shape,
                color = SignAiTheme.colors.input,
                border = BorderStroke(1.dp, SignAiTheme.colors.inputBorder),
                modifier = Modifier.weight(0.25f).height(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    SignAiIcon(SignAiIcons.Mic, contentDescription = "Micrófono", tint = SignAiTheme.colors.textMain)
                }
            }
            SignAiSurface(
                onClick = {
                    val text = query.trim()
                    query = ""
                    keyboard?.hide()
                    if (text.isNotEmpty()) onSend(text)
                },
                shape = shape,
                color = SignAiTheme.colors.primary,
                modifier = Modifier.weight(0.75f).height(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    SignAiIcon(SignAiIcons.Send, contentDescription = "Enviar", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun HistoryMenuButton(
    history: SnapshotList<String>,
    onReplay: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    SignAiDropdown(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.heightIn(max = 280.dp),
        trigger = {
            SignAiIconButton(
                onClick = { expanded = !expanded },
                variant = IconButtonVariant.Ghost,
            ) {
                SignAiIcon(
                    imageVector = SignAiIcons.History,
                    contentDescription = "Historial",
                    tint = SignAiTheme.colors.textMain,
                )
            }
        },
    ) {
        if (history.isEmpty()) {
            SignAiDropdownMenuItem(text = "Sin historial", onClick = {})
        } else {
            val maxChars = 10
            history.forEach { item ->
                val label = if (item.length > maxChars) item.take(maxChars) + "..." else item
                SignAiDropdownMenuItem(
                    text = label,
                    onClick = {
                        expanded = false
                        onReplay(item)
                    },
                )
            }
        }
    }
}
