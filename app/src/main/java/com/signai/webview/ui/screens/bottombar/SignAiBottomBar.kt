package com.signai.webview.ui.screens.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.designsystem.components.SignAiIcon
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.components.Surface
import com.signai.webview.ui.designsystem.components.textfield.OutlinedTextField
import com.signai.webview.ui.designsystem.components.textfield.OutlinedTextFieldDefaults
import com.signai.webview.ui.designsystem.foundation.SignAiShapes
import com.signai.webview.ui.designsystem.foundation.SignAiSizes
import com.signai.webview.ui.icons.SignAiIcons
import com.signai.webview.ui.state.PlaybackState
import com.signai.webview.ui.theme.SignAiTheme

/**
 * Control panel (spec `CTL-*`): phrase input + history, and the
 * `Traducir | Micrófono` action row from the prototype.
 */
@Composable
fun SignAiBottomBar(
    query: String,
    onQueryChange: (String) -> Unit,
    playbackState: PlaybackState,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    onOpenHistory: () -> Unit,
    enabled: Boolean = true,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    val busy =
        playbackState == PlaybackState.Translating ||
            playbackState == PlaybackState.Signing ||
            playbackState == PlaybackState.Processing
    val controlsEnabled = enabled && !busy

    val sendEnabled =
        controlsEnabled &&
            query.isNotBlank()
    val listening = playbackState == PlaybackState.Listening

    val send: () -> Unit = {
        val text = query.trim()
        if (text.isNotEmpty() && sendEnabled) {
            keyboard?.hide()
            onSend(text)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(SignAiTheme.colors.surface)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // 1dp top border (prototype `border-top: 1px solid var(--line)`).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SignAiTheme.colors.outline),
        )
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    enabled = controlsEnabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { send() }),
                    placeholder = {
                        SignAiText(
                            text = "Escribe una frase...",
                            color = SignAiTheme.colors.muted,
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(SignAiSizes.inputHeight),
                    shape = SignAiShapes.cornerControl,
                    colors = OutlinedTextFieldDefaults.colors().copy(
                        focusedTextColor = SignAiTheme.colors.text,
                        unfocusedTextColor = SignAiTheme.colors.text,
                        cursorColor = SignAiTheme.colors.strong,
                        focusedOutlineColor = SignAiTheme.colors.strong,
                        unfocusedOutlineColor = SignAiTheme.colors.outline,
                        focusedContainerColor = SignAiTheme.colors.background,
                        unfocusedContainerColor = SignAiTheme.colors.background,
                    ),
                )
                ControlIconButton(
                    icon = SignAiIcons.History,
                    contentDescription = "Historial reciente",
                    onClick = onOpenHistory,
                    enabled = controlsEnabled,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = send,
                    enabled = sendEnabled,
                    shape = SignAiShapes.cornerControl,
                    color = SignAiTheme.colors.strong,
                    modifier = Modifier
                        .weight(1f)
                        .height(SignAiSizes.actionButtonHeight)
                        .alpha(if (sendEnabled) 1f else 0.35f)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Traducir"
                        },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(SignAiSizes.actionButtonHeight),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SignAiIcon(
                            imageVector = SignAiIcons.Send,
                            contentDescription = null,
                            size = 18.dp,
                            tint = SignAiTheme.colors.onStrong,
                        )
                        Box(Modifier.size(9.dp))
                        SignAiText(
                            text = "Traducir",
                            style = SignAiTheme.typography.body1.copy(fontWeight = FontWeight.Bold),
                            color = SignAiTheme.colors.onStrong,
                        )
                    }
                }

                ControlIconButton(
                    icon = SignAiIcons.Mic,
                    contentDescription = "Entrada por voz",
                    onClick = onMic,
                    active = listening,
                    enabled = controlsEnabled,
                )
            }
        }
    }
}

@Composable
private fun ControlIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = SignAiShapes.cornerControl
    val container = if (active) SignAiTheme.colors.strong else SignAiTheme.colors.soft
    val tint = if (active) SignAiTheme.colors.onStrong else SignAiTheme.colors.text
    val ringColor = SignAiTheme.colors.soft
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = container,
        modifier = Modifier
            .size(SignAiSizes.actionButtonSize)
            .alpha(if (enabled) 1f else 0.35f)
            .then(
                if (active) {
                    // Prototype `.mic-button.active` has
                    // `box-shadow: 0 0 0 5px var(--soft)`: a 5dp soft ring that
                    // does not affect layout. Drawn behind the surface.
                    Modifier.drawBehind {
                        val ring = 5.dp.toPx()
                        drawRoundRect(
                            color = ringColor,
                            topLeft = Offset(-ring, -ring),
                            size = Size(size.width + ring * 2, size.height + ring * 2),
                            cornerRadius = CornerRadius(16.dp.toPx()),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
    ) {
        Box(
            modifier = Modifier.size(SignAiSizes.actionButtonSize),
            contentAlignment = Alignment.Center,
        ) {
            SignAiIcon(
                imageVector = icon,
                contentDescription = null,
                size = 20.dp,
                tint = tint,
            )
        }
    }
}
