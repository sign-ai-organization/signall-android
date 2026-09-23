package com.signai.webview.ui.screens.bottombar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.signai.webview.BuildConfig
import com.signai.webview.ui.components.HistoryDropdown
import com.signai.webview.ui.designsystem.components.Button
import com.signai.webview.ui.designsystem.components.ButtonVariant
import com.signai.webview.ui.designsystem.components.SignAiIcon
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.components.textfield.OutlinedTextField
import com.signai.webview.ui.designsystem.components.textfield.OutlinedTextFieldDefaults
import com.signai.webview.ui.designsystem.foundation.SignAiShapes
import com.signai.webview.ui.designsystem.foundation.SignAiSizes
import com.signai.webview.ui.designsystem.foundation.SignAiSpacing
import com.signai.webview.ui.icons.SignAiIcons
import com.signai.webview.ui.theme.SignAiTheme

@Composable
fun SignAiBottomBar(
    history: List<String>,
    onReplay: (String) -> Unit,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(SignAiSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(SignAiSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SignAiSpacing.sm),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    SignAiText(
                        text = "Versión: ${BuildConfig.VERSION_NAME}",
                        color = SignAiTheme.colors.text,
                    )
                },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors().copy(
                    focusedTextColor = SignAiTheme.colors.text,
                    unfocusedTextColor = SignAiTheme.colors.text,
                    cursorColor = SignAiTheme.colors.primary,
                    focusedOutlineColor = SignAiTheme.colors.primary,
                    unfocusedOutlineColor = SignAiTheme.colors.outline,
                    focusedContainerColor = SignAiTheme.colors.surface,
                    unfocusedContainerColor = SignAiTheme.colors.surface,
                ),
            )
            HistoryDropdown(
                history = history,
                onPick = { text ->
                    keyboard?.hide()
                    onReplay(text)
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SignAiSpacing.sm)) {
            val shape = SignAiShapes.cornerSmall
            Button(
                onClick = onMic,
                variant = ButtonVariant.PrimaryOutlined,
                shape = shape,
                modifier = Modifier.weight(0.25f).height(SignAiSizes.buttonHeight),
            ) {
                SignAiIcon(
                    imageVector = SignAiIcons.Mic,
                    contentDescription = "Micrófono",
                )
            }
            Button(
                onClick = {
                    val text = query.trim()
                    query = ""
                    keyboard?.hide()
                    if (text.isNotEmpty()) onSend(text)
                },
                variant = ButtonVariant.Primary,
                shape = shape,
                modifier = Modifier.weight(0.75f).height(SignAiSizes.buttonHeight),
            ) {
                SignAiIcon(
                    imageVector = SignAiIcons.Send,
                    contentDescription = "Enviar",
                )
            }
        }
    }
}
