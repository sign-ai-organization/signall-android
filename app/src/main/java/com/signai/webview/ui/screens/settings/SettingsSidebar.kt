package com.signai.webview.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signai.webview.ui.SignAiBranding
import com.signai.webview.ui.components.CharacterPicker
import com.signai.webview.ui.components.SettingRow
import com.signai.webview.ui.components.SignAiSmallIconButton
import com.signai.webview.ui.components.SignPickerDropdown
import com.signai.webview.ui.designsystem.components.SignAiIcon
import com.signai.webview.ui.designsystem.components.SignAiSlider
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.components.Switch
import com.signai.webview.ui.designsystem.foundation.SignAiShapes
import com.signai.webview.ui.designsystem.foundation.SignAiSizes
import com.signai.webview.ui.icons.SignAiIcons
import com.signai.webview.domain.Character
import com.signai.webview.domain.Setting
import com.signai.webview.domain.Settings
import com.signai.webview.ui.theme.SignAiTheme

/**
 * Right drawer — Settings (spec `SET-*`), with sections AVATAR / ACCESIBILIDAD /
 * APARIENCIA / AVANZADO. The debug toggles live in AVANZADO, separated from the
 * user-facing settings (`SET-8`).
 */
@Composable
fun SettingsSidebar(
    settings: Settings,
    onToggle: (Setting, Boolean) -> Unit,
    onCharacter: (Character) -> Unit,
    onSpeed: (Int) -> Unit,
    onInterfaceSize: (Int) -> Unit,
    sign: String,
    signs: List<String>,
    onPickSign: (String) -> Unit,
    onClose: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(SignAiSizes.settingsLogoSize)
                    .background(SignAiTheme.colors.strong, SignAiShapes.cornerTile),
                contentAlignment = Alignment.Center,
            ) {
                SignAiText(
                    text = SignAiBranding.LogoLetter,
                    style = SignAiTheme.typography.h3.copy(
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = SignAiTheme.colors.onStrong,
                )
            }
            SignAiSmallIconButton(
                icon = SignAiIcons.X,
                contentDescription = "Cerrar",
                onClick = onClose,
            )
        }

        // Prototype `.panel-top { margin-bottom: 38px }`.
        Spacer(Modifier.height(38.dp))

        Section("AVATAR") {
            SettingRow(label = "Personaje", icon = SignAiIcons.Sparkle) {
                CharacterPicker(current = settings.character, onPick = onCharacter)
            }
            SpeedRow(speed = settings.speed, onSpeed = onSpeed)
        }

        Section("ACCESIBILIDAD") {
            SettingRow(label = "Tamaño de interfaz", icon = SignAiIcons.TextAa) {
                Box(modifier = Modifier.width(66.dp)) {
                    SignAiSlider(
                        value = settings.interfaceSize / 100f,
                        onValueChange = { onInterfaceSize((it * 100).toInt()) },
                    )
                }
            }
            SettingRow(label = "Texto en negrita", icon = SignAiIcons.TextAa) {
                Switch(checked = settings.bold, onCheckedChange = { onToggle(Setting.Bold, it) })
            }
            SettingRow(label = "Contraste aumentado", icon = SignAiIcons.CircleHalf) {
                Switch(checked = settings.contrast, onCheckedChange = { onToggle(Setting.Contrast, it) })
            }
            SettingRow(label = "Texto más grande", icon = SignAiIcons.Plus) {
                Switch(checked = settings.large, onCheckedChange = { onToggle(Setting.Large, it) })
            }
            SettingRow(label = "Reducir animaciones", icon = SignAiIcons.Minus) {
                Switch(checked = settings.reduceMotion, onCheckedChange = { onToggle(Setting.ReduceMotion, it) })
            }
        }

        Section("APARIENCIA") {
            SettingRow(
                label = if (settings.darkTheme) "Modo oscuro" else "Modo claro",
                icon = if (settings.darkTheme) SignAiIcons.Moon else SignAiIcons.Sun,
            ) {
                Switch(checked = settings.darkTheme, onCheckedChange = { onToggle(Setting.DarkTheme, it) })
            }
        }

        AboutLink(onClick = onOpenAbout)

        Section("AVANZADO") {
            SettingRow(label = "Modo debug", icon = SignAiIcons.Gear) {
                Switch(checked = settings.debug, onCheckedChange = { onToggle(Setting.Debug, it) })
            }
            SettingRow(label = "Cámara libre (orbitar + zoom)", icon = SignAiIcons.Gear) {
                Switch(checked = settings.freeCam, onCheckedChange = { onToggle(Setting.FreeCam, it) })
            }
            SettingRow(label = "Suelo (grilla)", icon = SignAiIcons.Gear) {
                Switch(checked = settings.grid, onCheckedChange = { onToggle(Setting.Grid, it) })
            }
            SettingRow(label = "Fondo bosque", icon = SignAiIcons.Gear) {
                Switch(checked = settings.bg, onCheckedChange = { onToggle(Setting.Background, it) })
            }
            SettingRow(
                label = "Seña de prueba si falta en el catálogo",
                icon = SignAiIcons.Gear,
            ) {
                Switch(
                    checked = settings.demoSignFallback,
                    onCheckedChange = { onToggle(Setting.DemoSignFallback, it) },
                )
            }
            Spacer(Modifier.height(8.dp))
            SignPickerDropdown(current = sign, options = signs, onPick = onPickSign)
        }
    }
}

@Composable
private fun SpeedRow(speed: Int, onSpeed: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            SignAiIcon(
                imageVector = SignAiIcons.SpeakerHigh,
                contentDescription = null,
                size = 18.dp,
                tint = SignAiTheme.colors.muted,
            )
            SignAiText(
                text = "Velocidad de señas",
                style = SignAiTheme.typography.body2,
                color = SignAiTheme.colors.text,
            )
        }
        SignAiSlider(
            value = (speed - 10) / 90f,
            onValueChange = { onSpeed(10 + (it * 90).toInt()) },
        )
    }
}

@Composable
private fun Section(
    label: String,
    content: @Composable () -> Unit,
) {
    // Prototype `.setting-section { border-top: 1px solid var(--line);
    // padding: 21px 0 8px }` and `.label { margin: 0 0 9px }`.
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SignAiTheme.colors.outline),
        )
        Column(modifier = Modifier.padding(top = 21.dp, bottom = 8.dp)) {
            SignAiText(
                text = label,
                style = SignAiTheme.typography.label,
                color = SignAiTheme.colors.muted,
                modifier = Modifier.padding(bottom = 9.dp),
            )
            content()
        }
    }
}

@Composable
private fun AboutLink(onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SignAiTheme.colors.outline),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SignAiIcon(
                imageVector = SignAiIcons.Info,
                contentDescription = null,
                size = 18.dp,
                tint = SignAiTheme.colors.muted,
            )
            SignAiText(
                text = "Acerca de",
                style = SignAiTheme.typography.body2,
                color = SignAiTheme.colors.text,
                modifier = Modifier.weight(1f),
            )
            SignAiIcon(
                imageVector = SignAiIcons.CaretRight,
                contentDescription = null,
                size = 16.dp,
                tint = SignAiTheme.colors.muted,
            )
        }
    }
}
