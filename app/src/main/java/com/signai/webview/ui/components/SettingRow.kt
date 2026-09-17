package com.signai.webview.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.signai.webview.ui.designsystem.components.Checkbox
import com.signai.webview.ui.designsystem.components.CheckboxColors
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.foundation.SignAiSpacing
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
            color = SignAiTheme.colors.text,
            modifier = Modifier.padding(start = SignAiSpacing.xs),
        )
    }
}

@Composable
private fun signAiCheckboxColors(): CheckboxColors = CheckboxColors(
    checkedCheckmarkColor = SignAiTheme.colors.onPrimary,
    uncheckedCheckmarkColor = SignAiTheme.colors.transparent,
    checkedBoxColor = SignAiTheme.colors.primary,
    uncheckedBoxColor = SignAiTheme.colors.transparent,
    disabledCheckedBoxColor = SignAiTheme.colors.disabled,
    disabledUncheckedBoxColor = SignAiTheme.colors.transparent,
    disabledIndeterminateBoxColor = SignAiTheme.colors.disabled,
    checkedBorderColor = SignAiTheme.colors.primary,
    uncheckedBorderColor = SignAiTheme.colors.outline,
    disabledBorderColor = SignAiTheme.colors.disabled,
    disabledUncheckedBorderColor = SignAiTheme.colors.disabled,
    disabledIndeterminateBorderColor = SignAiTheme.colors.disabled,
)
