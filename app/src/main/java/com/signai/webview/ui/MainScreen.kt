package com.signai.webview.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.signai.webview.domain.Gloss
import com.signai.webview.ui.components.SignAiSplash
import com.signai.webview.ui.designsystem.components.DrawerSide
import com.signai.webview.ui.designsystem.components.SignAiEdgeDrawer
import com.signai.webview.ui.designsystem.components.Surface
import com.signai.webview.ui.screens.bottombar.SignAiBottomBar
import com.signai.webview.ui.screens.conversation.ConversationPanel
import com.signai.webview.ui.screens.settings.SettingsSidebar
import com.signai.webview.ui.screens.stage.AvatarStage
import com.signai.webview.ui.state.MainScreenState
import com.signai.webview.ui.state.PlaybackState
import com.signai.webview.ui.theme.SignAiTheme
import com.signai.webview.ui.webview.AvatarWebView
import kotlinx.coroutines.delay

private const val SplashTimeoutMs = 8_000L
private const val ContentFadeMs = 500
private const val SplashFadeMs = 400

@Composable
fun MainScreen(state: MainScreenState) {
    var showConversation by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    // Blur the 3D canvas only while a drawer is open (perf, spec `EDG-14`).
    LaunchedEffect(showConversation, showSettings) {
        state.setBlur(showConversation || showSettings)
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

    val avatarStatus = when (state.playbackState) {
        PlaybackState.Done -> "Reproducción completada"
        PlaybackState.Signing -> "Reproduciendo señas..."
        else -> "Esperando traducción"
    }

    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha },
            color = SignAiTheme.colors.background,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val panelWidth = minOf(maxWidth * 0.88f, 350.dp)
            SignAiEdgeDrawer(
                side = DrawerSide.End,
                isOpen = showSettings,
                onOpenChange = { open ->
                    showSettings = open
                    if (open) showConversation = false
                },
                drawerWidth = panelWidth,
                edgeStripBottomExclusion = bottomBarHeight,
                previewLabel = "Ajustes",
                drawerContent = {
                    SettingsSidebar(
                        settings = state.settings,
                        onToggle = state::toggle,
                        onCharacter = state::setCharacter,
                        onSpeed = state::setSpeed,
                        onInterfaceSize = state::setInterfaceSize,
                        sign = state.sign,
                        signs = state.signs,
                        onPickSign = state::pickSign,
                        onClose = { showSettings = false },
                        onOpenAbout = { showSettings = false },
                    )
                },
            ) {
                SignAiEdgeDrawer(
                    side = DrawerSide.Start,
                    isOpen = showConversation,
                    onOpenChange = { open ->
                        showConversation = open
                        if (open) showSettings = false
                    },
                    drawerWidth = panelWidth,
                    edgeStripBottomExclusion = bottomBarHeight,
                    previewLabel = "Conversación",
                    drawerContent = {
                        ConversationPanel(
                            text = state.lastText,
                            gloss = Gloss.glossFor(state.lastText),
                            avatarStatus = avatarStatus,
                            onRepeat = state::replay,
                            onClose = { showConversation = false },
                        )
                    },
                ) {
                    Column(Modifier.fillMaxSize()) {
                        AvatarStage(
                            status = state.playbackState.label,
                            doneVisible = state.playbackState == PlaybackState.Done,
                            onRepeat = state::replay,
                            modifier = Modifier.weight(1f),
                        ) {
                            AvatarWebView(
                                modifier = Modifier.fillMaxSize(),
                                onReady = state::attachWebView,
                                onProgress = { progress = it },
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
                                playbackState = state.playbackState,
                                onSend = state::play,
                                onMic = state::toggleMic,
                                onPickHistory = state::play,
                            )
                        }
                    }
                }
            }
            }
        }

        AnimatedVisibility(
            visible = loading,
            enter = EnterTransition.None,
            exit = fadeOut(animationSpec = tween(durationMillis = SplashFadeMs)),
        ) {
            SignAiSplash(progress = progress)
        }
    }
}
