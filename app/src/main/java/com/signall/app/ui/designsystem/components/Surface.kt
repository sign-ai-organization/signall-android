package com.signall.app.ui.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.signall.app.ui.designsystem.SignAllTheme
import com.signall.app.ui.designsystem.LocalContentColor
import com.signall.app.ui.designsystem.contentColorFor
import com.signall.app.ui.designsystem.foundation.ripple

/**
 * Lo visual de un [Surface]: forma, relleno, color del contenido, sombra y borde.
 *
 * Los colores arrancan en [Color.Unspecified] a propósito. Un `data class` no puede
 * tener un default `@Composable` (`SignAllTheme.colors.surface`), así que el centinela
 * viaja hasta el composable y ahí se resuelve contra el tema. El resultado es el mismo
 * de antes: `contentColor` sigue saliendo de [contentColorFor] aplicado al relleno ya
 * resuelto, no al centinela.
 */
@Immutable
data class SurfaceStyle(
    val shape: Shape = RectangleShape,
    val color: Color = Color.Unspecified,
    val contentColor: Color = Color.Unspecified,
    val shadowElevation: Dp = 0.dp,
    val border: BorderStroke? = null,
)

@Composable
@NonRestartableComposable
fun Surface(
    modifier: Modifier = Modifier,
    style: SurfaceStyle = SurfaceStyle(),
    content: @Composable () -> Unit,
) {
    val color = style.resolvedColor(SignAllTheme.colors.surface)
    val contentColor = style.resolvedContentColor(color)
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
    ) {
        Box(
            modifier =
                modifier
                    .surface(
                        shape = style.shape,
                        backgroundColor = color,
                        border = style.border,
                        shadowElevation = style.shadowElevation,
                    )
                    .semantics(mergeDescendants = false) {
                        isTraversalGroup = true
                    }
                    .pointerInput(Unit) {},
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

@Composable
@NonRestartableComposable
fun Surface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: SurfaceStyle = SurfaceStyle(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val color = style.resolvedColor(SignAllTheme.colors.background)
    val contentColor = style.resolvedContentColor(color)
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
    ) {
        Box(
            modifier =
                modifier
                    .surface(
                        shape = style.shape,
                        backgroundColor = color,
                        border = style.border,
                        shadowElevation = style.shadowElevation,
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = contentColor),
                        enabled = enabled,
                        onClick = onClick,
                    ),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

@Composable
@NonRestartableComposable
fun Surface(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: SurfaceStyle = SurfaceStyle(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val color = style.resolvedColor(SignAllTheme.colors.background)
    val contentColor = style.resolvedContentColor(color)
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
    ) {
        Box(
            modifier =
                modifier
                    .surface(
                        shape = style.shape,
                        backgroundColor = color,
                        border = style.border,
                        shadowElevation = style.shadowElevation,
                    )
                    .selectable(
                        selected = selected,
                        interactionSource = interactionSource,
                        indication = ripple(),
                        enabled = enabled,
                        onClick = onClick,
                    ),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

@Composable
@NonRestartableComposable
fun Surface(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: SurfaceStyle = SurfaceStyle(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val color = style.resolvedColor(SignAllTheme.colors.background)
    val contentColor = style.resolvedContentColor(color)
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
    ) {
        Box(
            modifier =
                modifier
                    .surface(
                        shape = style.shape,
                        backgroundColor = color,
                        border = style.border,
                        shadowElevation = style.shadowElevation,
                    )
                    .toggleable(
                        value = checked,
                        interactionSource = interactionSource,
                        indication = ripple(),
                        enabled = enabled,
                        onValueChange = onCheckedChange,
                    ),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

@Composable
private fun SurfaceStyle.resolvedColor(fallback: Color): Color =
    if (color == Color.Unspecified) fallback else color

@Composable
private fun SurfaceStyle.resolvedContentColor(backgroundColor: Color): Color =
    if (contentColor == Color.Unspecified) contentColorFor(backgroundColor) else contentColor

@Composable
private fun Modifier.surface(
    shape: Shape,
    backgroundColor: Color,
    border: BorderStroke?,
    shadowElevation: Dp,
) = this
    .shadow(
        ambientColor = SignAllTheme.colors.elevation,
        spotColor = SignAllTheme.colors.elevation,
        elevation = shadowElevation,
        shape = shape,
        clip = false,
    )
    .then(if (border != null) Modifier.border(border, shape) else Modifier)
    .background(color = backgroundColor, shape = shape)
    .clip(shape)
