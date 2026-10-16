package com.signai.webview.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.designsystem.components.SignAiIcon
import com.signai.webview.ui.designsystem.components.Surface
import com.signai.webview.ui.designsystem.foundation.SignAiShapes
import com.signai.webview.ui.designsystem.foundation.SignAiSizes
import com.signai.webview.ui.theme.SignAiTheme

/**
 * Square icon button on a `soft` surface (spec `SET-1`/`CNV-1` closes).
 *
 * The prototype has two sizes: `.icon-button.small` (36dp, radius 12) and the
 * plain `.icon-button` (50dp, radius 16, used as About's back button), hence
 * [shape] is configurable.
 *
 * [enabled] exists for the steppers: a button that has nothing left to do (the "−" at
 * the minimum) should not look or behave like a live control, and `contentDescription`
 * alone is not enough for a screen reader either — the node goes to
 * `disabled`/`notEnabled` and stops being clickable.
 */
@Composable
fun SignAiSmallIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = SignAiSizes.iconButtonSmall,
    shape: Shape = SignAiShapes.cornerIcon,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = SignAiTheme.colors.soft,
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            SignAiIcon(
                imageVector = icon,
                contentDescription = null,
                size = 18.dp,
                tint = if (enabled) {
                    SignAiTheme.colors.text
                } else {
                    SignAiTheme.colors.textDisabled
                },
            )
        }
    }
}
