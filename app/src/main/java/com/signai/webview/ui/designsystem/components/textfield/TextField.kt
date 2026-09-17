package com.signai.webview.ui.designsystem.components.textfield

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.designsystem.SignAiTheme
import com.signai.webview.ui.designsystem.components.textfield.base.CommonDecorationBox
import com.signai.webview.ui.designsystem.components.textfield.base.FocusedOutlineThickness
import com.signai.webview.ui.designsystem.components.textfield.base.HorizontalIconPadding
import com.signai.webview.ui.designsystem.components.textfield.base.LabelBottomPadding
import com.signai.webview.ui.designsystem.components.textfield.base.SupportingTopPadding
import com.signai.webview.ui.designsystem.components.textfield.base.TextFieldColors
import com.signai.webview.ui.designsystem.components.textfield.base.TextFieldHorizontalPadding
import com.signai.webview.ui.designsystem.components.textfield.base.TextFieldMinHeight
import com.signai.webview.ui.designsystem.components.textfield.base.TextFieldVerticalPadding
import com.signai.webview.ui.designsystem.components.textfield.base.UnfocusedOutlineThickness
import com.signai.webview.ui.designsystem.components.textfield.base.containerOutline

@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = SignAiTheme.typography.input,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    placeholder: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    label: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    shape: Shape = TextFieldDefaults.Shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    cursorBrush: Brush = SolidColor(colors.cursorColor(isError).value),
) {
    val textColor =
        textStyle.color.takeOrElse {
            colors.textColor(enabled, isError, interactionSource).value
        }
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

    CompositionLocalProvider(LocalTextSelectionColors provides colors.selectionColors) {
        BasicTextField(
            modifier =
                modifier
                    .defaultMinSize(
                        minHeight = TextFieldDefaults.MinHeight,
                    )
                    .fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = mergedTextStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            visualTransformation = visualTransformation,
            onTextLayout = onTextLayout,
            interactionSource = interactionSource,
            cursorBrush = cursorBrush,
            decorationBox = @Composable { innerTextField ->
                TextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    visualTransformation = visualTransformation,
                    label = label,
                    placeholder = placeholder,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    prefix = prefix,
                    suffix = suffix,
                    supportingText = supportingText,
                    enabled = enabled,
                    isError = isError,
                    interactionSource = interactionSource,
                    colors = colors,
                    shape = shape,
                )
            },
        )
    }
}

@Composable
fun TextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = SignAiTheme.typography.input,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    placeholder: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    label: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    shape: Shape = TextFieldDefaults.Shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    cursorBrush: Brush = SolidColor(colors.cursorColor(isError).value),
) {
    val textColor =
        textStyle.color.takeOrElse {
            colors.textColor(enabled, isError, interactionSource).value
        }
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

    CompositionLocalProvider(LocalTextSelectionColors provides colors.selectionColors) {
        BasicTextField(
            modifier =
                modifier
                    .defaultMinSize(
                        minHeight = TextFieldDefaults.MinHeight,
                    )
                    .fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = mergedTextStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            visualTransformation = visualTransformation,
            onTextLayout = onTextLayout,
            interactionSource = interactionSource,
            cursorBrush = cursorBrush,
            decorationBox = @Composable { innerTextField ->
                TextFieldDefaults.DecorationBox(
                    value = value.text,
                    innerTextField = innerTextField,
                    visualTransformation = visualTransformation,
                    label = label,
                    placeholder = placeholder,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    prefix = prefix,
                    suffix = suffix,
                    supportingText = supportingText,
                    enabled = enabled,
                    isError = isError,
                    interactionSource = interactionSource,
                    colors = TextFieldDefaults.colors(),
                    shape = shape,
                )
            },
        )
    }
}

@Immutable
object TextFieldDefaults {
    val MinHeight = TextFieldMinHeight
    val Shape: Shape = RoundedCornerShape(8.dp)

    private fun contentPadding(
        start: Dp = TextFieldHorizontalPadding,
        end: Dp = TextFieldHorizontalPadding,
        top: Dp = TextFieldVerticalPadding,
        bottom: Dp = TextFieldVerticalPadding,
    ): PaddingValues = PaddingValues(start, top, end, bottom)

    private fun labelPadding(
        start: Dp = 0.dp,
        top: Dp = 0.dp,
        end: Dp = 0.dp,
        bottom: Dp = LabelBottomPadding,
    ): PaddingValues = PaddingValues(start, top, end, bottom)

    private fun supportingTextPadding(
        start: Dp = 0.dp,
        top: Dp = SupportingTopPadding,
        end: Dp = TextFieldHorizontalPadding,
        bottom: Dp = 0.dp,
    ): PaddingValues = PaddingValues(start, top, end, bottom)

    @Composable
    private fun leadingIconPadding(
        start: Dp = HorizontalIconPadding,
        top: Dp = 0.dp,
        end: Dp = 0.dp,
        bottom: Dp = 0.dp,
    ): PaddingValues = PaddingValues(start, top, end, bottom)

    @Composable
    private fun trailingIconPadding(
        start: Dp = 0.dp,
        top: Dp = 0.dp,
        end: Dp = HorizontalIconPadding,
        bottom: Dp = 0.dp,
    ): PaddingValues = PaddingValues(start, top, end, bottom)

    @Composable
    fun containerBorderThickness(
        interactionSource: InteractionSource,
    ): Dp {
        val focused by interactionSource.collectIsFocusedAsState()

        return if (focused) FocusedOutlineThickness else UnfocusedOutlineThickness
    }

    @Composable
    fun DecorationBox(
        value: String,
        innerTextField: @Composable () -> Unit,
        enabled: Boolean,
        visualTransformation: VisualTransformation,
        interactionSource: InteractionSource,
        isError: Boolean = false,
        label: @Composable (() -> Unit)? = null,
        placeholder: @Composable (() -> Unit)? = null,
        leadingIcon: @Composable (() -> Unit)? = null,
        trailingIcon: @Composable (() -> Unit)? = null,
        prefix: @Composable (() -> Unit)? = null,
        suffix: @Composable (() -> Unit)? = null,
        supportingText: @Composable (() -> Unit)? = null,
        shape: Shape = Shape,
        colors: TextFieldColors = colors(),
        container: @Composable () -> Unit = {
            ContainerBox(enabled, isError, interactionSource, colors, shape)
        },
    ) {
        CommonDecorationBox(
            value = value,
            innerTextField = innerTextField,
            visualTransformation = visualTransformation,
            placeholder = placeholder,
            label = label,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            prefix = prefix,
            suffix = suffix,
            supportingText = supportingText,
            enabled = enabled,
            isError = isError,
            interactionSource = interactionSource,
            colors = colors,
            contentPadding = contentPadding(),
            labelPadding = labelPadding(),
            supportingTextPadding = supportingTextPadding(),
            leadingIconPadding = leadingIconPadding(),
            trailingIconPadding = trailingIconPadding(),
            container = container,
        )
    }

    @Composable
    fun ContainerBox(
        enabled: Boolean,
        isError: Boolean,
        interactionSource: InteractionSource,
        colors: TextFieldColors,
        shape: Shape = Shape,
        borderThickness: Dp = containerBorderThickness(interactionSource),
    ) {
        Box(
            Modifier
                .background(colors.containerColor(enabled, isError, interactionSource).value, shape)
                .containerOutline(enabled, isError, interactionSource, colors, borderThickness, shape),
        )
    }

    @Composable
    fun colors(): TextFieldColors {
        return TextFieldColors(
            focusedTextColor = SignAiTheme.colors.text,
            unfocusedTextColor = SignAiTheme.colors.text,
            disabledTextColor = SignAiTheme.colors.onDisabled,
            errorTextColor = SignAiTheme.colors.text,
            focusedContainerColor = SignAiTheme.colors.surface,
            unfocusedContainerColor = SignAiTheme.colors.surface,
            disabledContainerColor = SignAiTheme.colors.disabled,
            errorContainerColor = SignAiTheme.colors.surface,
            cursorColor = SignAiTheme.colors.primary,
            errorCursorColor = SignAiTheme.colors.error,
            textSelectionColors = LocalTextSelectionColors.current,
            focusedOutlineColor = SignAiTheme.colors.transparent,
            unfocusedOutlineColor = SignAiTheme.colors.transparent,
            disabledOutlineColor = SignAiTheme.colors.transparent,
            errorOutlineColor = SignAiTheme.colors.error,
            focusedLeadingIconColor = SignAiTheme.colors.primary,
            unfocusedLeadingIconColor = SignAiTheme.colors.primary,
            disabledLeadingIconColor = SignAiTheme.colors.onDisabled,
            errorLeadingIconColor = SignAiTheme.colors.primary,
            focusedTrailingIconColor = SignAiTheme.colors.primary,
            unfocusedTrailingIconColor = SignAiTheme.colors.primary,
            disabledTrailingIconColor = SignAiTheme.colors.onDisabled,
            errorTrailingIconColor = SignAiTheme.colors.primary,
            focusedLabelColor = SignAiTheme.colors.primary,
            unfocusedLabelColor = SignAiTheme.colors.primary,
            disabledLabelColor = SignAiTheme.colors.textDisabled,
            errorLabelColor = SignAiTheme.colors.error,
            focusedPlaceholderColor = SignAiTheme.colors.textSecondary,
            unfocusedPlaceholderColor = SignAiTheme.colors.textSecondary,
            disabledPlaceholderColor = SignAiTheme.colors.textDisabled,
            errorPlaceholderColor = SignAiTheme.colors.textSecondary,
            focusedSupportingTextColor = SignAiTheme.colors.primary,
            unfocusedSupportingTextColor = SignAiTheme.colors.primary,
            disabledSupportingTextColor = SignAiTheme.colors.textDisabled,
            errorSupportingTextColor = SignAiTheme.colors.error,
            focusedPrefixColor = SignAiTheme.colors.primary,
            unfocusedPrefixColor = SignAiTheme.colors.primary,
            disabledPrefixColor = SignAiTheme.colors.onDisabled,
            errorPrefixColor = SignAiTheme.colors.primary,
            focusedSuffixColor = SignAiTheme.colors.primary,
            unfocusedSuffixColor = SignAiTheme.colors.primary,
            disabledSuffixColor = SignAiTheme.colors.onDisabled,
            errorSuffixColor = SignAiTheme.colors.primary,
        )
    }
}
