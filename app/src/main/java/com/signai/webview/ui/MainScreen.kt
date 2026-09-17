package com.signai.webview.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.components.SignAiSplash
import com.signai.webview.ui.designsystem.components.SignAiEdgeDrawer
import com.signai.webview.ui.designsystem.components.Surface
import com.signai.webview.ui.designsystem.foundation.SignAiSizes
import com.signai.webview.ui.screens.bottombar.SignAiBottomBar
import com.signai.webview.ui.screens.settings.SettingsSidebar
import com.signai.webview.ui.state.MainScreenState
import com.signai.webview.ui.theme.SignAiTheme
import com.signai.webview.ui.webview.AvatarWebView
import kotlinx.coroutines.delay

private const val SplashTimeoutMs = 8_000L
private const val ContentFadeMs = 500
private const val SplashFadeMs = 400

@Composable
fun MainScreen(state: MainScreenState) {
    var showSettings by remember { mutableStateOf(false) }
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    var loading by remember { mutableStateOf(true) }
    val density = LocalDensity.current

    LaunchedEffect(showSettings) {
        state.setBlur(showSettings)
    }

    // Safety net: never leave the app stuck on the splash if the WebView fails
    // to load (e.g. no internet for the Three.js CDN).
    LaunchedEffect(Unit) {
        delay(SplashTimeoutMs)
        loading = false
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (loading) 0f else 1f,
        animationSpec = tween(durationMillis = ContentFadeMs, easing = LinearEasing),
        label = "contentAlpha",
    )

    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha },
            color = SignAiTheme.colors.background,
        ) {
            SignAiEdgeDrawer(
                isOpen = showSettings,
                onOpenChange = { showSettings = it },
                drawerWidth = SignAiSizes.drawerWidth,
                edgeStripBottomExclusion = bottomBarHeight,
                content = {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) {
                            AvatarWebView(
                                modifier = Modifier.fillMaxSize(),
                                onReady = state::attachWebView,
                                onLoaded = { loading = false },
                            )
                        }
                        Box(
                            Modifier.onSizeChanged {
                                bottomBarHeight = with(density) { it.height.toDp() }
                            },
                        ) {
                            SignAiBottomBar(
                                history = state.historyList,
                                onReplay = state::play,
                                onSend = state::play,
                                onMic = { state.play("mic") },
                            )
                        }
                    }
                },
                drawerContent = {
                    SettingsSidebar(
                        settings = state.settings,
                        onToggle = state::toggle,
                        sign = state.sign,
                        signs = state.signs,
                        onPickSign = state::pickSign,
                    )
                },
            )
        }

        AnimatedVisibility(
            visible = loading,
            enter = EnterTransition.None,
            exit = fadeOut(animationSpec = tween(durationMillis = SplashFadeMs)),
        ) {
            SignAiSplash()
        }
    }
}
