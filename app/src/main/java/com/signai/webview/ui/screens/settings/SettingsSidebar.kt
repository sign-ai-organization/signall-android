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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.signai.webview.BuildConfig
import com.signai.webview.ui.SignAiBranding
import com.signai.webview.ui.components.CharacterPicker
import com.signai.webview.ui.components.SettingRow
import com.signai.webview.ui.components.SignAiBrandTile
import com.signai.webview.ui.components.SignAiSmallIconButton
import com.signai.webview.ui.components.SignAiStepper
import com.signai.webview.ui.components.SignPickerDropdown
import com.signai.webview.ui.designsystem.components.SignAiIcon
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.components.Switch
import com.signai.webview.ui.designsystem.foundation.SignAiSizes
import com.signai.webview.ui.icons.SignAiIcons
import com.signai.webview.domain.Character
import com.signai.webview.domain.Setting
import com.signai.webview.domain.Settings
import com.signai.webview.ui.theme.SignAiTheme

/**
 * Right drawer — Settings (spec `SET-*`): AVATAR / ACCESIBILIDAD / APARIENCIA
 * and, **only in development builds**, AVANZADO.
 *
 * AVANZADO ya no es una sección de producción ni depende de una preferencia:
 * existe si `BuildConfig.DEV_TOOLS` está encendido (`-Psignai.devTools=true` o
 * `SIGNAI_DEV_TOOLS=1`, ver `app/build.gradle.kts`). Con el flag apagado no se
 * compone el `Section`, así que no hay ninguna forma de llegar a esos ajustes
 * desde el teléfono, ni con la vieja preferencia `debug` guardada.
 */
@Composable
fun SettingsSidebar(
    settings: Settings,
    onToggle: (Setting, Boolean) -> Unit,
    onCharacter: (Character) -> Unit,
    canSwitchAvatar: Boolean,
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
            SignAiBrandTile(
                size = SignAiSizes.settingsLogoSize,
                contentDescription = SignAiBranding.Name,
            )
            SignAiSmallIconButton(
                icon = SignAiIcons.X,
                contentDescription = "Cerrar",
                onClick = onClose,
            )
        }

        // Prototype `.panel-top { margin-bottom: 38px }`.
        Spacer(Modifier.height(38.dp))

        Section("AVATAR") {
            SettingRow(
                label = "Personaje",
                icon = SignAiIcons.Person,
                stackBelow = SignAiSizes.settingRowStackBelow,
            ) {
                CharacterPicker(
                    current = settings.character,
                    onPick = onCharacter,
                    enabled = canSwitchAvatar,
                )
            }
            SpeedRow(speed = settings.speed, onSpeed = onSpeed)
        }

        Section("ACCESIBILIDAD") {
            SettingRow(
                label = "Tamaño de interfaz",
                icon = SignAiIcons.TextAa,
                stackBelow = SignAiSizes.settingRowStackBelow,
            ) {
                SignAiStepper(
                    value = settings.interfaceSize,
                    onValue = onInterfaceSize,
                    label = "el tamaño de la interfaz",
                )
            }
            SettingRow(label = "Texto en negrita", icon = SignAiIcons.TextB) {
                Switch(checked = settings.bold, onCheckedChange = { onToggle(Setting.Bold, it) })
            }
            SettingRow(label = "Contraste aumentado", icon = SignAiIcons.CircleHalf) {
                Switch(checked = settings.contrast, onCheckedChange = { onToggle(Setting.Contrast, it) })
            }
            SettingRow(label = "Texto más grande", icon = SignAiIcons.TextHOne) {
                Switch(checked = settings.large, onCheckedChange = { onToggle(Setting.Large, it) })
            }
            SettingRow(label = "Reducir animaciones", icon = SignAiIcons.FilmStrip) {
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

        // Solo existe si la compilación lo pide: no es una preferencia del
        // teléfono. Con el flag apagado, ni la sección ni la consola de la
        // escena se encienden desde acá.
        if (BuildConfig.DEV_TOOLS) {
            Section("AVANZADO") {
                SettingRow(label = "Cámara libre", icon = SignAiIcons.Cursor) {
                    Switch(checked = settings.freeCam, onCheckedChange = { onToggle(Setting.FreeCam, it) })
                }
                SettingRow(label = "Suelo (grilla)", icon = SignAiIcons.GridFour) {
                    Switch(checked = settings.grid, onCheckedChange = { onToggle(Setting.Grid, it) })
                }
                SettingRow(label = "Fondo bosque", icon = SignAiIcons.Tree) {
                    Switch(checked = settings.bg, onCheckedChange = { onToggle(Setting.Background, it) })
                }
                SettingRow(label = "Seña de prueba", icon = SignAiIcons.PlayCircle) {
                    Switch(
                        checked = settings.demoSignFallback,
                        onCheckedChange = { onToggle(Setting.DemoSignFallback, it) },
                    )
                }
                SettingRow(
                    label = "Seña de ejemplo",
                    icon = SignAiIcons.Sparkle,
                    // 300 y no 270: con el selector de 150 dp, entre 271 y 299 dp
                    // de fila la etiqueta se partiría en dos líneas. En el Redmi
                    // (fila de 301 dp) queda en una sola; en pantallas de 360 dp o
                    // menos se apila, que es preferible a "Seña de / ejemplo".
                    stackBelow = 300.dp,
                ) {
                    SignPickerDropdown(current = sign, options = signs, onPick = onPickSign)
                }
            }
        }
    }
}

/**
 * Velocidad de las señas (`SET-4`, `RF-AVT-08`).
 *
 * Era una etiqueta con un `SignAiSlider` que mapeaba 10..100 a 0..1 y no mostraba
 * el número en ningún lado: no había forma de saber en qué valor estaba. Ahora es
 * una fila más, con el mismo `SignAiStepper` de «Tamaño de interfaz»: dos botones
 * y el valor visible.
 *
 * El paso es 10 (decisión del owner, 2026-11-03): de 10 a 100 en nueve toques.
 * La escala y el rango no cambian, y quien ya tiene un valor guardado lo conserva.
 */
@Composable
private fun SpeedRow(speed: Int, onSpeed: (Int) -> Unit) {
    SettingRow(
        label = "Velocidad de señas",
        icon = SignAiIcons.Speedometer,
        stackBelow = SignAiSizes.settingRowStackBelow,
    ) {
        SignAiStepper(
            value = speed,
            onValue = onSpeed,
            step = 10,
            minValue = 10,
            maxValue = 100,
            label = "la velocidad de las señas",
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
