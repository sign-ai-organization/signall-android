package com.signall.app.ui.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.signall.app.ui.designsystem.SignAllTheme
import com.signall.app.ui.designsystem.LocalContentColor
import com.signall.app.ui.designsystem.contentColorFor
import com.signall.app.ui.designsystem.foundation.ButtonElevation
import androidx.compose.ui.tooling.preview.Preview

/**
 * Lo visual de un [IconButton]: qué [IconButtonVariant] elige los colores, más la
 * forma, el relleno interno y el `interactionSource` que antes viajaban sueltos en
 * la firma.
 *
 * El `interactionSource` arranca en `null` como centinela: no se puede usar
 * `remember` en el default de un `data class`, así que el composable lo resuelve.
 */
@Immutable
data class IconButtonAppearance(
    val variant: IconButtonVariant = IconButtonVariant.Primary,
    val shape: Shape = IconButtonDefaults.ButtonSquareShape,
    val contentPadding: PaddingValues = IconButtonDefaults.ButtonPadding,
    val interactionSource: MutableInteractionSource? = null,
)

@Composable
fun IconButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    appearance: IconButtonAppearance = IconButtonAppearance(),
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val style = IconButtonDefaults.styleFor(appearance.variant, appearance.shape)
    val interactionSource = appearance.interactionSource ?: remember { MutableInteractionSource() }

    IconButtonComponent(
        modifier = modifier,
        enabled = enabled,
        style = style,
        onClick = onClick,
        contentPadding = appearance.contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
private fun IconButtonComponent(
    modifier: Modifier,
    enabled: Boolean,
    style: IconButtonStyle,
    onClick: () -> Unit,
    contentPadding: PaddingValues,
    interactionSource: MutableInteractionSource,
    content: @Composable () -> Unit,
) {
    val containerColor = style.colors.containerColor(enabled).value
    val contentColor = style.colors.contentColor(enabled).value
    val borderColor = style.colors.borderColor(enabled).value
    val borderStroke = if (borderColor != null) BorderStroke(IconButtonDefaults.OutlineHeight, borderColor) else null

    val shadowElevation = style.elevation?.shadowElevation(enabled, interactionSource)?.value ?: 0.dp

    Surface(
        onClick = onClick,
        modifier =
            modifier.defaultMinSize(
                minWidth = IconButtonDefaults.ButtonSize,
                minHeight = IconButtonDefaults.ButtonSize,
            ).semantics { role = Role.Button },
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
        Box(
            modifier = Modifier.padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            // Add a loading indicator if needed
            content()
        }
    }
}

enum class IconButtonVariant {
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

internal object IconButtonDefaults {
    val ButtonSize = 44.dp
    val ButtonPadding = PaddingValues(4.dp)
    val ButtonSquareShape = RoundedCornerShape(12.dp)
    val ButtonCircleShape = RoundedCornerShape(percent = 50)
    val OutlineHeight = 1.dp

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
    fun styleFor(variant: IconButtonVariant, shape: Shape): IconButtonStyle {
        return when (variant) {
            IconButtonVariant.Primary -> primaryFilled(shape)
            IconButtonVariant.PrimaryOutlined -> primaryOutlined(shape)
            IconButtonVariant.PrimaryElevated -> primaryElevated(shape)
            IconButtonVariant.PrimaryGhost -> primaryGhost(shape)
            IconButtonVariant.Secondary -> secondaryFilled(shape)
            IconButtonVariant.SecondaryOutlined -> secondaryOutlined(shape)
            IconButtonVariant.SecondaryElevated -> secondaryElevated(shape)
            IconButtonVariant.SecondaryGhost -> secondaryGhost(shape)
            IconButtonVariant.Destructive -> destructiveFilled(shape)
            IconButtonVariant.DestructiveOutlined -> destructiveOutlined(shape)
            IconButtonVariant.DestructiveElevated -> destructiveElevated(shape)
            IconButtonVariant.DestructiveGhost -> destructiveGhost(shape)
            IconButtonVariant.Ghost -> ghost(shape)
        }
    }

    @Composable
    fun primaryFilled(shape: Shape) =
        IconButtonStyle(
            colors =
                IconButtonColors(
                    containerColor = SignAllTheme.colors.primary,
                    contentColor = SignAllTheme.colors.onPrimary,
                    disabledContainerColor = SignAllTheme.colors.disabled,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                ),
            shape = shape,
            elevation = null,
        )

    @Composable
    fun primaryOutlined(shape: Shape) =
        IconButtonStyle(
            colors =
                IconButtonColors(
                    containerColor = SignAllTheme.colors.transparent,
                    contentColor = SignAllTheme.colors.primary,
                    borderColor = SignAllTheme.colors.primary,
                    disabledContainerColor = SignAllTheme.colors.transparent,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                    disabledBorderColor = SignAllTheme.colors.disabled,
                ),
            shape = shape,
            elevation = null,
        )

    @Composable
    fun primaryElevated(shape: Shape) =
        IconButtonStyle(
            colors =
                IconButtonColors(
                    containerColor = SignAllTheme.colors.primary,
                    contentColor = SignAllTheme.colors.onPrimary,
                    disabledContainerColor = SignAllTheme.colors.disabled,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                ),
            shape = shape,
            elevation = buttonElevation(),
        )

    @Composable
    fun primaryGhost(shape: Shape) =
        IconButtonStyle(
            colors =
                IconButtonColors(
                    containerColor = SignAllTheme.colors.transparent,
                    contentColor = SignAllTheme.colors.primary,
                    borderColor = SignAllTheme.colors.transparent,
                    disabledContainerColor = SignAllTheme.colors.transparent,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                    disabledBorderColor = SignAllTheme.colors.transparent,
                ),
            shape = shape,
            elevation = null,
        )

    @Composable
    fun secondaryFilled(shape: Shape) =
        IconButtonStyle(
            colors =
                IconButtonColors(
                    containerColor = SignAllTheme.colors.secondary,
                    contentColor = SignAllTheme.colors.onSecondary,
                    disabledContainerColor = SignAllTheme.colors.disabled,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                ),
            shape = shape,
            elevation = null,
        )

    @Composable
    fun secondaryOutlined(shape: Shape) =
        IconButtonStyle(
            colors =
                IconButtonColors(
                    containerColor = SignAllTheme.colors.transparent,
                    contentColor = SignAllTheme.colors.secondary,
                    borderColor = SignAllTheme.colors.secondary,
                    disabledContainerColor = SignAllTheme.colors.transparent,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                    disabledBorderColor = SignAllTheme.colors.disabled,
                ),
            shape = shape,
            elevation = null,
        )

    @Composable
    fun secondaryElevated(shape: Shape) =
        IconButtonStyle(
            colors =
                IconButtonColors(
                    containerColor = SignAllTheme.colors.secondary,
                    contentColor = SignAllTheme.colors.onSecondary,
                    disabledContainerColor = SignAllTheme.colors.disabled,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                ),
            shape = shape,
            elevation = buttonElevation(),
        )

    @Composable
    fun secondaryGhost(shape: Shape) =
        IconButtonStyle(
            colors =
                IconButtonColors(
                    containerColor = SignAllTheme.colors.transparent,
                    contentColor = SignAllTheme.colors.secondary,
                    borderColor = SignAllTheme.colors.transparent,
                    disabledContainerColor = SignAllTheme.colors.transparent,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                    disabledBorderColor = SignAllTheme.colors.transparent,
                ),
            shape = shape,
            elevation = null,
        )

    @Composable
    fun destructiveFilled(shape: Shape) =
        IconButtonStyle(
            colors =
                IconButtonColors(
                    containerColor = SignAllTheme.colors.error,
                    contentColor = SignAllTheme.colors.onError,
                    disabledContainerColor = SignAllTheme.colors.disabled,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                ),
            shape = shape,
            elevation = null,
        )

    @Composable
    fun destructiveOutlined(shape: Shape) =
        IconButtonStyle(
            colors =
                IconButtonColors(
                    containerColor = SignAllTheme.colors.transparent,
                    contentColor = SignAllTheme.colors.error,
                    borderColor = SignAllTheme.colors.error,
                    disabledContainerColor = SignAllTheme.colors.transparent,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                    disabledBorderColor = SignAllTheme.colors.disabled,
                ),
            shape = shape,
            elevation = null,
        )

    @Composable
    fun destructiveElevated(shape: Shape) =
        IconButtonStyle(
            colors =
                IconButtonColors(
                    containerColor = SignAllTheme.colors.error,
                    contentColor = SignAllTheme.colors.onError,
                    disabledContainerColor = SignAllTheme.colors.disabled,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                ),
            shape = shape,
            elevation = buttonElevation(),
        )

    @Composable
    fun destructiveGhost(shape: Shape) =
        IconButtonStyle(
            colors =
                IconButtonColors(
                    containerColor = SignAllTheme.colors.transparent,
                    contentColor = SignAllTheme.colors.error,
                    borderColor = SignAllTheme.colors.transparent,
                    disabledContainerColor = SignAllTheme.colors.transparent,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                    disabledBorderColor = SignAllTheme.colors.transparent,
                ),
            shape = shape,
            elevation = null,
        )

    @Composable
    fun ghost(shape: Shape) =
        IconButtonStyle(
            colors =
                IconButtonColors(
                    containerColor = SignAllTheme.colors.transparent,
                    contentColor = LocalContentColor.current,
                    disabledContainerColor = SignAllTheme.colors.transparent,
                    disabledContentColor = SignAllTheme.colors.onDisabled,
                ),
            shape = shape,
            elevation = null,
        )
}

@Immutable
data class IconButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color? = null,
    val disabledContainerColor: Color,
    val disabledContentColor: Color,
    val disabledBorderColor: Color? = null,
) {
    @Composable
    fun containerColor(enabled: Boolean) = rememberUpdatedState(if (enabled) containerColor else disabledContainerColor)

    @Composable
    fun contentColor(enabled: Boolean) = rememberUpdatedState(if (enabled) contentColor else disabledContentColor)

    @Composable
    fun borderColor(enabled: Boolean) = rememberUpdatedState(if (enabled) borderColor else disabledBorderColor)
}

@Immutable
data class IconButtonStyle(
    val colors: IconButtonColors,
    val shape: Shape,
    val elevation: ButtonElevation? = null,
)

@Composable
@Preview
fun PrimaryIconButtonPreview() {
    SignAllTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicText(text = "Primary Icon Buttons", style = SignAllTheme.typography.h2)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconButton(appearance = IconButtonAppearance(IconButtonVariant.Primary)) {
                    DummyIconForIconButtonPreview()
                }
                IconButton(appearance = IconButtonAppearance(IconButtonVariant.PrimaryOutlined)) {
                    DummyIconForIconButtonPreview()
                }
                IconButton(appearance = IconButtonAppearance(IconButtonVariant.PrimaryElevated)) {
                    DummyIconForIconButtonPreview()
                }
                IconButton(appearance = IconButtonAppearance(IconButtonVariant.PrimaryGhost)) {
                    DummyIconForIconButtonPreview()
                }
            }
        }
    }
}

@Composable
@Preview
fun SecondaryIconButtonPreview() {
    SignAllTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicText(text = "Secondary Icon Buttons", style = SignAllTheme.typography.h2)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconButton(appearance = IconButtonAppearance(IconButtonVariant.Secondary)) {
                    DummyIconForIconButtonPreview()
                }
                IconButton(appearance = IconButtonAppearance(IconButtonVariant.SecondaryOutlined)) {
                    DummyIconForIconButtonPreview()
                }
                IconButton(appearance = IconButtonAppearance(IconButtonVariant.SecondaryElevated)) {
                    DummyIconForIconButtonPreview()
                }
                IconButton(appearance = IconButtonAppearance(IconButtonVariant.SecondaryGhost)) {
                    DummyIconForIconButtonPreview()
                }
            }
        }
    }
}

@Composable
@Preview
fun DestructiveIconButtonPreview() {
    SignAllTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicText(text = "Destructive Icon Buttons", style = SignAllTheme.typography.h2)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconButton(appearance = IconButtonAppearance(IconButtonVariant.Destructive)) {
                    DummyIconForIconButtonPreview()
                }
                IconButton(appearance = IconButtonAppearance(IconButtonVariant.DestructiveOutlined)) {
                    DummyIconForIconButtonPreview()
                }
                IconButton(appearance = IconButtonAppearance(IconButtonVariant.DestructiveElevated)) {
                    DummyIconForIconButtonPreview()
                }
                IconButton(appearance = IconButtonAppearance(IconButtonVariant.DestructiveGhost)) {
                    DummyIconForIconButtonPreview()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Preview
fun GhostIconButtonPreview() {
    SignAllTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicText(text = "Ghost Icon Buttons", style = SignAllTheme.typography.h2)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8)).background(SignAllTheme.colors.background),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides contentColorFor(color = SignAllTheme.colors.background)) {
                        IconButton(appearance = IconButtonAppearance(IconButtonVariant.Ghost)) {
                            DummyIconForIconButtonPreview()
                        }
                    }
                }

                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8)).background(SignAllTheme.colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides contentColorFor(color = SignAllTheme.colors.primary)) {
                        IconButton(appearance = IconButtonAppearance(IconButtonVariant.Ghost)) {
                            DummyIconForIconButtonPreview()
                        }
                    }
                }

                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8)).background(SignAllTheme.colors.secondary),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides contentColorFor(color = SignAllTheme.colors.secondary)) {
                        IconButton(appearance = IconButtonAppearance(IconButtonVariant.Ghost)) {
                            DummyIconForIconButtonPreview()
                        }
                    }
                }

                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8)).background(SignAllTheme.colors.tertiary),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides contentColorFor(color = SignAllTheme.colors.tertiary)) {
                        IconButton(appearance = IconButtonAppearance(IconButtonVariant.Ghost)) {
                            DummyIconForIconButtonPreview()
                        }
                    }
                }

                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8)).background(SignAllTheme.colors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides contentColorFor(color = SignAllTheme.colors.surface)) {
                        IconButton(appearance = IconButtonAppearance(IconButtonVariant.Ghost)) {
                            DummyIconForIconButtonPreview()
                        }
                    }
                }

                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8)).background(SignAllTheme.colors.error),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides contentColorFor(color = SignAllTheme.colors.error)) {
                        IconButton(appearance = IconButtonAppearance(IconButtonVariant.Ghost)) {
                            DummyIconForIconButtonPreview()
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Preview
fun IconButtonShapesPreview() {
    SignAllTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicText(text = "Square Shape", style = SignAllTheme.typography.h2)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconButton(
                    appearance = IconButtonAppearance(variant = IconButtonVariant.Primary, shape = IconButtonDefaults.ButtonSquareShape),
                ) {
                    DummyIconForIconButtonPreview()
                }
                IconButton(
                    appearance = IconButtonAppearance(variant = IconButtonVariant.PrimaryOutlined, shape = IconButtonDefaults.ButtonSquareShape),
                ) {
                    DummyIconForIconButtonPreview()
                }
            }

            BasicText(text = "Circle Shape", style = SignAllTheme.typography.h2)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconButton(
                    appearance = IconButtonAppearance(variant = IconButtonVariant.Primary, shape = IconButtonDefaults.ButtonCircleShape),
                ) {
                    DummyIconForIconButtonPreview()
                }
                IconButton(
                    appearance = IconButtonAppearance(variant = IconButtonVariant.PrimaryOutlined, shape = IconButtonDefaults.ButtonCircleShape),
                ) {
                    DummyIconForIconButtonPreview()
                }
            }
        }
    }
}

@Composable
@Preview
private fun DummyIconForIconButtonPreview() {
    Canvas(modifier = Modifier.size(16.dp)) {
        val center = size / 2f
        val radius = size.minDimension * 0.4f
        val strokeWidth = 4f
        val cap = StrokeCap.Round

        drawLine(
            color = Color.Black,
            start = Offset(center.width - radius, center.height),
            end = Offset(center.width + radius, center.height),
            strokeWidth = strokeWidth,
            cap = cap,
        )

        drawLine(
            color = Color.Black,
            start = Offset(center.width, center.height - radius),
            end = Offset(center.width, center.height + radius),
            strokeWidth = strokeWidth,
            cap = cap,
        )

        val diagonalRadius = radius * 0.75f
        drawLine(
            color = Color.Black,
            start =
                Offset(
                    center.width - diagonalRadius,
                    center.height - diagonalRadius,
                ),
            end =
                Offset(
                    center.width + diagonalRadius,
                    center.height + diagonalRadius,
                ),
            strokeWidth = strokeWidth,
            cap = cap,
        )

        drawLine(
            color = Color.Black,
            start =
                Offset(
                    center.width - diagonalRadius,
                    center.height + diagonalRadius,
                ),
            end =
                Offset(
                    center.width + diagonalRadius,
                    center.height - diagonalRadius,
                ),
            strokeWidth = strokeWidth,
            cap = cap,
        )
    }
}
