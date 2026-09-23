package com.signai.webview.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Small square icon button on a `soft` surface (spec `SET-1`/`CNV-1` closes). */
@Composable
fun SignAiSmallIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = SignAiSizes.iconButtonSmall,
) {
    Surface(
        onClick = onClick,
        shape = SignAiShapes.cornerIcon,
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
                tint = SignAiTheme.colors.text,
            )
        }
    }
}
