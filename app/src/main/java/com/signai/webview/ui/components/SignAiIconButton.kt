package com.signai.webview.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.signai.webview.ui.designsystem.components.IconButton
import com.signai.webview.ui.designsystem.components.IconButtonVariant
import com.signai.webview.ui.designsystem.components.SignAiIcon
import com.signai.webview.ui.theme.SignAiTheme

@Composable
fun SignAiIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color = SignAiTheme.colors.text,
) {
    IconButton(
        onClick = onClick,
        variant = IconButtonVariant.Ghost,
    ) {
        SignAiIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}
