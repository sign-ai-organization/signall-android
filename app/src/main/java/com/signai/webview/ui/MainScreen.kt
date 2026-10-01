package com.signai.webview.ui

import androidx.activity.compose.BackHandler
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

private const val SplashTimeoutMs = 8_000L
private const val ContentFadeMs = 500
private const val SplashFadeMs = 400

@Composable
fun MainScreen(state: MainScreenState) {
    var showHistory by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    // Open fraction of each drawer: the left one drives the fallback dim of the
    // right handle, the right one drives the dim of the left handle (`EDG-14`).
    var conversationFraction by remember { mutableFloatStateOf(0f) }
    var settingsFraction by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    val showConversation = state.openDrawer == DrawerSide.Start
    val showSettings = state.openDrawer == DrawerSide.End

    // Blur the 3D canvas only while a drawer is open (perf, spec `EDG-14`). The
    // control panel is disabled separately, by `state.replayPending` (`CNV-9`).
    LaunchedEffect(showConversation, showSettings) {
        state.setBlur(showConversation || showSettings)
    }

    // Back closes the history sheet, then the open drawer, instead of leaving.
    BackHandler(enabled = showConversation || showSettings || showHistory) {
        when {
            showHistory -> showHistory = false
            showConversation || showSettings -> state.closeDrawer()
        }
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

                    // Compose order is the fix (spec `EDG-22`). The WebView is
                    // an embedded Android View: it takes every touch inside its
                    // bounds and no Compose sibling can paint over it, so the
                    // stage and the control bar must be the FIRST children and
                    // the drawers must be composed after them, as siblings (not
                    // nested inside each other). Previously each drawer wrapped
                    // the next one (End → Start → stage), so the scene and the
                    // bar lived inside the drawers' content and stayed on top of
                    // the panels, while the backdrops never saw a tap.
                    Box(Modifier.fillMaxSize()) {
                        AvatarStage(
                            status = state.statusLine,
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
                                interactive = !(showConversation || showSettings),
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
                                enabled = !state.replayPending,
                            )
                        }

                        SignAiEdgeDrawer(
                            side = DrawerSide.Start,
                            isOpen = showConversation,
                            onOpenChange = { open ->
                                if (open) state.openDrawer(DrawerSide.Start) else state.closeDrawer()
                            },
                            drawerWidth = panelWidth,
                            edgeStripBottomExclusion = bottomBarHeight,
                            previewLabel = "Conversación",
                            otherDrawerOpen = showSettings,
                            currentlyOpenFraction = conversationFraction,
                            otherOpenFraction = settingsFraction,
                            onFractionChange = { conversationFraction = it },
                            claimGesture = { state.claimDrawerGesture(DrawerSide.Start) },
                            releaseGesture = { state.releaseDrawerGesture(DrawerSide.Start) },
                            drawerContent = {
                                ConversationPanel(
                                    turns = state.conversation,
                                    onRepeat = state::repeatTurnFromDrawer,
                                    onClose = state::closeDrawer,
                                )
                            },
                        ) {
                            // Empty: the drawer still needs the slot, but the
                            // scene is a sibling below it now (spec `EDG-22`).
                            Box(Modifier.fillMaxSize())
                        }

                        SignAiEdgeDrawer(
                            side = DrawerSide.End,
                            isOpen = showSettings,
                            onOpenChange = { open ->
                                if (open) state.openDrawer(DrawerSide.End) else state.closeDrawer()
                            },
                            drawerWidth = panelWidth,
                            edgeStripBottomExclusion = bottomBarHeight,
                            previewLabel = "Ajustes",
                            otherDrawerOpen = showConversation,
                            currentlyOpenFraction = settingsFraction,
                            otherOpenFraction = conversationFraction,
                            onFractionChange = { settingsFraction = it },
                            claimGesture = { state.claimDrawerGesture(DrawerSide.End) },
                            releaseGesture = { state.releaseDrawerGesture(DrawerSide.End) },
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
                                    onClose = state::closeDrawer,
                                    onOpenAbout = {
                                        state.closeDrawer()
                                        showAbout = true
                                    },
                                )
                            },
                        ) {
                            Box(Modifier.fillMaxSize())
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
