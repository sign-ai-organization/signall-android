package com.signai.webview.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.designsystem.components.Button
import com.signai.webview.ui.designsystem.components.SignAiDropdown
import com.signai.webview.ui.designsystem.components.SignAiDropdownMenuItem
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.components.signAiButtonColors
import com.signai.webview.ui.designsystem.foundation.SignAiShapes
import com.signai.webview.ui.designsystem.foundation.SignAiSizes
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
            Button(
                onClick = { expanded = !expanded },
                colors = signAiButtonColors(
                    containerColor = SignAiTheme.colors.transparent,
                    contentColor = SignAiTheme.colors.text,
                    borderColor = SignAiTheme.colors.outline,
                ),
                shape = SignAiShapes.cornerMedium,
                modifier = Modifier.fillMaxWidth().height(SignAiSizes.fieldHeight),
            ) {
                SignAiText(
                    text = "Seña: $current",
                    color = SignAiTheme.colors.text,
                    maxLines = 1,
                )
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
