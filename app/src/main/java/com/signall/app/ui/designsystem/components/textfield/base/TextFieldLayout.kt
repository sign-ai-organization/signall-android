package com.signall.app.ui.designsystem.components.textfield.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.fastFirstOrNull
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Las ranuras que dibuja [TextFieldLayout]. La del texto y la del contenedor son
 * obligatorias; el resto se compone solo si no es `null`.
 */
internal class TextFieldLayoutSlots(
    val textField: @Composable () -> Unit,
    val container: @Composable () -> Unit,
    val label: @Composable (() -> Unit)? = null,
    val placeholder: @Composable ((Modifier) -> Unit)? = null,
    val leading: @Composable (() -> Unit)? = null,
    val trailing: @Composable (() -> Unit)? = null,
    val prefix: @Composable (() -> Unit)? = null,
    val suffix: @Composable (() -> Unit)? = null,
    val supporting: @Composable (() -> Unit)? = null,
)

@Composable
internal fun TextFieldLayout(
    slots: TextFieldLayoutSlots,
    paddings: TextFieldPaddings,
    modifier: Modifier = Modifier,
) {
    val contentPadding = paddings.content
    val measurePolicy =
        remember(contentPadding) {
            TextFieldMeasurePolicy(contentPadding)
        }

    Layout(
        modifier = modifier,
        content = { TextFieldContent(slots, paddings) },
        measurePolicy = measurePolicy,
    )
}

/**
 * Compone las ranuras en el orden en que el [TextFieldMeasurePolicy] las espera. El
 * orden importa: es el mismo del layout original.
 */
@Composable
private fun TextFieldContent(
    slots: TextFieldLayoutSlots,
    paddings: TextFieldPaddings,
) {
    val horizontalPadding = contentHorizontalPadding(slots, paddings, LocalLayoutDirection.current)

    slots.label?.let { LabelSlot(paddings.label, it) }
    slots.container()
    IconSlots(slots, paddings)
    AffixSlots(slots, horizontalPadding)
    TextSlots(slots, horizontalPadding)
    slots.supporting?.let { SupportingTextSlot(paddings.supportingText, it) }
}

@Composable
private fun LabelSlot(
    paddingValues: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .layoutId(LabelId)
            .padding(paddingValues)
            .wrapContentHeight(),
    ) { content() }
}

@Composable
private fun IconSlots(
    slots: TextFieldLayoutSlots,
    paddings: TextFieldPaddings,
) {
    slots.leading?.let { IconSlot(LeadingId, paddings.leadingIcon, it) }
    slots.trailing?.let { IconSlot(TrailingId, paddings.trailingIcon, it) }
}

@Composable
private fun IconSlot(
    slotId: String,
    paddingValues: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .layoutId(slotId)
                .padding(paddingValues)
                .then(IconDefaultSizeModifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun AffixSlots(
    slots: TextFieldLayoutSlots,
    horizontalPadding: TextFieldSidePadding,
) {
    slots.prefix?.let { AffixSlot(PrefixId, horizontalPadding.start, PrefixSuffixTextPadding, it) }
    slots.suffix?.let { AffixSlot(SuffixId, PrefixSuffixTextPadding, horizontalPadding.end, it) }
}

@Composable
private fun AffixSlot(
    slotId: String,
    start: Dp,
    end: Dp,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .layoutId(slotId)
            .heightIn(min = MinTextLineHeight)
            .wrapContentHeight()
            .padding(start = start, end = end),
    ) { content() }
}

@Composable
private fun TextSlots(
    slots: TextFieldLayoutSlots,
    horizontalPadding: TextFieldSidePadding,
) {
    val textModifier = textPadding(slots, horizontalPadding)

    slots.placeholder?.let {
        it(
            Modifier
                .layoutId(PlaceholderId)
                .then(textModifier),
        )
    }
    Box(
        modifier =
            Modifier
                .layoutId(TextFieldId)
                .then(textModifier),
        propagateMinConstraints = true,
    ) {
        slots.textField()
    }
}

@Composable
private fun SupportingTextSlot(
    paddingValues: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .layoutId(SupportingId)
            .heightIn(min = MinSupportingTextLineHeight)
            .wrapContentHeight()
            .padding(paddingValues),
    ) { content() }
}

/** El relleno horizontal del texto, ya descontado el lugar de los iconos. */
private class TextFieldSidePadding(
    val start: Dp,
    val end: Dp,
)

private fun contentHorizontalPadding(
    slots: TextFieldLayoutSlots,
    paddings: TextFieldPaddings,
    layoutDirection: LayoutDirection,
): TextFieldSidePadding {
    val start = paddings.content.calculateStartPadding(layoutDirection)
    val end = paddings.content.calculateEndPadding(layoutDirection)
    val startLeadingIconPadding = paddings.leadingIcon.calculateStartPadding(layoutDirection)
    val endTrailingIconPadding = paddings.trailingIcon.calculateEndPadding(layoutDirection)

    return TextFieldSidePadding(
        start =
            if (slots.leading != null) {
                (start - startLeadingIconPadding).coerceAtLeast(0.dp)
            } else {
                start
            },
        end =
            if (slots.trailing != null) {
                (end - endTrailingIconPadding).coerceAtLeast(0.dp)
            } else {
                end
            },
    )
}

private fun textPadding(
    slots: TextFieldLayoutSlots,
    horizontalPadding: TextFieldSidePadding,
): Modifier = Modifier
    .heightIn(min = MinTextLineHeight)
    .wrapContentHeight()
    .padding(
        start = if (slots.prefix == null) horizontalPadding.start else 0.dp,
        end = if (slots.suffix == null) horizontalPadding.end else 0.dp,
    )

private class TextFieldMeasurePolicy(
    private val paddingValues: PaddingValues,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val topPaddingValue = paddingValues.calculateTopPadding().roundToPx()
        val bottomPaddingValue = paddingValues.calculateBottomPadding().roundToPx()

        var occupiedSpaceHorizontally = 0
        var occupiedSpaceVertically = 0

        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        // measure leading icon
        val leadingPlaceable = measurables.fastFirstOrNull { it.layoutId == LeadingId }?.measure(looseConstraints)
        occupiedSpaceHorizontally += widthOrZero(leadingPlaceable)
        occupiedSpaceVertically = max(occupiedSpaceVertically, heightOrZero(leadingPlaceable))

        // measure trailing icon
        val trailingPlaceable =
            measurables.fastFirstOrNull { it.layoutId == TrailingId }
                ?.measure(looseConstraints.offset(horizontal = -occupiedSpaceHorizontally))
        occupiedSpaceHorizontally += widthOrZero(trailingPlaceable)
        occupiedSpaceVertically = max(occupiedSpaceVertically, heightOrZero(trailingPlaceable))

        // measure prefix
        val prefixPlaceable =
            measurables.fastFirstOrNull { it.layoutId == PrefixId }
                ?.measure(looseConstraints.offset(horizontal = -occupiedSpaceHorizontally))
        occupiedSpaceHorizontally += widthOrZero(prefixPlaceable)
        occupiedSpaceVertically = max(occupiedSpaceVertically, heightOrZero(prefixPlaceable))

        // measure suffix
        val suffixPlaceable =
            measurables.fastFirstOrNull { it.layoutId == SuffixId }
                ?.measure(looseConstraints.offset(horizontal = -occupiedSpaceHorizontally))
        occupiedSpaceHorizontally += widthOrZero(suffixPlaceable)
        occupiedSpaceVertically = max(occupiedSpaceVertically, heightOrZero(suffixPlaceable))

        // measure label
        val labelConstraints =
            looseConstraints.offset(
                vertical = -bottomPaddingValue,
                horizontal = -occupiedSpaceHorizontally,
            )
        val labelPlaceable = measurables.fastFirstOrNull { it.layoutId == LabelId }?.measure(labelConstraints)

        val supportingMeasurable = measurables.fastFirstOrNull { it.layoutId == SupportingId }
        val supportingIntrinsicHeight = supportingMeasurable?.minIntrinsicHeight(constraints.minWidth) ?: 0

        val effectiveTopOffset = topPaddingValue + heightOrZero(labelPlaceable)
        val textFieldConstraints =
            constraints.copy(minHeight = 0).offset(
                vertical = -effectiveTopOffset - bottomPaddingValue - supportingIntrinsicHeight,
                horizontal = -occupiedSpaceHorizontally,
            )

        val textFieldPlaceable = measurables.fastFirst { it.layoutId == TextFieldId }.measure(textFieldConstraints)

        // measure placeholder
        val placeholderConstraints = textFieldConstraints.copy(minWidth = 0)
        val placeholderPlaceable = measurables.fastFirstOrNull { it.layoutId == PlaceholderId }?.measure(placeholderConstraints)

        occupiedSpaceVertically =
            max(
                occupiedSpaceVertically,
                max(heightOrZero(textFieldPlaceable), heightOrZero(placeholderPlaceable)) + effectiveTopOffset + bottomPaddingValue,
            )
        val width =
            calculateWidth(
                widths =
                    TextFieldWidths(
                        leading = widthOrZero(leadingPlaceable),
                        trailing = widthOrZero(trailingPlaceable),
                        prefix = widthOrZero(prefixPlaceable),
                        suffix = widthOrZero(suffixPlaceable),
                        textField = textFieldPlaceable.width,
                        label = widthOrZero(labelPlaceable),
                        placeholder = widthOrZero(placeholderPlaceable),
                    ),
                constraints = constraints,
            )

        // measure supporting text
        val supportingConstraints =
            looseConstraints.offset(
                vertical = -occupiedSpaceVertically,
            ).copy(minHeight = 0, maxWidth = width)
        val supportingPlaceable = supportingMeasurable?.measure(supportingConstraints)
        val labelHeight = heightOrZero(labelPlaceable)
        val supportingHeight = heightOrZero(supportingPlaceable)

        val totalHeight =
            calculateHeight(
                heights =
                    TextFieldHeights(
                        textField = textFieldPlaceable.height,
                        label = labelHeight,
                        leading = heightOrZero(leadingPlaceable),
                        trailing = heightOrZero(trailingPlaceable),
                        prefix = heightOrZero(prefixPlaceable),
                        suffix = heightOrZero(suffixPlaceable),
                        placeholder = heightOrZero(placeholderPlaceable),
                        supporting = supportingHeight,
                    ),
                constraints = constraints,
                density = density,
                paddingValues = paddingValues,
            )

        val containerHeight = totalHeight - supportingHeight - labelHeight

        val containerPlaceable =
            measurables.fastFirst { it.layoutId == ContainerId }.measure(
                Constraints(
                    minWidth = if (width != Constraints.Infinity) width else 0,
                    maxWidth = width,
                    minHeight = if (containerHeight != Constraints.Infinity) containerHeight else 0,
                    maxHeight = containerHeight,
                ),
            )

        // When the call site forces a height larger than the natural content
        // height, the input/placeholder must be vertically centered in the
        // *actual* container instead of pinned to the top padding. Without this
        // the text sits 10dp from the top of a 50dp box, leaving a lopsided
        // 16dp below (the hint and the typed text both looked off-center).
        val inputBlockHeight =
            maxOf(
                heightOrZero(textFieldPlaceable),
                heightOrZero(placeholderPlaceable),
            )
        val centeredTextPosition =
            labelHeight + ((containerHeight - inputBlockHeight) / 2).coerceAtLeast(0)

        return layout(width, totalHeight) {
            placePlaceables(
                placeables =
                    TextFieldPlaceables(
                        textField = textFieldPlaceable,
                        label = labelPlaceable,
                        placeholder = placeholderPlaceable,
                        leading = leadingPlaceable,
                        trailing = trailingPlaceable,
                        prefix = prefixPlaceable,
                        suffix = suffixPlaceable,
                        container = containerPlaceable,
                        supporting = supportingPlaceable,
                    ),
                width = width,
                totalHeight = totalHeight,
                textPosition = centeredTextPosition,
            )
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int {
        return intrinsicHeight(measurables, width) { intrinsicMeasurable, w ->
            intrinsicMeasurable.maxIntrinsicHeight(w)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int {
        return intrinsicHeight(measurables, width) { intrinsicMeasurable, w ->
            intrinsicMeasurable.minIntrinsicHeight(w)
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int {
        return intrinsicWidth(measurables, height) { intrinsicMeasurable, h ->
            intrinsicMeasurable.maxIntrinsicWidth(h)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int {
        return intrinsicWidth(measurables, height) { intrinsicMeasurable, h ->
            intrinsicMeasurable.minIntrinsicWidth(h)
        }
    }

    private fun intrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
        intrinsicMeasurer: (IntrinsicMeasurable, Int) -> Int,
    ): Int {
        val textFieldWidth = intrinsicMeasurer(measurables.fastFirst { it.layoutId == TextFieldId }, height)
        val widths =
            TextFieldWidths(
                leading = measurables.intrinsicWidthOf(LeadingId, height, intrinsicMeasurer),
                trailing = measurables.intrinsicWidthOf(TrailingId, height, intrinsicMeasurer),
                prefix = measurables.intrinsicWidthOf(PrefixId, height, intrinsicMeasurer),
                suffix = measurables.intrinsicWidthOf(SuffixId, height, intrinsicMeasurer),
                textField = textFieldWidth,
                label = measurables.intrinsicWidthOf(LabelId, height, intrinsicMeasurer),
                placeholder = measurables.intrinsicWidthOf(PlaceholderId, height, intrinsicMeasurer),
            )
        return calculateWidth(widths = widths, constraints = ZeroConstraints)
    }

    private fun IntrinsicMeasureScope.intrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
        intrinsicMeasurer: (IntrinsicMeasurable, Int) -> Int,
    ): Int {
        var remainingWidth = width
        val leadingHeight =
            measurables.fastFirstOrNull { it.layoutId == LeadingId }?.let {
                remainingWidth =
                    remainingWidth.substractConstraintSafely(
                        it.maxIntrinsicWidth(Constraints.Infinity),
                    )
                intrinsicMeasurer(it, width)
            } ?: 0
        val trailingHeight =
            measurables.fastFirstOrNull { it.layoutId == TrailingId }?.let {
                remainingWidth =
                    remainingWidth.substractConstraintSafely(
                        it.maxIntrinsicWidth(Constraints.Infinity),
                    )
                intrinsicMeasurer(it, width)
            } ?: 0
        val labelHeight =
            measurables.fastFirstOrNull { it.layoutId == LabelId }?.let {
                intrinsicMeasurer(it, remainingWidth)
            } ?: 0

        val prefixHeight =
            measurables.fastFirstOrNull { it.layoutId == PrefixId }?.let {
                val height = intrinsicMeasurer(it, remainingWidth)
                remainingWidth =
                    remainingWidth.substractConstraintSafely(
                        it.maxIntrinsicWidth(Constraints.Infinity),
                    )
                height
            } ?: 0
        val suffixHeight =
            measurables.fastFirstOrNull { it.layoutId == SuffixId }?.let {
                val height = intrinsicMeasurer(it, remainingWidth)
                remainingWidth =
                    remainingWidth.substractConstraintSafely(
                        it.maxIntrinsicWidth(Constraints.Infinity),
                    )
                height
            } ?: 0

        val textFieldHeight = intrinsicMeasurer(measurables.fastFirst { it.layoutId == TextFieldId }, remainingWidth)
        val placeholderHeight =
            measurables.fastFirstOrNull { it.layoutId == PlaceholderId }?.let {
                intrinsicMeasurer(it, remainingWidth)
            } ?: 0

        val supportingHeight =
            measurables.fastFirstOrNull { it.layoutId == SupportingId }?.let {
                intrinsicMeasurer(it, width)
            } ?: 0

        val heights =
            TextFieldHeights(
                textField = textFieldHeight,
                label = labelHeight,
                leading = leadingHeight,
                trailing = trailingHeight,
                prefix = prefixHeight,
                suffix = suffixHeight,
                placeholder = placeholderHeight,
                supporting = supportingHeight,
            )

        return calculateHeight(
            heights = heights,
            constraints = ZeroConstraints,
            density = density,
            paddingValues = paddingValues,
        )
    }
}

private fun List<IntrinsicMeasurable>.intrinsicWidthOf(
    slotId: String,
    height: Int,
    intrinsicMeasurer: (IntrinsicMeasurable, Int) -> Int,
): Int = fastFirstOrNull { it.layoutId == slotId }?.let { intrinsicMeasurer(it, height) } ?: 0

private fun Int.substractConstraintSafely(from: Int): Int {
    if (this == Constraints.Infinity) {
        return this
    }
    return this - from
}

/** Los anchos medidos de cada ranura, para que [calculateWidth] no reciba siete sueltos. */
private class TextFieldWidths(
    val leading: Int,
    val trailing: Int,
    val prefix: Int,
    val suffix: Int,
    val textField: Int,
    val label: Int,
    val placeholder: Int,
)

private fun calculateWidth(
    widths: TextFieldWidths,
    constraints: Constraints,
): Int {
    val affixTotalWidth = widths.prefix + widths.suffix
    val middleSection =
        maxOf(
            widths.textField + affixTotalWidth,
            widths.placeholder + affixTotalWidth,
            // Prefix/suffix does not get applied to label
            widths.label,
        )
    val wrappedWidth = widths.leading + middleSection + widths.trailing
    return max(wrappedWidth, constraints.minWidth)
}

/** Los altos medidos de cada ranura, para que [calculateHeight] no reciba once sueltos. */
private class TextFieldHeights(
    val textField: Int,
    val label: Int,
    val leading: Int,
    val trailing: Int,
    val prefix: Int,
    val suffix: Int,
    val placeholder: Int,
    val supporting: Int,
)

private fun calculateHeight(
    heights: TextFieldHeights,
    constraints: Constraints,
    density: Float,
    paddingValues: PaddingValues,
): Int {
    val verticalPadding = density * (paddingValues.calculateTopPadding() + paddingValues.calculateBottomPadding()).value

    val inputFieldHeight =
        maxOf(
            heights.textField,
            heights.placeholder,
            heights.prefix,
            heights.suffix,
        )

    val layoutHeight = verticalPadding + heights.label + inputFieldHeight + heights.supporting

    return max(
        constraints.minHeight,
        maxOf(
            heights.leading,
            heights.trailing,
            layoutHeight.roundToInt(),
        ),
    )
}

/** Los placeables medidos, para que [placePlaceables] no reciba trece sueltos. */
private class TextFieldPlaceables(
    val textField: Placeable,
    val label: Placeable?,
    val placeholder: Placeable?,
    val leading: Placeable?,
    val trailing: Placeable?,
    val prefix: Placeable?,
    val suffix: Placeable?,
    val container: Placeable,
    val supporting: Placeable?,
)

private fun Placeable.PlacementScope.placePlaceables(
    placeables: TextFieldPlaceables,
    width: Int,
    totalHeight: Int,
    textPosition: Int,
) {
    val textFieldPlaceable = placeables.textField
    val labelPlaceable = placeables.label
    val placeholderPlaceable = placeables.placeholder
    val leadingPlaceable = placeables.leading
    val trailingPlaceable = placeables.trailing
    val prefixPlaceable = placeables.prefix
    val suffixPlaceable = placeables.suffix
    val supportingPlaceable = placeables.supporting

    val containerHeight = totalHeight - heightOrZero(labelPlaceable) - heightOrZero(supportingPlaceable)
    val labelHeight = heightOrZero(labelPlaceable)

    placeables.container.place(0, heightOrZero(labelPlaceable))

    labelPlaceable?.placeRelative(0, 0)

    leadingPlaceable?.placeRelative(
        0,
        Alignment.CenterVertically.align(leadingPlaceable.height, containerHeight) + labelHeight,
    )
    trailingPlaceable?.placeRelative(
        width - trailingPlaceable.width,
        Alignment.CenterVertically.align(trailingPlaceable.height, containerHeight) + labelHeight,
    )

    prefixPlaceable?.placeRelative(widthOrZero(leadingPlaceable), textPosition)
    suffixPlaceable?.placeRelative(
        width - widthOrZero(trailingPlaceable) - suffixPlaceable.width,
        textPosition,
    )

    val textHorizontalPosition = widthOrZero(leadingPlaceable) + widthOrZero(prefixPlaceable)
    textFieldPlaceable.placeRelative(textHorizontalPosition, textPosition)
    placeholderPlaceable?.placeRelative(textHorizontalPosition, textPosition)

    supportingPlaceable?.placeRelative(0, totalHeight - heightOrZero(supportingPlaceable))
}
