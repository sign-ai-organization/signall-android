package com.signall.app.ui.designsystem.components

// CUSTOMIZED — Lumo-generated (`lumo --add Button`). Overrides applied:
//   - The look lives in `ButtonAppearance` (variant, shape, padding,
//     interactionSource): the variant still picks the real `ButtonVariant`
//     colors, and call sites can tweak the shape token without injecting custom
//     colors. Bundling it also keeps `Button`'s signature at 7 parameters.
//   - `DefaultButtonContent` uses `BasicText` (Fase 4, no material3).
// Do NOT re-run `lumo --add Button` (it would overwrite these changes).

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.signall.app.ui.designsystem.SignAllTheme
import com.signall.app.ui.designsystem.LocalContentColor
import com.signall.app.ui.designsystem.foundation.ButtonElevation
import androidx.compose.ui.tooling.preview.Preview

/**
 * Lo visual de un [Button]: qué [variant] elige los colores, más la forma, el
 * relleno interno y el `interactionSource` que antes viajaban sueltos en la
 * firma. `shape` en `null` significa "la forma del variant".
 *
 * El `interactionSource` arranca en `null` como centinela: no se puede usar
 * `remember` en el default de un `data class`, así que el composable lo resuelve.
 */
@Immutable
data class ButtonAppearance(
    val variant: ButtonVariant = ButtonVariant.Primary,
    val shape: Shape? = null,
    val contentPadding: PaddingValues = ButtonDefaults.contentPadding,
    val interactionSource: MutableInteractionSource? = null,
)

@Composable
fun Button(
    modifier: Modifier = Modifier,
    text: String? = null,
    enabled: Boolean = true,
    appearance: ButtonAppearance = ButtonAppearance(),
    onClick: () -> Unit = {},
    content: (@Composable () -> Unit)? = null,
) {
    val baseStyle = buttonStyleFor(appearance.variant)
    val interactionSource = appearance.interactionSource ?: remember { MutableInteractionSource() }
    ButtonComponent(
        text = text,
        modifier = modifier,
        enabled = enabled,
        style =
            baseStyle.copy(
                shape = appearance.shape ?: baseStyle.shape,
                contentPadding = appearance.contentPadding,
            ),
        onClick = onClick,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * `style` tiene que venir ya resuelto por el llamador (variant + forma + relleno);
 * acá solo se usa para pintar.
 */
@Composable
internal fun ButtonComponent(
    text: String? = null,
    modifier: Modifier,
    enabled: Boolean = true,
    style: ButtonStyle,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: (@Composable () -> Unit)? = null,
) {
    val containerColor = style.colors.containerColor(enabled).value
    val contentColor = style.colors.contentColor(enabled).value
    val borderColor = style.colors.borderColor(enabled).value
    val borderStroke =
        if (borderColor != null) {
            BorderStroke(
                ButtonDefaults.OutlineHeight,
                borderColor,
            )
        } else {
            null
        }

    val shadowElevation = style.elevation?.shadowElevation(enabled, interactionSource)?.value ?: 0.dp

//    in case of full width button
//    val buttonModifier = modifier.fillMaxWidth()

    Surface(
        onClick = onClick,
        modifier =
            modifier
                .defaultMinSize(minHeight = ButtonDefaults.MinHeight)
                .semantics { role = Role.Button },
        enabled = enabled,
        style =
            SurfaceStyle(
                shape = style.shape,
                color = containerColor,
                contentColor = contentColor,
                shadowElevation = shadowElevation,
                border = borderStroke,
            ),
        interactionSource = interactionSource,
    ) {
        DefaultButtonContent(
            text = text,
            contentColor = contentColor,
            content = content,
            modifier = Modifier.padding(style.contentPadding),
        )
    }
}

@Composable
private fun DefaultButtonContent(
    modifier: Modifier = Modifier,
    text: String? = null,
    contentColor: Color,
    content: (@Composable () -> Unit)? = null,
) {
    if (text?.isEmpty() == false) {
        Row(
            modifier,
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = text,
                style = SignAllTheme.typography.button.copy(
                    color = contentColor,
                    textAlign = TextAlign.Center,
                ),
                overflow = TextOverflow.Clip,
            )
        }
    } else if (content != null) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

enum class ButtonVariant {
    Primary,
    PrimaryOutlined,
    PrimaryElevated,
    PrimaryGhost,
    Secondary,
    SecondaryOutlined,
    SecondaryElevated,
    SecondaryGhost,
    Destructive,
    DestructiveOutlined,
    DestructiveElevated,
    DestructiveGhost,
    Ghost,
}

@Composable
internal fun buttonStyleFor(variant: ButtonVariant): ButtonStyle {
    return when (variant) {
        ButtonVariant.Primary -> ButtonDefaults.primaryFilled()
        ButtonVariant.PrimaryOutlined -> ButtonDefaults.primaryOutlined()
        ButtonVariant.PrimaryElevated -> ButtonDefaults.primaryElevated()
        ButtonVariant.PrimaryGhost -> ButtonDefaults.primaryGhost()
        ButtonVariant.Secondary -> ButtonDefaults.secondaryFilled()
        ButtonVariant.SecondaryOutlined -> ButtonDefaults.secondaryOutlined()
        ButtonVariant.SecondaryElevated -> ButtonDefaults.secondaryElevated()
        ButtonVariant.SecondaryGhost -> ButtonDefaults.secondaryGhost()
        ButtonVariant.Destructive -> ButtonDefaults.destructiveFilled()
        ButtonVariant.DestructiveOutlined -> ButtonDefaults.destructiveOutlined()
        ButtonVariant.DestructiveElevated -> ButtonDefaults.destructiveElevated()
        ButtonVariant.DestructiveGhost -> ButtonDefaults.destructiveGhost()
        ButtonVariant.Ghost -> ButtonDefaults.ghost()
    }
}

internal object ButtonDefaults {
    internal val MinHeight = 44.dp
    internal val OutlineHeight = 1.dp
    private val ButtonHorizontalPadding = 16.dp
    private val ButtonVerticalPadding = 8.dp
    private val ButtonShape = RoundedCornerShape(12)

    val contentPadding =
        PaddingValues(
            start = ButtonHorizontalPadding,
            top = ButtonVerticalPadding,
            end = ButtonHorizontalPadding,
            bottom = ButtonVerticalPadding,
        )

    private val filledShape = ButtonShape
    private val elevatedShape = ButtonShape
    private val outlinedShape = ButtonShape

    @Composable
    fun buttonElevation() =
        ButtonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 2.dp,
            focusedElevation = 2.dp,
            hoveredElevation = 2.dp,
            disabledElevation = 0.dp,
        )

    @Composable
    fun primaryFilled() =
        ButtonStyle(
            colors =
                ButtonColors(
                    containerColor = SignAllTheme.colors.primary,
                    contentColor = SignAllTheme.colors.onPrimary,
                    disabledContainerColor = SignAllTheme.colors.disabled,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                ),
            shape = filledShape,
            elevation = null,
            contentPadding = contentPadding,
        )

    @Composable
    fun primaryElevated() =
        ButtonStyle(
            colors =
                ButtonColors(
                    containerColor = SignAllTheme.colors.primary,
                    contentColor = SignAllTheme.colors.onPrimary,
                    disabledContainerColor = SignAllTheme.colors.disabled,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                ),
            shape = elevatedShape,
            elevation = buttonElevation(),
            contentPadding = contentPadding,
        )

    @Composable
    fun primaryOutlined() =
        ButtonStyle(
            colors =
                ButtonColors(
                    containerColor = SignAllTheme.colors.transparent,
                    contentColor = SignAllTheme.colors.primary,
                    borderColor = SignAllTheme.colors.primary,
                    disabledContainerColor = SignAllTheme.colors.transparent,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                    disabledBorderColor = SignAllTheme.colors.disabled,
                ),
            shape = outlinedShape,
            elevation = null,
            contentPadding = contentPadding,
        )

    @Composable
    fun primaryGhost() =
        ButtonStyle(
            colors =
                ButtonColors(
                    containerColor = SignAllTheme.colors.transparent,
                    contentColor = SignAllTheme.colors.primary,
                    borderColor = SignAllTheme.colors.transparent,
                    disabledContainerColor = SignAllTheme.colors.transparent,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                    disabledBorderColor = SignAllTheme.colors.transparent,
                ),
            shape = filledShape,
            elevation = null,
            contentPadding = contentPadding,
        )

    @Composable
    fun secondaryFilled() =
        ButtonStyle(
            colors =
                ButtonColors(
                    containerColor = SignAllTheme.colors.secondary,
                    contentColor = SignAllTheme.colors.onSecondary,
                    disabledContainerColor = SignAllTheme.colors.disabled,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                ),
            shape = filledShape,
            elevation = null,
            contentPadding = contentPadding,
        )

    @Composable
    fun secondaryElevated() =
        ButtonStyle(
            colors =
                ButtonColors(
                    containerColor = SignAllTheme.colors.secondary,
                    contentColor = SignAllTheme.colors.onSecondary,
                    disabledContainerColor = SignAllTheme.colors.disabled,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                ),
            shape = elevatedShape,
            elevation = buttonElevation(),
            contentPadding = contentPadding,
        )

    @Composable
    fun secondaryOutlined() =
        ButtonStyle(
            colors =
                ButtonColors(
                    containerColor = SignAllTheme.colors.transparent,
                    contentColor = SignAllTheme.colors.secondary,
                    borderColor = SignAllTheme.colors.secondary,
                    disabledContainerColor = SignAllTheme.colors.transparent,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                    disabledBorderColor = SignAllTheme.colors.disabled,
                ),
            shape = outlinedShape,
            elevation = null,
            contentPadding = contentPadding,
        )

    @Composable
    fun secondaryGhost() =
        ButtonStyle(
            colors =
                ButtonColors(
                    containerColor = SignAllTheme.colors.transparent,
                    contentColor = SignAllTheme.colors.secondary,
                    borderColor = SignAllTheme.colors.transparent,
                    disabledContainerColor = SignAllTheme.colors.transparent,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                    disabledBorderColor = SignAllTheme.colors.transparent,
                ),
            shape = filledShape,
            elevation = null,
            contentPadding = contentPadding,
        )

    @Composable
    fun destructiveFilled() =
        ButtonStyle(
            colors =
                ButtonColors(
                    containerColor = SignAllTheme.colors.error,
                    contentColor = SignAllTheme.colors.onError,
                    disabledContainerColor = SignAllTheme.colors.disabled,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                ),
            shape = filledShape,
            elevation = null,
            contentPadding = contentPadding,
        )

    @Composable
    fun destructiveElevated() =
        ButtonStyle(
            colors =
                ButtonColors(
                    containerColor = SignAllTheme.colors.error,
                    contentColor = SignAllTheme.colors.onError,
                    disabledContainerColor = SignAllTheme.colors.disabled,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                ),
            shape = elevatedShape,
            elevation = buttonElevation(),
            contentPadding = contentPadding,
        )

    @Composable
    fun destructiveOutlined() =
        ButtonStyle(
            colors =
                ButtonColors(
                    containerColor = SignAllTheme.colors.transparent,
                    contentColor = SignAllTheme.colors.error,
                    borderColor = SignAllTheme.colors.error,
                    disabledContainerColor = SignAllTheme.colors.transparent,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                    disabledBorderColor = SignAllTheme.colors.disabled,
                ),
            shape = outlinedShape,
            elevation = null,
            contentPadding = contentPadding,
        )

    @Composable
    fun destructiveGhost() =
        ButtonStyle(
            colors =
                ButtonColors(
                    containerColor = SignAllTheme.colors.transparent,
                    contentColor = SignAllTheme.colors.error,
                    borderColor = SignAllTheme.colors.transparent,
                    disabledContainerColor = SignAllTheme.colors.transparent,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                    disabledBorderColor = SignAllTheme.colors.transparent,
                ),
            shape = filledShape,
            elevation = null,
            contentPadding = contentPadding,
        )

    @Composable
    fun ghost() =
        ButtonStyle(
            colors =
                ButtonColors(
                    containerColor = SignAllTheme.colors.transparent,
                    contentColor = LocalContentColor.current,
                    borderColor = SignAllTheme.colors.transparent,
                    disabledContainerColor = SignAllTheme.colors.transparent,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                    disabledBorderColor = SignAllTheme.colors.transparent,
                ),
            shape = filledShape,
            elevation = null,
            contentPadding = contentPadding,
        )
}

@Immutable
internal data class ButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color? = null,
    val disabledContainerColor: Color,
    val disabledContentColor: Color,
    val disabledBorderColor: Color? = null,
) {
    @Composable
    internal fun containerColor(enabled: Boolean) =
        rememberUpdatedState(newValue = if (enabled) containerColor else disabledContainerColor)

    @Composable
    internal fun contentColor(enabled: Boolean) =
        rememberUpdatedState(newValue = if (enabled) contentColor else disabledContentColor)

    @Composable
    fun borderColor(enabled: Boolean) = rememberUpdatedState(newValue = if (enabled) borderColor else disabledBorderColor)
}

@Immutable
internal data class ButtonStyle(
    val colors: ButtonColors,
    val shape: Shape,
    val elevation: ButtonElevation? = null,
    val contentPadding: PaddingValues,
)

@Composable
@Preview
fun ButtonPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PrimaryButtonPreview()
        SecondaryButtonPreview()
        DestructiveButtonPreview()
    }
}

@Composable
@Preview
fun PrimaryButtonPreview() {
    SignAllTheme {
        Column(
            modifier =
                Modifier
                    .background(SignAllTheme.colors.background)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SignAllText(text = "Primary Buttons", style = SignAllTheme.typography.h2)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(text = "PrimaryFilled", appearance = ButtonAppearance(ButtonVariant.Primary), onClick = {})

                Button(
                    text = "Disabled",
                    appearance = ButtonAppearance(ButtonVariant.Primary),
                    enabled = false,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(text = "PrimaryOutlined", appearance = ButtonAppearance(ButtonVariant.PrimaryOutlined), onClick = {})

                Button(
                    text = "Disabled",
                    appearance = ButtonAppearance(ButtonVariant.PrimaryOutlined),
                    enabled = false,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(text = "PrimaryElevated", appearance = ButtonAppearance(ButtonVariant.PrimaryElevated), onClick = {})

                Button(
                    text = "Disabled",
                    appearance = ButtonAppearance(ButtonVariant.PrimaryElevated),
                    enabled = false,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(text = "PrimaryGhost", appearance = ButtonAppearance(ButtonVariant.PrimaryGhost), onClick = {})

                Button(
                    text = "Disabled",
                    appearance = ButtonAppearance(ButtonVariant.PrimaryGhost),
                    enabled = false,
                )
            }
        }
    }
}

@Composable
@Preview
fun SecondaryButtonPreview() {
    SignAllTheme {
        Column(
            modifier =
                Modifier
                    .background(SignAllTheme.colors.background)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SignAllText(text = "Secondary Buttons", style = SignAllTheme.typography.h2)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(text = "SecondaryFilled", appearance = ButtonAppearance(ButtonVariant.Secondary), onClick = {})

                Button(
                    text = "Disabled",
                    appearance = ButtonAppearance(ButtonVariant.Secondary),
                    enabled = false,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(text = "SecondaryOutlined", appearance = ButtonAppearance(ButtonVariant.SecondaryOutlined), onClick = {})

                Button(
                    text = "Disabled",
                    appearance = ButtonAppearance(ButtonVariant.SecondaryOutlined),
                    enabled = false,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(text = "SecondaryElevated", appearance = ButtonAppearance(ButtonVariant.SecondaryElevated), onClick = {})

                Button(
                    text = "Disabled",
                    appearance = ButtonAppearance(ButtonVariant.SecondaryElevated),
                    enabled = false,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(text = "SecondaryGhost", appearance = ButtonAppearance(ButtonVariant.SecondaryGhost), onClick = {})

                Button(
                    text = "Disabled",
                    appearance = ButtonAppearance(ButtonVariant.SecondaryGhost),
                    enabled = false,
                )
            }
        }
    }
}

@Composable
@Preview
fun DestructiveButtonPreview() {
    SignAllTheme {
        Column(
            modifier =
                Modifier
                    .background(SignAllTheme.colors.background)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SignAllText(text = "Destructive Buttons", style = SignAllTheme.typography.h2)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(text = "DestructiveFilled", appearance = ButtonAppearance(ButtonVariant.Destructive), onClick = {})

                Button(
                    text = "Disabled",
                    appearance = ButtonAppearance(ButtonVariant.Destructive),
                    enabled = false,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(text = "DestructiveOutlined", appearance = ButtonAppearance(ButtonVariant.DestructiveOutlined), onClick = {})

                Button(
                    text = "Disabled",
                    appearance = ButtonAppearance(ButtonVariant.DestructiveOutlined),
                    enabled = false,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(text = "DestructiveElevated", appearance = ButtonAppearance(ButtonVariant.DestructiveElevated), onClick = {})

                Button(
                    text = "Disabled",
                    appearance = ButtonAppearance(ButtonVariant.DestructiveElevated),
                    enabled = false,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(text = "DestructiveGhost", appearance = ButtonAppearance(ButtonVariant.DestructiveGhost), onClick = {})

                Button(
                    text = "Disabled",
                    appearance = ButtonAppearance(ButtonVariant.DestructiveGhost),
                    enabled = false,
                )
            }
        }
    }
}
