package com.signai.webview.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.designsystem.components.SignAiIcon
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.foundation.SignAiSizes
import com.signai.webview.ui.theme.SignAiTheme

/**
 * Reusable settings row (spec `SET-9`): optional icon + label on the left, any
 * control on the right. Min height 50.
 */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SignAiSizes.settingRowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            if (icon != null) {
                SignAiIcon(
                    imageVector = icon,
                    contentDescription = null,
                    size = 18.dp,
                    tint = SignAiTheme.colors.muted,
                )
            }
            SignAiText(
                text = label,
                style = SignAiTheme.typography.body2,
                color = SignAiTheme.colors.text,
            )
        }
        content()
    }
}
