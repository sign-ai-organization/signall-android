package com.signall.app.ui.designsystem.components.textfield.base

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse

/**
 * Los cuatro colores de un mismo rol —el texto del campo, su etiqueta, el borde…—,
 * uno por estado del campo.
 *
 * Se agrupa por rol y no por estado para que el `copy` de [TextFieldColors] siga
 * leyéndose: cada sección del campo se cambia con un solo nombre.
 */
@Immutable
class TextFieldStateColors(
    val focused: Color,
    val unfocused: Color,
    val disabled: Color,
    val error: Color,
) {
    fun copy(
        focused: Color = this.focused,
        unfocused: Color = this.unfocused,
        disabled: Color = this.disabled,
        error: Color = this.error,
    ) = TextFieldStateColors(
        focused = focused.takeOrElse { this.focused },
        unfocused = unfocused.takeOrElse { this.unfocused },
        disabled = disabled.takeOrElse { this.disabled },
        error = error.takeOrElse { this.error },
    )

    internal fun colorFor(
        enabled: Boolean,
        isError: Boolean,
        isFocused: Boolean,
    ): Color = when {
        !enabled -> disabled
        isError -> error
        isFocused -> focused
        else -> unfocused
    }
}

/** El fondo del contenedor y su borde. */
@Immutable
class TextFieldContainerColors(
    val background: TextFieldStateColors,
    val border: TextFieldStateColors,
) {
    fun copy(
        background: TextFieldStateColors = this.background,
        border: TextFieldStateColors = this.border,
    ) = TextFieldContainerColors(background = background, border = border)
}

/** Todo lo que es texto adentro del campo. */
@Immutable
class TextFieldTextColors(
    val input: TextFieldStateColors,
    val label: TextFieldStateColors,
    val placeholder: TextFieldStateColors,
    val supporting: TextFieldStateColors,
    val prefix: TextFieldStateColors,
    val suffix: TextFieldStateColors,
) {
    fun copy(
        input: TextFieldStateColors = this.input,
        label: TextFieldStateColors = this.label,
        placeholder: TextFieldStateColors = this.placeholder,
        supporting: TextFieldStateColors = this.supporting,
        prefix: TextFieldStateColors = this.prefix,
        suffix: TextFieldStateColors = this.suffix,
    ) = TextFieldTextColors(
        input = input,
        label = label,
        placeholder = placeholder,
        supporting = supporting,
        prefix = prefix,
        suffix = suffix,
    )
}

/** Los iconos de los extremos. */
@Immutable
class TextFieldIconColors(
    val leading: TextFieldStateColors,
    val trailing: TextFieldStateColors,
) {
    fun copy(
        leading: TextFieldStateColors = this.leading,
        trailing: TextFieldStateColors = this.trailing,
    ) = TextFieldIconColors(leading = leading, trailing = trailing)
}

/** El cursor. El resto de la selección vive en [TextFieldColors.textSelectionColors]. */
@Immutable
class TextFieldCursorColors(
    val color: Color,
    val errorColor: Color,
) {
    fun copy(
        color: Color = this.color,
        errorColor: Color = this.errorColor,
    ) = TextFieldCursorColors(
        color = color.takeOrElse { this.color },
        errorColor = errorColor.takeOrElse { this.errorColor },
    )
}

@Immutable
class TextFieldColors(
    val container: TextFieldContainerColors,
    val text: TextFieldTextColors,
    val icons: TextFieldIconColors,
    val cursor: TextFieldCursorColors,
    val textSelectionColors: TextSelectionColors,
) {
    fun copy(
        container: TextFieldContainerColors = this.container,
        text: TextFieldTextColors = this.text,
        icons: TextFieldIconColors = this.icons,
        cursor: TextFieldCursorColors = this.cursor,
        textSelectionColors: TextSelectionColors? = this.textSelectionColors,
    ) = TextFieldColors(
        container = container,
        text = text,
        icons = icons,
        cursor = cursor,
        textSelectionColors = textSelectionColors.takeOrElse { this.textSelectionColors },
    )

    private fun TextSelectionColors?.takeOrElse(block: () -> TextSelectionColors): TextSelectionColors = this ?: block()

    @Composable
    internal fun leadingIconColor(
        enabled: Boolean,
        isError: Boolean,
        interactionSource: InteractionSource,
    ): State<Color> = icons.leading.stateColor(enabled, isError, interactionSource)

    @Composable
    internal fun trailingIconColor(
        enabled: Boolean,
        isError: Boolean,
        interactionSource: InteractionSource,
    ): State<Color> = icons.trailing.stateColor(enabled, isError, interactionSource)

    @Composable
    internal fun labelColor(
        enabled: Boolean,
        isError: Boolean,
        interactionSource: InteractionSource,
    ): State<Color> = text.label.stateColor(enabled, isError, interactionSource)

    @Composable
    internal fun placeholderColor(
        enabled: Boolean,
        isError: Boolean,
        interactionSource: InteractionSource,
    ): State<Color> = text.placeholder.stateColor(enabled, isError, interactionSource)

    @Composable
    internal fun supportingTextColor(
        enabled: Boolean,
        isError: Boolean,
        interactionSource: InteractionSource,
    ): State<Color> = text.supporting.stateColor(enabled, isError, interactionSource)

    @Composable
    internal fun prefixColor(
        enabled: Boolean,
        isError: Boolean,
        interactionSource: InteractionSource,
    ): State<Color> = text.prefix.stateColor(enabled, isError, interactionSource)

    @Composable
    internal fun suffixColor(
        enabled: Boolean,
        isError: Boolean,
        interactionSource: InteractionSource,
    ): State<Color> = text.suffix.stateColor(enabled, isError, interactionSource)

    @Composable
    internal fun containerColor(
        enabled: Boolean,
        isError: Boolean,
        interactionSource: InteractionSource,
    ): State<Color> = container.background.stateColor(enabled, isError, interactionSource)

    @Composable
    fun containerOutlineColor(
        enabled: Boolean,
        isError: Boolean,
        interactionSource: InteractionSource,
    ): State<Color> = container.border.stateColor(enabled, isError, interactionSource)

    @Composable
    internal fun textColor(
        enabled: Boolean,
        isError: Boolean,
        interactionSource: InteractionSource,
    ): State<Color> = text.input.stateColor(enabled, isError, interactionSource)

    @Composable
    internal fun cursorColor(isError: Boolean): State<Color> {
        return rememberUpdatedState(if (isError) cursor.errorColor else cursor.color)
    }

    internal val selectionColors: TextSelectionColors
        @Composable get() = textSelectionColors
}

/**
 * Atajo para escribir un rol completo de [TextFieldColors] con sus cuatro estados.
 * Existe para que los `colors()` de cada variante no queden en cuatro líneas por rol.
 */
internal fun textFieldStateColors(
    focused: Color,
    unfocused: Color,
    disabled: Color,
    error: Color,
): TextFieldStateColors = TextFieldStateColors(
    focused = focused,
    unfocused = unfocused,
    disabled = disabled,
    error = error,
)

@Composable
private fun TextFieldStateColors.stateColor(
    enabled: Boolean,
    isError: Boolean,
    interactionSource: InteractionSource,
): State<Color> {
    val focused by interactionSource.collectIsFocusedAsState()

    return rememberUpdatedState(colorFor(enabled, isError, focused))
}
