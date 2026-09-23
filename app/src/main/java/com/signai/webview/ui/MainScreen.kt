package com.signai.webview.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.components.HistoryBottomSheet
import com.signai.webview.ui.components.SignAiSplash
import com.signai.webview.ui.designsystem.components.DrawerSide
import com.signai.webview.ui.designsystem.components.SignAiEdgeDrawer
import com.signai.webview.ui.designsystem.components.Surface
import com.signai.webview.ui.designsystem.foundation.LocalReduceMotion
import com.signai.webview.ui.designsystem.foundation.SignAiMotion
import com.signai.webview.ui.screens.about.AboutScreen
import com.signai.webview.ui.screens.bottombar.SignAiBottomBar
import com.signai.webview.ui.screens.conversation.ConversationPanel
import com.signai.webview.ui.screens.settings.SettingsSidebar
import com.signai.webview.ui.screens.stage.AvatarStage
import com.signai.webview.ui.state.MainScreenState
import com.signai.webview.ui.state.PlaybackState
import com.signai.webview.ui.theme.SignAiTheme
import com.signai.webview.ui.webview.AvatarWebView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SplashTimeoutMs = 8_000L
private const val ContentFadeMs = 500
private const val SplashFadeMs = 400

/** Wait for the drawer close animation before starting a delayed replay. */
private const val DrawerCloseDelayMs = 500L

@Composable
fun MainScreen(state: MainScreenState) {
    var showConversation by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    // Open fraction of the left drawer; dims the right handle so it falls behind
    // the left drawer's backdrop too (the nested layout hides it otherwise).
    var conversationFraction by remember { mutableFloatStateOf(0f) }
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
        animationSpec = SignAiMotion.motionTween(
            durationMillis = ContentFadeMs,
            easing = LinearEasing,
        ),
        label = "contentAlpha",
    )
    val splashExit: ExitTransition =
        if (LocalReduceMotion.current) {
            ExitTransition.None
        } else {
            fadeOut(animationSpec = tween(durationMillis = SplashFadeMs))
        }

    Box(Modifier.fillMaxSize()) {
        if (showAbout) {
            // About is exclusive: Main and drawers are not shown (spec `NAV-8`).
            AboutScreen(onBack = { showAbout = false })
        } else {
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
                        handleDimFraction = conversationFraction,
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
                                onOpenAbout = {
                                    showSettings = false
                                    showAbout = true
                                },
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
                            onFractionChange = { conversationFraction = it },
                            drawerContent = {
                                ConversationPanel(
                                    turns = state.conversation,
                                    onRepeat = { text ->
                                        showConversation = false
                                        scope.launch {
                                            delay(DrawerCloseDelayMs)
                                            state.replayTurn(text)
                                        }
                                    },
                                    onClose = { showConversation = false },
                                )
                            },
                        ) {
                            // The WebView fills the whole content and the control
                            // bar overlays it, so the bar's rounded top corners
                            // reveal the scene (not the black root background).
                            Box(Modifier.fillMaxSize()) {
                                AvatarStage(
                                    status = state.playbackState.label,
                                    doneVisible = state.playbackState == PlaybackState.Done,
                                    onRepeat = state::replay,
                                    bottomInset = bottomBarHeight,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    AvatarWebView(
                                        modifier = Modifier.fillMaxSize(),
                                        onReady = state::attachWebView,
                                        onProgress = { progress = it },
                                        onPlaybackDone = state::onPlaybackDone,
                                        onLoaded = {
                                            loading = false
                                            state.syncWebSettings()
                                        },
                                    )
                                }
                                Box(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .onSizeChanged {
                                            bottomBarHeight = with(density) { it.height.toDp() }
                                        },
                                ) {
                                    SignAiBottomBar(
                                        query = state.inputText,
                                        onQueryChange = state::setInput,
                                        playbackState = state.playbackState,
                                        onSend = state::play,
                                        onMic = state::toggleMic,
                                        onOpenHistory = { showHistory = true },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        HistoryBottomSheet(
            visible = showHistory && !showAbout,
            history = state.historyList,
            onPick = { phrase ->
                showHistory = false
                state.setInput(phrase)
                state.play(phrase)
            },
            onDismiss = { showHistory = false },
        )

        AnimatedVisibility(
            visible = loading,
            enter = EnterTransition.None,
            exit = splashExit,
        ) {
            SignAiSplash(progress = progress)
        }
    }
}
