package com.signai.webview.ui.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.theme.LocalContentColor

// CUSTOMIZED — no re-generate with `lumo --add Icon`.
// Wraps foundation Image + rememberVectorPainter so we don't depend on material3.Icon.
// Tint defaults to LocalContentColor (same convention as M3 Icon).

@Composable
fun SignAiIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    val painter: Painter = rememberVectorPainter(image = imageVector)
    val colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint)
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = colorFilter,
    )
}
