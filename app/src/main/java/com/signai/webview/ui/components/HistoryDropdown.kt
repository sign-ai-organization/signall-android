package com.signai.webview.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.signai.webview.ui.designsystem.components.SignAiDropdown
import com.signai.webview.ui.designsystem.components.SignAiDropdownMenuItem
import com.signai.webview.ui.designsystem.foundation.SignAiSizes
import com.signai.webview.ui.icons.SignAiIcons

@Composable
fun HistoryDropdown(
    history: List<String>,
    onPick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    SignAiDropdown(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.heightIn(max = SignAiSizes.dropdownMaxHeight),
        trigger = {
            SignAiIconButton(
                icon = SignAiIcons.History,
                contentDescription = "Historial",
                onClick = { expanded = !expanded },
            )
        },
    ) {
        if (history.isEmpty()) {
            SignAiDropdownMenuItem(text = "Sin historial", onClick = {})
        } else {
            val maxChars = 10
            history.forEach { item ->
                val label = if (item.length > maxChars) item.take(maxChars) + "..." else item
                SignAiDropdownMenuItem(
                    text = label,
                    onClick = {
                        expanded = false
                        onPick(item)
                    },
                )
            }
        }
    }
}
