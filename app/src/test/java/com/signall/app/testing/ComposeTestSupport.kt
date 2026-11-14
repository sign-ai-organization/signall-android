package com.signall.app.testing

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import com.signall.app.ui.theme.SignAllTheme

/**
 * Ayudas para las pruebas de composables.
 *
 * `tap()` es un toque **por la acción de semántica** y no por el puntero. Con un
 * `BasicTextField` en la misma composición —lo tiene el panel de control—, un
 * `performClick()` sobre un nodo hermano se pierde en Robolectric: el nodo tiene
 * sus límites y su `OnClick`, pero el evento no llega. La acción de semántica es
 * la misma que dispara un lector de pantalla y ejecuta el `onClick` real, que es
 * lo que interesa comprobar acá.
 */
fun SemanticsNodeInteraction.tap(): SemanticsNodeInteraction =
    performSemanticsAction(SemanticsActions.OnClick)

/** Envuelve el contenido en el tema real, que es de donde salen los colores. */
@Composable
fun Themed(
    darkTheme: Boolean = true,
    reduceMotion: Boolean = false,
    bold: Boolean = false,
    content: @Composable () -> Unit,
) {
    SignAllTheme(
        darkTheme = darkTheme,
        reduceMotion = reduceMotion,
        bold = bold,
        content = content,
    )
}
