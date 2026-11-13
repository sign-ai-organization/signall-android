package com.signall.app.ui.designsystem.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.signall.app.ui.designsystem.LocalContentColor
import com.signall.app.ui.designsystem.SignAllTheme

// CUSTOMIZED — no re-generate with `lumo --add Text`.
// Wraps foundation BasicText so we don't depend on material3.Text.
// Style defaults to SignAllTheme.typography.body1; color defaults to LocalContentColor.

@Composable
fun SignAllText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    style: TextStyle = SignAllTheme.typography.body1,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
    )
}
