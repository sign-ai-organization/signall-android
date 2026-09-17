package com.signai.webview.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.designsystem.components.Checkbox
import com.signai.webview.ui.designsystem.components.CheckboxColors
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.theme.SignAiTheme

@Composable
fun SettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = signAiCheckboxColors(),
        )
        SignAiText(
            text = label,
            color = SignAiTheme.colors.textMain,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun signAiCheckboxColors(): CheckboxColors = CheckboxColors(
    checkedCheckmarkColor = Color.White,
    uncheckedCheckmarkColor = Color.Transparent,
    checkedBoxColor = SignAiTheme.colors.primary,
    uncheckedBoxColor = Color.Transparent,
    disabledCheckedBoxColor = SignAiTheme.colors.border,
    disabledUncheckedBoxColor = Color.Transparent,
    disabledIndeterminateBoxColor = SignAiTheme.colors.border,
    checkedBorderColor = SignAiTheme.colors.primary,
    uncheckedBorderColor = SignAiTheme.colors.border,
    disabledBorderColor = SignAiTheme.colors.border,
    disabledUncheckedBorderColor = SignAiTheme.colors.border,
    disabledIndeterminateBorderColor = SignAiTheme.colors.border,
)
