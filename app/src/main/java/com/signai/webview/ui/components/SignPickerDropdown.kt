package com.signai.webview.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.designsystem.components.SignAiDropdown
import com.signai.webview.ui.designsystem.components.SignAiDropdownMenuItem
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.components.Surface
import com.signai.webview.ui.theme.SignAiTheme

@Composable
fun SignPickerDropdown(
    current: String,
    options: List<String>,
    onPick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    SignAiDropdown(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        maxHeight = 200.dp,
        trigger = {
            Surface(
                onClick = { expanded = !expanded },
                shape = RoundedCornerShape(8.dp),
                color = SignAiTheme.colors.transparent,
                border = BorderStroke(1.dp, SignAiTheme.colors.outline),
                modifier = Modifier.fillMaxWidth().height(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    SignAiText(
                        text = "Seña: $current",
                        color = SignAiTheme.colors.text,
                        maxLines = 1,
                    )
                }
            }
        },
    ) {
        options.forEach { option ->
            SignAiDropdownMenuItem(
                text = if (option == current) "✓ $option" else option,
                onClick = {
                    expanded = false
                    onPick(option)
                },
            )
        }
    }
}
