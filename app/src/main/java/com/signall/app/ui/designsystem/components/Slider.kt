package com.signall.app.ui.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.signall.app.ui.theme.SignAllTheme

/**
 * Minimal single-thumb slider (spec `SET-4`/`ACC-1`). Compose foundation has no
 * slider and we avoid Material 3, so it is drawn manually.
 *
 * [value] is expected in `0f..1f`.
 */
@Composable
fun SignAllSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = SignAllTheme.colors.strong,
    inactiveColor: Color = SignAllTheme.colors.soft,
) {
    val coerced = value.coerceIn(0f, 1f)
    val surfaceColor = SignAllTheme.colors.surface
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(coerced, 0f..1f)
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onValueChange((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures { change, _ ->
                    onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            val centerY = size.height / 2f
            val trackHeight = 4.dp.toPx()
            val thumbRadius = 8.dp.toPx()
            val usable = size.width - thumbRadius * 2
            val thumbX = thumbRadius + usable * coerced

            drawLine(
                color = inactiveColor,
                start = Offset(thumbRadius, centerY),
                end = Offset(size.width - thumbRadius, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = if (enabled) activeColor else inactiveColor,
                start = Offset(thumbRadius, centerY),
                end = Offset(thumbX, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = surfaceColor,
                radius = thumbRadius + 2.dp.toPx(),
                center = Offset(thumbX, centerY),
            )
            drawCircle(
                color = if (enabled) activeColor else inactiveColor,
                radius = thumbRadius,
                center = Offset(thumbX, centerY),
            )
        }
    }
}
