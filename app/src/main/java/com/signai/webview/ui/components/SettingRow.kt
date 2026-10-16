package com.signai.webview.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.designsystem.components.SignAiIcon
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.foundation.SignAiSizes
import com.signai.webview.ui.theme.SignAiTheme

/**
 * Reusable settings row (spec `SET-9`): optional icon + label on the left, any
 * control on the right. Min height 50.
 *
 * La etiqueta lleva `weight(1f)` y por dentro el texto también, así que es lo único
 * que cede espacio: sin eso una etiqueta larga ("Seña de prueba si falta en el
 * catálogo") se pasaba de largo y el switch quedaba **encima** del texto, porque
 * `SpaceBetween` sin pesos no recorta a nadie. Con el peso, la etiqueta envuelve y el
 * control queda siempre de tamaño natural y pegado a la derecha.
 */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SignAiSizes.settingRowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            if (icon != null) {
                SignAiIcon(
                    imageVector = icon,
                    contentDescription = null,
                    size = 18.dp,
                    tint = SignAiTheme.colors.muted,
                )
            }
            SignAiText(
                text = label,
                style = SignAiTheme.typography.body2,
                color = SignAiTheme.colors.text,
                modifier = Modifier.weight(1f),
            )
        }
        content()
    }
}
