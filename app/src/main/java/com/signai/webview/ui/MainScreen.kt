package com.signai.webview.ui

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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.designsystem.components.SignAiEdgeDrawer
import com.signai.webview.ui.designsystem.components.Surface
import com.signai.webview.ui.screens.bottombar.SignAiBottomBar
import com.signai.webview.ui.screens.settings.SettingsSidebar
import com.signai.webview.ui.state.MainScreenState
import com.signai.webview.ui.theme.SignAiTheme
import com.signai.webview.ui.webview.AvatarWebView

@Composable
fun MainScreen(state: MainScreenState) {
    var showSettings by remember { mutableStateOf(false) }
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    LaunchedEffect(showSettings) {
        state.setBlur(showSettings)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SignAiTheme.colors.background,
    ) {
        SignAiEdgeDrawer(
            isOpen = showSettings,
            onOpenChange = { showSettings = it },
            drawerWidth = 300.dp,
            edgeStripBottomExclusion = bottomBarHeight,
            content = {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        AvatarWebView(
                            modifier = Modifier.fillMaxSize(),
                            onReady = state::attachWebView,
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
}
