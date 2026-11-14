package com.signall.app.ui.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.signall.app.ui.designsystem.SignAllTheme
import com.signall.app.ui.designsystem.LocalContentColor
import com.signall.app.ui.designsystem.components.SwitchDefaults.RippleRadius
import com.signall.app.ui.designsystem.components.SwitchDefaults.SwitchHeight
import com.signall.app.ui.designsystem.components.SwitchDefaults.SwitchWidth
import com.signall.app.ui.designsystem.components.SwitchDefaults.ThumbSize
import com.signall.app.ui.designsystem.components.SwitchDefaults.ThumbSizeStateOffset
import com.signall.app.ui.designsystem.components.SwitchDefaults.TrackBorderWidth
import com.signall.app.ui.designsystem.components.SwitchDefaults.TrackShape
import com.signall.app.ui.designsystem.components.SwitchDefaults.UncheckedThumbSize
import com.signall.app.ui.designsystem.foundation.ripple
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val scope = rememberCoroutineScope()
    val pressed by interactionSource.collectIsPressedAsState()

    val animationState =
        remember {
            SwitchAnimationState(checked, pressed)
        }

    LaunchedEffect(checked, pressed) {
        animationState.animateTo(checked, pressed, scope)
    }

    val toggleableModifier =
        if (onCheckedChange != null) {
            Modifier.toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
            )
        } else {
            Modifier
        }

    SwitchComponent(
        modifier = modifier.then(toggleableModifier),
        state =
            SwitchThumbState(
                checked = checked,
                position = animationState.thumbPosition.value,
                sizeOffset = animationState.thumbSizeOffset.value,
            ),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        thumbContent = thumbContent,
    )
}

/**
 * Lo que anima el thumb en un fotograma: si está prendido y dónde quedó la
 * animación. Va junto porque siempre viaja junto.
 */
@Immutable
private data class SwitchThumbState(
    val checked: Boolean,
    val position: Float,
    val sizeOffset: Float,
)

@Composable
private fun SwitchComponent(
    modifier: Modifier,
    state: SwitchThumbState,
    enabled: Boolean,
    colors: SwitchColors,
    interactionSource: InteractionSource,
    thumbContent: (@Composable () -> Unit)?,
) {
    val borderColor = colors.borderColor(enabled = enabled, checked = state.checked)

    Box(
        modifier =
            modifier
                .size(SwitchWidth, SwitchHeight)
                .background(
                    color = colors.trackColor(enabled, state.checked),
                    shape = TrackShape,
                )
                .border(
                    width = TrackBorderWidth,
                    color = borderColor,
                    shape = TrackShape,
                ),
    ) {
        val checkedThumbSize = UncheckedThumbSize + ThumbSizeStateOffset * state.position
        val uncheckedThumbSize =
            UncheckedThumbSize + ThumbSizeStateOffset * if (state.position == 0f) state.sizeOffset else state.position

        val thumbSize = if (state.checked) checkedThumbSize else uncheckedThumbSize
        val verticalPadding = (SwitchHeight - ThumbSize) / 2

        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .size(thumbSize)
                    .offset {
                        val trackWidth = SwitchWidth.toPx()
                        val currentThumbSize = thumbSize.toPx()
                        val maxThumbSize = ThumbSize.toPx()
                        val padding = verticalPadding.toPx()

                        val totalMovableDistance = trackWidth - maxThumbSize - (padding * 2)
                        val sizeDifference = (maxThumbSize - currentThumbSize) / 2

                        IntOffset(
                            x = (padding + sizeDifference + (totalMovableDistance * state.position)).roundToInt(),
                            y = 0,
                        )
                    }
                    .drawBehind {
                        drawCircle(
                            color = colors.thumbColor(enabled, state.checked),
                        )
                    }
                    .indication(
                        interactionSource = interactionSource,
                        indication =
                            ripple(
                                bounded = false,
                                radius = RippleRadius,
                            ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbContent != null) {
                CompositionLocalProvider(
                    LocalContentColor provides colors.iconColor(enabled, state.checked),
                ) {
                    thumbContent()
                }
            }
        }
    }
}

/**
 * Los dieciséis colores del switch, en un solo objeto.
 *
 * Cada campo arranca en [Color.Unspecified] a propósito: un `data class` no puede
 * tener defaults `@Composable` (`SignAllTheme.colors.*`), así que el centinela viaja
 * hasta [SwitchDefaults.colors] y ahí se resuelve contra el tema. Los defaults
 * siguen siendo exactamente los de antes.
 */
@Immutable
data class SwitchAppearance(
    val checkedThumbColor: Color = Color.Unspecified,
    val checkedTrackColor: Color = Color.Unspecified,
    val checkedBorderColor: Color = Color.Unspecified,
    val checkedIconColor: Color = Color.Unspecified,
    val uncheckedThumbColor: Color = Color.Unspecified,
    val uncheckedTrackColor: Color = Color.Unspecified,
    val uncheckedBorderColor: Color = Color.Unspecified,
    val uncheckedIconColor: Color = Color.Unspecified,
    val disabledCheckedThumbColor: Color = Color.Unspecified,
    val disabledCheckedTrackColor: Color = Color.Unspecified,
    val disabledCheckedBorderColor: Color = Color.Unspecified,
    val disabledCheckedIconColor: Color = Color.Unspecified,
    val disabledUncheckedThumbColor: Color = Color.Unspecified,
    val disabledUncheckedTrackColor: Color = Color.Unspecified,
    val disabledUncheckedBorderColor: Color = Color.Unspecified,
    val disabledUncheckedIconColor: Color = Color.Unspecified,
)

object SwitchDefaults {
    // Prototype `.switch` (PrototipoFrontend/app/globals.css): 43x25, padding 3,
    // 19px thumb, no border, radius 20 (clamps to a pill).
    val ThumbSize = 19.dp
    val UncheckedThumbSize = 19.dp
    val ThumbSizeStateOffset = ThumbSize - UncheckedThumbSize
    val SwitchWidth = 43.dp
    val SwitchHeight = 25.dp
    val TrackBorderWidth = 0.dp
    val TrackShape = RoundedCornerShape(20.dp)
    val RippleRadius = 20.dp

    @Composable
    fun colors(appearance: SwitchAppearance = SwitchAppearance()): SwitchColors {
        val theme = SignAllTheme.colors
        return SwitchColors(
            checkedThumbColor = appearance.checkedThumbColor.orDefault(theme.background),
            checkedTrackColor = appearance.checkedTrackColor.orDefault(theme.strong),
            checkedBorderColor = appearance.checkedBorderColor.orDefault(theme.strong),
            checkedIconColor = appearance.checkedIconColor.orDefault(theme.strong),
            uncheckedThumbColor = appearance.uncheckedThumbColor.orDefault(theme.muted),
            uncheckedTrackColor = appearance.uncheckedTrackColor.orDefault(theme.soft),
            uncheckedBorderColor = appearance.uncheckedBorderColor.orDefault(theme.soft),
            uncheckedIconColor = appearance.uncheckedIconColor.orDefault(theme.muted),
            disabledCheckedThumbColor = appearance.disabledCheckedThumbColor.orDefault(theme.onDisabled),
            disabledCheckedTrackColor = appearance.disabledCheckedTrackColor.orDefault(theme.disabled),
            disabledCheckedBorderColor = appearance.disabledCheckedBorderColor.orDefault(theme.disabled),
            disabledCheckedIconColor = appearance.disabledCheckedIconColor.orDefault(theme.disabled),
            disabledUncheckedThumbColor = appearance.disabledUncheckedThumbColor.orDefault(theme.disabled),
            disabledUncheckedTrackColor = appearance.disabledUncheckedTrackColor.orDefault(theme.transparent),
            disabledUncheckedBorderColor = appearance.disabledUncheckedBorderColor.orDefault(theme.disabled),
            disabledUncheckedIconColor = appearance.disabledUncheckedIconColor.orDefault(theme.onDisabled),
        )
    }
}

private fun Color.orDefault(default: Color): Color = if (this == Color.Unspecified) default else this

@Stable
class SwitchColors(
    private val checkedThumbColor: Color,
    private val checkedTrackColor: Color,
    private val checkedBorderColor: Color,
    private val checkedIconColor: Color,
    private val uncheckedThumbColor: Color,
    private val uncheckedTrackColor: Color,
    private val uncheckedBorderColor: Color,
    private val uncheckedIconColor: Color,
    private val disabledCheckedThumbColor: Color,
    private val disabledCheckedTrackColor: Color,
    private val disabledCheckedBorderColor: Color,
    private val disabledCheckedIconColor: Color,
    private val disabledUncheckedThumbColor: Color,
    private val disabledUncheckedTrackColor: Color,
    private val disabledUncheckedBorderColor: Color,
    private val disabledUncheckedIconColor: Color,
) {
    @Stable
    internal fun thumbColor(enabled: Boolean, checked: Boolean): Color =
        when {
            enabled && checked -> checkedThumbColor
            enabled && !checked -> uncheckedThumbColor
            !enabled && checked -> disabledCheckedThumbColor
            else -> disabledUncheckedThumbColor
        }

    @Stable
    internal fun trackColor(enabled: Boolean, checked: Boolean): Color =
        when {
            enabled && checked -> checkedTrackColor
            enabled && !checked -> uncheckedTrackColor
            !enabled && checked -> disabledCheckedTrackColor
            else -> disabledUncheckedTrackColor
        }

    @Stable
    internal fun borderColor(enabled: Boolean, checked: Boolean): Color =
        when {
            enabled && checked -> checkedBorderColor
            enabled && !checked -> uncheckedBorderColor
            !enabled && checked -> disabledCheckedBorderColor
            else -> disabledUncheckedBorderColor
        }

    @Stable
    internal fun iconColor(enabled: Boolean, checked: Boolean): Color =
        when {
            enabled && checked -> checkedIconColor
            enabled && !checked -> uncheckedIconColor
            !enabled && checked -> disabledCheckedIconColor
            else -> disabledUncheckedIconColor
        }
}

@Stable
private class SwitchAnimationState(
    initialChecked: Boolean,
    initialPressed: Boolean,
) {
    var checked by mutableStateOf(initialChecked)
    var pressed by mutableStateOf(initialPressed)

    val thumbPosition = Animatable(if (checked) 1f else 0f)
    val thumbSizeOffset = Animatable(0f)

    val animationSpec =
        tween<Float>(
            durationMillis = 200,
            easing = FastOutSlowInEasing,
        )

    suspend fun animateTo(
        targetChecked: Boolean,
        targetPressed: Boolean,
        scope: CoroutineScope,
    ) {
        checked = targetChecked
        pressed = targetPressed

        scope.launch {
            thumbPosition.animateTo(
                targetValue = if (targetChecked) 1f else 0f,
                animationSpec = animationSpec,
            )
        }
        scope.launch {
            thumbSizeOffset.animateTo(
                targetValue = if (targetPressed) 1f else 0f,
                animationSpec = animationSpec,
            )
        }
    }
}

@Preview
@Composable
private fun SwitchPreview() {
    SignAllTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            val value =
                remember {
                    mutableStateOf(false)
                }

            Spacer(modifier = Modifier.size(16.dp))
            Switch(
                checked = value.value,
                onCheckedChange = {
                    value.value = it
                },
            )
            Spacer(modifier = Modifier.size(16.dp))
            Switch(
                checked = value.value,
                onCheckedChange = {
                    value.value = it
                },
            )
            Spacer(modifier = Modifier.size(16.dp))

            Switch(
                checked = true,
                onCheckedChange = {
                    value.value = it
                },
            )
            Spacer(modifier = Modifier.size(16.dp))

            Switch(
                checked = false,
                onCheckedChange = {
                    value.value = it
                },
            )
            Spacer(modifier = Modifier.size(16.dp))

            Switch(
                checked = true,
                enabled = false,
                onCheckedChange = {
                    value.value = it
                },
            )
            Spacer(modifier = Modifier.size(16.dp))

            Switch(
                checked = false,
                enabled = false,
                onCheckedChange = {
                    value.value = it
                },
            )
            Spacer(modifier = Modifier.size(16.dp))
        }
    }
}
