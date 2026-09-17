package com.signai.webview.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.components.SettingRow
import com.signai.webview.ui.components.SignPickerDropdown
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.state.Setting
import com.signai.webview.ui.state.Settings
import com.signai.webview.ui.theme.SignAiTheme

@Composable
fun SettingsSidebar(
    settings: Settings,
    onToggle: (Setting, Boolean) -> Unit,
    sign: String,
    signs: List<String>,
    onPickSign: (String) -> Unit,
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

        SettingRow("Modo debug", settings.debug) { onToggle(Setting.Debug, it) }
        SettingRow("Cámara libre (orbitar + zoom sin límite)", settings.freeCam) {
            onToggle(Setting.FreeCam, it)
        }
        SettingRow("Suelo (grilla)", settings.grid) { onToggle(Setting.Grid, it) }
        SettingRow("Fondo bosque", settings.bg) { onToggle(Setting.Background, it) }

        Spacer(Modifier.height(8.dp))
        SignPickerDropdown(
            current = sign,
            options = signs,
            onPick = onPickSign,
        )
    }
}
