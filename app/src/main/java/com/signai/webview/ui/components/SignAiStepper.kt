package com.signai.webview.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.foundation.SignAiSizes
import com.signai.webview.ui.icons.SignAiIcons
import com.signai.webview.ui.theme.SignAiTheme

/**
 * Stepper for a bounded integer (spec `ACC-1`, "Tamaño de interfaz").
 *
 * El owner pidió dos botones en vez de la barra (2026-10-16): una barra obliga a
 * apuntar a un valor exacto — en un drawer angosto y con la fuente escalada es
 * difícil — y no hay forma de saber en qué punto se está. Con los botones se avanza
 * de a poco y se ve el número.
 *
 * El paso es de 5 porque `uiScale` va de 0.9 a 1.1 en 20 tramos: 5 en
 * `interfaceSize` es 0.01 de densidad, que se nota pero no salta.
 */
private const val STEP = 5

/**
 * Valor al que lleva un toque, **sobre la grilla de [step]**.
 *
 * Un valor viejo puede no ser múltiplo del paso: la velocidad era un slider y en
 * el teléfono del owner había quedado en 52. Sumar o restar el paso a secas daba
 * 42 y 62, que no son valores del control y hacían que nunca volviera a caer en
 * 50, 60… Con esto el primer toque ya ordena el valor: desde 52, «−» va a 50 y
 * «+» a 60. Desde un valor ya alineado se comporta como siempre.
 */
private fun steppedValue(
    current: Int,
    step: Int,
    min: Int,
    max: Int,
    increase: Boolean,
): Int {
    val offset = (current - min).coerceAtLeast(0)
    val index = if (increase) {
        offset / step + 1
    } else {
        (offset + step - 1) / step - 1
    }
    return (min + index * step).coerceIn(min, max)
}

@Composable
fun SignAiStepper(
    value: Int,
    onValue: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = STEP,
    minValue: Int = 0,
    maxValue: Int = 100,
    label: String,
    decreaseContentDescription: String = "Reducir $label",
    increaseContentDescription: String = "Aumentar $label",
) {
    val canDecrease = value > minValue
    val canIncrease = value < maxValue
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        SignAiSmallIconButton(
            icon = SignAiIcons.Minus,
            contentDescription = decreaseContentDescription,
            enabled = canDecrease,
            onClick = {
                onValue(steppedValue(value, step, minValue, maxValue, increase = false))
            },
        )
        // El número va centrado **entre los dos botones**, no pegado al "−": con un
        // ancho fijo y sin `contentAlignment` el texto se dibujaba en la esquina
        // superior izquierda y el grupo se veía descuadrado. En negrita para que se lea
        // como el valor actual y no como una etiqueta más de la fila.
        Box(
            modifier = Modifier
                .size(width = 26.dp, height = SignAiSizes.iconButtonSmall)
                // El rótulo de la fila ya dice de qué es el número; los botones sí
                // necesitan nombre propio porque no tienen texto visible.
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            SignAiText(
                text = value.toString(),
                style = SignAiTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
                color = SignAiTheme.colors.text,
            )
        }
        SignAiSmallIconButton(
            icon = SignAiIcons.Plus,
            contentDescription = increaseContentDescription,
            enabled = canIncrease,
            onClick = {
                onValue(steppedValue(value, step, minValue, maxValue, increase = true))
            },
        )
    }
}
