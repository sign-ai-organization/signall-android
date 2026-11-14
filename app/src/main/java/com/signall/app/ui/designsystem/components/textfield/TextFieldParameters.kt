package com.signall.app.ui.designsystem.components.textfield

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import com.signall.app.ui.designsystem.components.textfield.base.TextFieldColors

/**
 * Cómo se comporta el campo: si acepta foco, si acepta escritura, cuántas líneas
 * muestra y si está en error. Es lo que cambia con el estado de la app.
 */
data class TextFieldOptions(
    val enabled: Boolean = true,
    val readOnly: Boolean = false,
    val singleLine: Boolean = false,
    val minLines: Int = 1,
    val maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    val isError: Boolean = false,
    val visualTransformation: VisualTransformation = VisualTransformation.None,
)

/**
 * Las ranuras de contenido que acompañan al texto. Todas son opcionales: el campo
 * se arma con las que no sean `null`.
 */
data class TextFieldDecorations(
    val label: @Composable (() -> Unit)? = null,
    val placeholder: @Composable (() -> Unit)? = null,
    val leadingIcon: @Composable (() -> Unit)? = null,
    val trailingIcon: @Composable (() -> Unit)? = null,
    val prefix: @Composable (() -> Unit)? = null,
    val suffix: @Composable (() -> Unit)? = null,
    val supportingText: @Composable (() -> Unit)? = null,
)

/**
 * Cómo se ve: tipografía, forma del contenedor y colores.
 *
 * @param shape la forma del contenedor. La variante subrayada no la dibuja, así que
 * su valor por defecto no cambia nada.
 * @param cursorBrush `null` deja que el cursor salga de [colors], que es el
 * comportamiento por defecto y el que sigue al color del campo.
 */
data class TextFieldVisuals(
    val textStyle: TextStyle,
    val shape: Shape = RectangleShape,
    val colors: TextFieldColors,
    val cursorBrush: Brush? = null,
)

/**
 * Lo que entra por el teclado y los avisos de layout del texto.
 *
 * @param interactionSource `null` deja que el campo cree el suyo. Va acá y no en la
 * firma del composable porque un `remember` no puede vivir en el default de un
 * grupo de parámetros.
 */
data class TextFieldInput(
    val keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    val keyboardActions: KeyboardActions = KeyboardActions.Default,
    val onTextLayout: (TextLayoutResult) -> Unit = {},
    val interactionSource: MutableInteractionSource? = null,
)
