package com.signall.app.ui.designsystem.components.textfield

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.signall.app.ui.designsystem.SignAllTheme
import com.signall.app.ui.designsystem.components.textfield.base.CommonDecorationBox
import com.signall.app.ui.designsystem.components.textfield.base.FocusedOutlineThickness
import com.signall.app.ui.designsystem.components.textfield.base.HorizontalIconPadding
import com.signall.app.ui.designsystem.components.textfield.base.SupportingTopPadding
import com.signall.app.ui.designsystem.components.textfield.base.TextFieldColors
import com.signall.app.ui.designsystem.components.textfield.base.TextFieldContainerColors
import com.signall.app.ui.designsystem.components.textfield.base.TextFieldCursorColors
import com.signall.app.ui.designsystem.components.textfield.base.TextFieldDecorationState
import com.signall.app.ui.designsystem.components.textfield.base.TextFieldHorizontalPadding
import com.signall.app.ui.designsystem.components.textfield.base.TextFieldIconColors
import com.signall.app.ui.designsystem.components.textfield.base.TextFieldMinHeight
import com.signall.app.ui.designsystem.components.textfield.base.TextFieldPaddings
import com.signall.app.ui.designsystem.components.textfield.base.TextFieldTextColors
import com.signall.app.ui.designsystem.components.textfield.base.TextFieldVerticalPadding
import com.signall.app.ui.designsystem.components.textfield.base.UnfocusedOutlineThickness
import com.signall.app.ui.designsystem.components.textfield.base.containerUnderline
import com.signall.app.ui.designsystem.components.textfield.base.textFieldStateColors

@Composable
fun UnderlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    options: TextFieldOptions = TextFieldOptions(),
    decorations: TextFieldDecorations = TextFieldDecorations(),
    visuals: TextFieldVisuals = UnderlinedTextFieldDefaults.visuals(),
    input: TextFieldInput = TextFieldInput(),
) {
    val colors = visuals.colors
    val isError = options.isError
    val defaultInteractionSource = remember { MutableInteractionSource() }
    val interactionSource = input.interactionSource ?: defaultInteractionSource
    val textColor =
        visuals.textStyle.color.takeOrElse {
            colors.textColor(options.enabled, isError, interactionSource).value
        }
    val mergedTextStyle = visuals.textStyle.merge(TextStyle(color = textColor))
    val cursorBrush = visuals.cursorBrush ?: SolidColor(colors.cursorColor(isError).value)

    CompositionLocalProvider(LocalTextSelectionColors provides colors.selectionColors) {
        BasicTextField(
            modifier =
                modifier
                    .defaultMinSize(
                        minHeight = UnderlinedTextFieldDefaults.MinHeight,
                    )
                    .fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            enabled = options.enabled,
            readOnly = options.readOnly,
            textStyle = mergedTextStyle,
            keyboardOptions = input.keyboardOptions,
            keyboardActions = input.keyboardActions,
            singleLine = options.singleLine,
            maxLines = options.maxLines,
            minLines = options.minLines,
            visualTransformation = options.visualTransformation,
            onTextLayout = input.onTextLayout,
            interactionSource = interactionSource,
            cursorBrush = cursorBrush,
            decorationBox = @Composable { innerTextField ->
                UnderlinedTextFieldDefaults.DecorationBox(
                    state =
                        TextFieldDecorationState(
                            value = value,
                            options = options,
                            decorations = decorations,
                            colors = colors,
                            interactionSource = interactionSource,
                        ),
                    innerTextField = innerTextField,
                )
            },
        )
    }
}

@Composable
fun UnderlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    options: TextFieldOptions = TextFieldOptions(),
    decorations: TextFieldDecorations = TextFieldDecorations(),
    visuals: TextFieldVisuals = UnderlinedTextFieldDefaults.visuals(),
    input: TextFieldInput = TextFieldInput(),
) {
    val colors = visuals.colors
    val isError = options.isError
    val defaultInteractionSource = remember { MutableInteractionSource() }
    val interactionSource = input.interactionSource ?: defaultInteractionSource
    val textColor =
        visuals.textStyle.color.takeOrElse {
            colors.textColor(options.enabled, isError, interactionSource).value
        }
    val mergedTextStyle = visuals.textStyle.merge(TextStyle(color = textColor))
    val cursorBrush = visuals.cursorBrush ?: SolidColor(colors.cursorColor(isError).value)

    CompositionLocalProvider(LocalTextSelectionColors provides colors.selectionColors) {
        BasicTextField(
            modifier =
                modifier
                    .defaultMinSize(
                        minHeight = UnderlinedTextFieldDefaults.MinHeight,
                    )
                    .fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            enabled = options.enabled,
            readOnly = options.readOnly,
            textStyle = mergedTextStyle,
            keyboardOptions = input.keyboardOptions,
            keyboardActions = input.keyboardActions,
            singleLine = options.singleLine,
            maxLines = options.maxLines,
            minLines = options.minLines,
            visualTransformation = options.visualTransformation,
            onTextLayout = input.onTextLayout,
            interactionSource = interactionSource,
            cursorBrush = cursorBrush,
            decorationBox = @Composable { innerTextField ->
                UnderlinedTextFieldDefaults.DecorationBox(
                    state =
                        TextFieldDecorationState(
                            value = value.text,
                            options = options,
                            decorations = decorations,
                            colors = UnderlinedTextFieldDefaults.colors(),
                            interactionSource = interactionSource,
                        ),
                    innerTextField = innerTextField,
                )
            },
        )
    }
}

@Immutable
object UnderlinedTextFieldDefaults {
    val MinHeight = TextFieldMinHeight

    private fun contentPadding(
        start: Dp = 0.dp,
        end: Dp = 0.dp,
        top: Dp = TextFieldVerticalPadding,
        bottom: Dp = TextFieldVerticalPadding,
    ): PaddingValues = PaddingValues(start, top, end, bottom)

    private fun labelPadding(
        start: Dp = 0.dp,
        top: Dp = 0.dp,
        end: Dp = 0.dp,
        bottom: Dp = 0.dp,
    ): PaddingValues = PaddingValues(start, top, end, bottom)

    private fun supportingTextPadding(
        start: Dp = 0.dp,
        top: Dp = SupportingTopPadding,
        end: Dp = TextFieldHorizontalPadding,
        bottom: Dp = 0.dp,
    ): PaddingValues = PaddingValues(start, top, end, bottom)

    @Composable
    private fun leadingIconPadding(
        start: Dp = 0.dp,
        top: Dp = 0.dp,
        end: Dp = HorizontalIconPadding / 2,
        bottom: Dp = 0.dp,
    ): PaddingValues = PaddingValues(start, top, end, bottom)

    @Composable
    private fun trailingIconPadding(
        start: Dp = HorizontalIconPadding / 2,
        top: Dp = 0.dp,
        end: Dp = 0.dp,
        bottom: Dp = 0.dp,
    ): PaddingValues = PaddingValues(start, top, end, bottom)

    @Composable
    internal fun paddings(): TextFieldPaddings = TextFieldPaddings(
        content = contentPadding(),
        label = labelPadding(),
        supportingText = supportingTextPadding(),
        leadingIcon = leadingIconPadding(),
        trailingIcon = trailingIconPadding(),
    )

    @Composable
    fun visuals(): TextFieldVisuals = TextFieldVisuals(
        textStyle = SignAllTheme.typography.input,
        colors = colors(),
    )

    @Composable
    fun containerBorderThickness(
        interactionSource: InteractionSource,
    ): Dp {
        val focused by interactionSource.collectIsFocusedAsState()

        return if (focused) FocusedOutlineThickness else UnfocusedOutlineThickness
    }

    @Composable
    internal fun DecorationBox(
        state: TextFieldDecorationState,
        innerTextField: @Composable () -> Unit,
        container: @Composable () -> Unit = {
            ContainerBox(
                enabled = state.options.enabled,
                isError = state.options.isError,
                interactionSource = state.interactionSource,
                colors = state.colors,
            )
        },
    ) {
        CommonDecorationBox(
            state = state,
            innerTextField = innerTextField,
            paddings = paddings(),
            container = container,
        )
    }

    @Composable
    fun ContainerBox(
        enabled: Boolean,
        isError: Boolean,
        interactionSource: InteractionSource,
        colors: TextFieldColors,
        borderThickness: Dp = containerBorderThickness(interactionSource),
    ) {
        Box(
            Modifier
                .background(colors.containerColor(enabled, isError, interactionSource).value)
                .containerUnderline(enabled, isError, interactionSource, colors, borderThickness),
        )
    }

    @Composable
    fun colors(): TextFieldColors {
        return TextFieldColors(
            container = TextFieldContainerColors(
                background = textFieldStateColors(
                    focused = SignAllTheme.colors.transparent,
                    unfocused = SignAllTheme.colors.transparent,
                    disabled = SignAllTheme.colors.transparent,
                    error = SignAllTheme.colors.transparent,
                ),
                border = textFieldStateColors(
                    focused = SignAllTheme.colors.primary,
                    unfocused = SignAllTheme.colors.secondary,
                    disabled = SignAllTheme.colors.disabled,
                    error = SignAllTheme.colors.error,
                ),
            ),
            text = TextFieldTextColors(
                input = textFieldStateColors(
                    focused = SignAllTheme.colors.text,
                    unfocused = SignAllTheme.colors.text,
                    disabled = SignAllTheme.colors.onDisabled,
                    error = SignAllTheme.colors.text,
                ),
                label = textFieldStateColors(
                    focused = SignAllTheme.colors.primary,
                    unfocused = SignAllTheme.colors.primary,
                    disabled = SignAllTheme.colors.textDisabled,
                    error = SignAllTheme.colors.error,
                ),
                placeholder = textFieldStateColors(
                    focused = SignAllTheme.colors.textSecondary,
                    unfocused = SignAllTheme.colors.textSecondary,
                    disabled = SignAllTheme.colors.textDisabled,
                    error = SignAllTheme.colors.textSecondary,
                ),
                supporting = textFieldStateColors(
                    focused = SignAllTheme.colors.primary,
                    unfocused = SignAllTheme.colors.primary,
                    disabled = SignAllTheme.colors.textDisabled,
                    error = SignAllTheme.colors.error,
                ),
                prefix = textFieldStateColors(
                    focused = SignAllTheme.colors.primary,
                    unfocused = SignAllTheme.colors.primary,
                    disabled = SignAllTheme.colors.onDisabled,
                    error = SignAllTheme.colors.primary,
                ),
                suffix = textFieldStateColors(
                    focused = SignAllTheme.colors.primary,
                    unfocused = SignAllTheme.colors.primary,
                    disabled = SignAllTheme.colors.onDisabled,
                    error = SignAllTheme.colors.primary,
                ),
            ),
            icons = TextFieldIconColors(
                leading = textFieldStateColors(
                    focused = SignAllTheme.colors.primary,
                    unfocused = SignAllTheme.colors.primary,
                    disabled = SignAllTheme.colors.onDisabled,
                    error = SignAllTheme.colors.primary,
                ),
                trailing = textFieldStateColors(
                    focused = SignAllTheme.colors.primary,
                    unfocused = SignAllTheme.colors.primary,
                    disabled = SignAllTheme.colors.onDisabled,
                    error = SignAllTheme.colors.primary,
                ),
            ),
            cursor = TextFieldCursorColors(
                color = SignAllTheme.colors.primary,
                errorColor = SignAllTheme.colors.error,
            ),
            textSelectionColors = LocalTextSelectionColors.current,
        )
    }
}
