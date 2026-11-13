package com.signall.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.signall.app.ui.designsystem.components.SignAllIcon
import com.signall.app.ui.designsystem.components.SignAllText
import com.signall.app.ui.designsystem.foundation.SignAllSizes
import com.signall.app.ui.theme.SignAllTheme

/**
 * Reusable settings row (spec `SET-9`): optional icon + label on the left, any
 * control on the right. Min height 50.
 *
 * La etiqueta lleva `weight(1f)` y por dentro el texto también, así que es lo único
 * que cede espacio: sin eso una etiqueta larga ("Seña de prueba si falta en el
 * catálogo") se pasaba de largo y el switch quedaba **encima** del texto, porque
 * `SpaceBetween` sin pesos no recorta a nadie. Con el peso, la etiqueta envuelve y el
 * control queda siempre de tamaño natural y pegado a la derecha.
 *
 * **Responsive** (owner, 2026-11-03): en un panel angosto esa misma regla se da
 * vuelta. Con el panel de una pantalla de 320 dp la fila queda en ~237 dp, el
 * selector de Personaje se lleva 167 dp y la etiqueta se quedaba con ~30 dp: "Personaje"
 * se partía en **tres líneas**. La fila que trae un control ancho puede pedir
 * [stackBelow]: por debajo de ese ancho se apila (etiqueta arriba, control abajo a la
 * derecha, con su tamaño táctil intacto). Las filas con switch no lo piden porque su
 * control mide 43 dp y la etiqueta nunca se queda sin lugar.
 */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    /**
     * Ancho de la fila por debajo del cual se apila. `0.dp` = nunca (filas con switch,
     * cuyo control es chico y no le quita lugar a la etiqueta).
     */
    stackBelow: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SignAllSizes.settingRowMinHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (maxWidth < stackBelow) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingLabel(label, icon, Modifier.fillMaxWidth())
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    content()
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SettingLabel(label, icon, Modifier.weight(1f))
                content()
            }
        }
    }
}

@Composable
private fun SettingLabel(label: String, icon: ImageVector?, modifier: Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        if (icon != null) {
            SignAllIcon(
                imageVector = icon,
                contentDescription = null,
                size = 18.dp,
                tint = SignAllTheme.colors.muted,
            )
        }
        SignAllText(
            text = label,
            style = SignAllTheme.typography.body2,
            color = SignAllTheme.colors.text,
            modifier = Modifier.weight(1f),
        )
    }
}
