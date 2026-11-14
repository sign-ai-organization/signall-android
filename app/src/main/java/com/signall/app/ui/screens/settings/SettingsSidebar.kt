package com.signall.app.ui.screens.settings

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
import com.signall.app.BuildConfig
import com.signall.app.ui.SignAllBranding
import com.signall.app.ui.components.CharacterPicker
import com.signall.app.ui.components.SettingRow
import com.signall.app.ui.components.SignAllBrandTile
import com.signall.app.ui.components.SignAllSmallIconButton
import com.signall.app.ui.components.SignAllStepper
import com.signall.app.ui.components.StepperConfig
import com.signall.app.ui.components.SignPickerDropdown
import com.signall.app.ui.designsystem.components.SignAllIcon
import com.signall.app.ui.designsystem.components.SignAllText
import com.signall.app.ui.designsystem.components.Switch
import com.signall.app.ui.designsystem.foundation.SignAllSizes
import com.signall.app.ui.icons.SignAllIcons
import com.signall.app.domain.Character
import com.signall.app.domain.Setting
import com.signall.app.domain.Settings
import com.signall.app.ui.theme.SignAllTheme

/**
 * Right drawer — Settings (spec `SET-*`): AVATAR / ACCESIBILIDAD / APARIENCIA
 * and, **only in development builds**, AVANZADO.
 *
 * AVANZADO ya no es una sección de producción ni depende de una preferencia:
 * existe si `BuildConfig.DEV_TOOLS` está encendido (`-Psignall.devTools=true` o
 * `SIGNALL_DEV_TOOLS=1`, ver `app/build.gradle.kts`). Con el flag apagado no se
 * compone el `Section`, así que no hay ninguna forma de llegar a esos ajustes
 * desde el teléfono, ni con la vieja preferencia `debug` guardada.
 */

/** Lo que el panel de ajustes le pide al host. */
class SettingsActions(
    val onToggle: (Setting, Boolean) -> Unit,
    val onCharacter: (Character) -> Unit,
    val onSpeed: (Int) -> Unit,
    val onInterfaceSize: (Int) -> Unit,
    val onPickSign: (String) -> Unit,
    val onClose: () -> Unit,
    val onOpenAbout: () -> Unit,
)

@Composable
fun SettingsSidebar(
    settings: Settings,
    canSwitchAvatar: Boolean,
    sign: String,
    signs: List<String>,
    actions: SettingsActions,
) {
    val onToggle = actions.onToggle
    val onCharacter = actions.onCharacter
    val onSpeed = actions.onSpeed
    val onInterfaceSize = actions.onInterfaceSize
    val onPickSign = actions.onPickSign
    val onClose = actions.onClose
    val onOpenAbout = actions.onOpenAbout
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
            SignAllBrandTile(
                size = SignAllSizes.settingsLogoSize,
                contentDescription = SignAllBranding.Name,
            )
            SignAllSmallIconButton(
                icon = SignAllIcons.X,
                contentDescription = "Cerrar",
                onClick = onClose,
            )
        }

        // Prototype `.panel-top { margin-bottom: 38px }`.
        Spacer(Modifier.height(38.dp))

        Section("AVATAR") {
            SettingRow(
                label = "Personaje",
                icon = SignAllIcons.Person,
                stackBelow = SignAllSizes.settingRowStackBelow,
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
                icon = SignAllIcons.TextAa,
                stackBelow = SignAllSizes.settingRowStackBelow,
            ) {
                SignAllStepper(
                    value = settings.interfaceSize,
                    onValue = onInterfaceSize,
                    config = StepperConfig(label = "el tamaño de la interfaz"),
                )
            }
            SettingRow(label = "Texto en negrita", icon = SignAllIcons.TextB) {
                Switch(checked = settings.bold, onCheckedChange = { onToggle(Setting.Bold, it) })
            }
            SettingRow(label = "Contraste aumentado", icon = SignAllIcons.CircleHalf) {
                Switch(checked = settings.contrast, onCheckedChange = { onToggle(Setting.Contrast, it) })
            }
            SettingRow(label = "Texto más grande", icon = SignAllIcons.TextHOne) {
                Switch(checked = settings.large, onCheckedChange = { onToggle(Setting.Large, it) })
            }
            SettingRow(label = "Reducir animaciones", icon = SignAllIcons.FilmStrip) {
                Switch(checked = settings.reduceMotion, onCheckedChange = { onToggle(Setting.ReduceMotion, it) })
            }
        }

        Section("APARIENCIA") {
            SettingRow(
                label = if (settings.darkTheme) "Modo oscuro" else "Modo claro",
                icon = if (settings.darkTheme) SignAllIcons.Moon else SignAllIcons.Sun,
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
                SettingRow(label = "Cámara libre", icon = SignAllIcons.Cursor) {
                    Switch(checked = settings.freeCam, onCheckedChange = { onToggle(Setting.FreeCam, it) })
                }
                SettingRow(label = "Suelo (grilla)", icon = SignAllIcons.GridFour) {
                    Switch(checked = settings.grid, onCheckedChange = { onToggle(Setting.Grid, it) })
                }
                SettingRow(label = "Fondo bosque", icon = SignAllIcons.Tree) {
                    Switch(checked = settings.bg, onCheckedChange = { onToggle(Setting.Background, it) })
                }
                SettingRow(label = "Seña de prueba", icon = SignAllIcons.PlayCircle) {
                    Switch(
                        checked = settings.demoSignFallback,
                        onCheckedChange = { onToggle(Setting.DemoSignFallback, it) },
                    )
                }
                SettingRow(
                    label = "Seña de ejemplo",
                    icon = SignAllIcons.Sparkle,
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
 * Era una etiqueta con un `SignAllSlider` que mapeaba 10..100 a 0..1 y no mostraba
 * el número en ningún lado: no había forma de saber en qué valor estaba. Ahora es
 * una fila más, con el mismo `SignAllStepper` de «Tamaño de interfaz»: dos botones
 * y el valor visible.
 *
 * El paso es 10 (decisión del owner, 2026-11-03): de 10 a 100 en nueve toques.
 * La escala y el rango no cambian, y quien ya tiene un valor guardado lo conserva.
 */
@Composable
private fun SpeedRow(speed: Int, onSpeed: (Int) -> Unit) {
    SettingRow(
        label = "Velocidad de señas",
        icon = SignAllIcons.Speedometer,
        stackBelow = SignAllSizes.settingRowStackBelow,
    ) {
        SignAllStepper(
            value = speed,
            onValue = onSpeed,
            config = StepperConfig(
                step = 10,
                minValue = 10,
                maxValue = 100,
                label = "la velocidad de las señas",
            ),
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
                .background(SignAllTheme.colors.outline),
        )
        Column(modifier = Modifier.padding(top = 21.dp, bottom = 8.dp)) {
            SignAllText(
                text = label,
                style = SignAllTheme.typography.label,
                color = SignAllTheme.colors.muted,
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
                .background(SignAllTheme.colors.outline),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SignAllIcon(
                imageVector = SignAllIcons.Info,
                contentDescription = null,
                size = 18.dp,
                tint = SignAllTheme.colors.muted,
            )
            SignAllText(
                text = "Acerca de",
                style = SignAllTheme.typography.body2,
                color = SignAllTheme.colors.text,
                modifier = Modifier.weight(1f),
            )
            SignAllIcon(
                imageVector = SignAllIcons.CaretRight,
                contentDescription = null,
                size = 16.dp,
                tint = SignAllTheme.colors.muted,
            )
        }
    }
}
