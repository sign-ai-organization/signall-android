package com.signall.app.ui.components

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
import com.signall.app.ui.designsystem.components.SignAllDropdown
import com.signall.app.ui.designsystem.components.SignAllDropdownMenuItem
import com.signall.app.ui.designsystem.components.SignAllIcon
import com.signall.app.ui.designsystem.components.SignAllText
import com.signall.app.ui.designsystem.components.Surface
import com.signall.app.ui.designsystem.foundation.SignAllShapes
import com.signall.app.ui.designsystem.foundation.SignAllSizes
import com.signall.app.ui.icons.SignAllIcons
import com.signall.app.ui.theme.SignAllTheme

/**
 * Picks one of the signs baked into the APK.
 *
 * The ids are dataset filenames (`0007_0000_0000` = sign 7, repetition 0,
 * angle 0), which say nothing to a person, so only the sign number is shown.
 * The number is not a gloss either — what a sign *means* comes from the
 * backend's catalog, which is still empty — so the label does not pretend to
 * name it.
 *
 * El trigger pesa lo mismo que el de Personaje ([SignAllSizes.characterTriggerWidth]
 * y [SignAllSizes.characterTriggerHeight]): antes era un botón `PrimaryOutlined`
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
    SignAllDropdown(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        maxHeight = 260.dp,
        trigger = {
            Surface(
                onClick = { expanded = !expanded },
                shape = SignAllShapes.cornerTile,
                color = SignAllTheme.colors.background,
                border = BorderStroke(
                    1.dp,
                    if (expanded) SignAllTheme.colors.strong else SignAllTheme.colors.outline,
                ),
                modifier = Modifier
                    .width(SignAllSizes.signTriggerWidth)
                    .height(SignAllSizes.characterTriggerHeight),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SignAllSizes.characterTriggerHeight)
                        .padding(start = 14.dp, end = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SignAllText(
                        text = label(current),
                        style = SignAllTheme.typography.body2,
                        color = SignAllTheme.colors.text,
                    )
                    SignAllIcon(
                        imageVector = SignAllIcons.CaretDown,
                        contentDescription = null,
                        size = 16.dp,
                        tint = SignAllTheme.colors.muted,
                        modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    )
                }
            }
        },
    ) {
        options.forEach { option ->
            SignAllDropdownMenuItem(
                text = if (option == current) "✓ ${label(option)}" else label(option),
                onClick = {
                    expanded = false
                    onPick(option)
                },
            )
        }
    }
}
