package com.signai.webview.ui.screens.bottombar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.signai.webview.BuildConfig
import com.signai.webview.ui.components.HistoryDropdown
import com.signai.webview.ui.designsystem.components.SignAiIcon
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.components.Surface
import com.signai.webview.ui.designsystem.components.textfield.OutlinedTextField
import com.signai.webview.ui.designsystem.components.textfield.OutlinedTextFieldDefaults
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
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val shape = RoundedCornerShape(6.dp)
            Surface(
                onClick = onMic,
                shape = shape,
                color = SignAiTheme.colors.surface,
                border = BorderStroke(1.dp, SignAiTheme.colors.outline),
                modifier = Modifier.weight(0.25f).height(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    SignAiIcon(
                        imageVector = SignAiIcons.Mic,
                        contentDescription = "Micrófono",
                        tint = SignAiTheme.colors.text,
                    )
                }
            }
            Surface(
                onClick = {
                    val text = query.trim()
                    query = ""
                    keyboard?.hide()
                    if (text.isNotEmpty()) onSend(text)
                },
                shape = shape,
                color = SignAiTheme.colors.primary,
                modifier = Modifier.weight(0.75f).height(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    SignAiIcon(
                        imageVector = SignAiIcons.Send,
                        contentDescription = "Enviar",
                        tint = SignAiTheme.colors.onPrimary,
                    )
                }
            }
        }
    }
}
