package com.signai.webview.ui.designsystem.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.signai.webview.ui.designsystem.LocalContentColor
import com.signai.webview.ui.designsystem.SignAiTheme

// CUSTOMIZED — no re-generate with `lumo --add Text`.
// Wraps foundation BasicText so we don't depend on material3.Text.
// Style defaults to SignAiTheme.typography.body1; color defaults to LocalContentColor.

@Composable
fun SignAiText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    style: TextStyle = SignAiTheme.typography.body1,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
    )
}
