package com.signai.webview.ui.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.signai.webview.ui.theme.SignAiTheme

// CUSTOMIZED — no re-generate with `lumo --add Dropdown` (Lumo doesn't have one).
// Replaces material3.DropdownMenu with foundation Popup + manual outside-click
// handling (focusable=true triggers onDismissRequest on outside click).
// The popup anchors to the trigger's boundsInWindow (bottom-left aligned).
//
// Usage:
//   SignAiDropdown(
//       expanded = ...,
//       onDismissRequest = { ... },
//       trigger = { MyTrigger(onClick = { ... }) },
//   ) {
//       SignAiDropdownMenuItem("Foo") { ... }
//   }

@Composable
fun SignAiDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 280.dp,
    trigger: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var triggerBounds by remember { mutableStateOf(IntRect.Zero) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val r = coordinates.boundsInWindow()
                triggerBounds = IntRect(
                    left = r.left.toInt(),
                    top = r.top.toInt(),
                    right = r.right.toInt(),
                    bottom = r.bottom.toInt(),
                )
            },
        ) {
            trigger()
        }
        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(
                    x = triggerBounds.left,
                    y = triggerBounds.bottom,
                ),
                onDismissRequest = onDismissRequest,
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    color = SignAiTheme.colors.surface,
                    border = BorderStroke(1.dp, SignAiTheme.colors.outline),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .widthIn(min = 160.dp)
                        .heightIn(max = maxHeight),
                ) {
                    Column(content = content)
                }
            }
        }
    }
}

@Composable
fun SignAiDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = SignAiTheme.colors.text,
    maxLines: Int = Int.MAX_VALUE,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        SignAiText(text, color = color, maxLines = maxLines)
    }
}
