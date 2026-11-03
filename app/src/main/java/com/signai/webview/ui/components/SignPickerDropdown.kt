package com.signai.webview.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.designsystem.components.SignAiDropdown
import com.signai.webview.ui.designsystem.components.SignAiDropdownMenuItem
import com.signai.webview.ui.designsystem.components.SignAiIcon
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.components.Surface
import com.signai.webview.ui.designsystem.foundation.SignAiShapes
import com.signai.webview.ui.designsystem.foundation.SignAiSizes
import com.signai.webview.ui.icons.SignAiIcons
import com.signai.webview.ui.theme.SignAiTheme

/**
 * Picks one of the signs baked into the APK.
 *
 * The ids are dataset filenames (`0007_0000_0000` = sign 7, repetition 0,
 * angle 0), which say nothing to a person, so only the sign number is shown.
 * The number is not a gloss either — what a sign *means* comes from the
 * backend's catalog, which is still empty — so the label does not pretend to
 * name it.
 *
 * El trigger pesa lo mismo que el de Personaje ([SignAiSizes.characterTriggerWidth]
 * y [SignAiSizes.characterTriggerHeight]): antes era un botón `PrimaryOutlined`
 * de ancho completo y azul, o sea el elemento más pesado del panel para un ajuste
 * de desarrollo. Desde 2026-11-03 va como una fila más, con el mismo borde,
 * chevron y tipografía que el selector de Personaje.
 */
private fun label(id: String): String =
    id.substringBefore('_').trimStart('0').ifEmpty { "0" }.let { "Seña $it" }

@Composable
fun SignPickerDropdown(
    current: String,
    options: List<String>,
    onPick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    SignAiDropdown(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        maxHeight = 260.dp,
        trigger = {
            Surface(
                onClick = { expanded = !expanded },
                shape = SignAiShapes.cornerTile,
                color = SignAiTheme.colors.background,
                border = BorderStroke(
                    1.dp,
                    if (expanded) SignAiTheme.colors.strong else SignAiTheme.colors.outline,
                ),
                modifier = Modifier
                    .width(SignAiSizes.signTriggerWidth)
                    .height(SignAiSizes.characterTriggerHeight),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SignAiSizes.characterTriggerHeight)
                        .padding(start = 14.dp, end = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SignAiText(
                        text = label(current),
                        style = SignAiTheme.typography.body2,
                        color = SignAiTheme.colors.text,
                    )
                    SignAiIcon(
                        imageVector = SignAiIcons.CaretDown,
                        contentDescription = null,
                        size = 16.dp,
                        tint = SignAiTheme.colors.muted,
                        modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    )
                }
            }
        },
    ) {
        options.forEach { option ->
            SignAiDropdownMenuItem(
                text = if (option == current) "✓ ${label(option)}" else label(option),
                onClick = {
                    expanded = false
                    onPick(option)
                },
            )
        }
    }
}
