package com.signai.webview.ui.screens.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.components.SignAiSmallIconButton
import com.signai.webview.ui.designsystem.components.SignAiIcon
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.components.Surface
import com.signai.webview.ui.designsystem.foundation.SignAiShapes
import com.signai.webview.ui.designsystem.foundation.SignAiSizes
import com.signai.webview.ui.designsystem.foundation.SignAiSpacing
import com.signai.webview.ui.designsystem.foundation.pressScale
import com.signai.webview.ui.icons.SignAiIcons
import com.signai.webview.ui.theme.SignAiTheme

/**
 * Left drawer — Conversation (spec `CNV-*`): header, `TÚ`/`TRADUCCIÓN` blocks,
 * avatar status and the repeat button.
 */
@Composable
fun ConversationPanel(
    text: String,
    gloss: String,
    avatarStatus: String,
    onRepeat: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SignAiText(
                    text = "CONTEXTO",
                    style = SignAiTheme.typography.eyebrow,
                    color = SignAiTheme.colors.muted,
                )
                SignAiText(
                    text = "Conversación",
                    style = SignAiTheme.typography.h2,
                    color = SignAiTheme.colors.text,
                )
            }
            SignAiSmallIconButton(
                icon = SignAiIcons.X,
                contentDescription = "Cerrar",
                onClick = onClose,
            )
        }

        Spacer(Modifier.height(38.dp))

        ConversationBlock(label = "TÚ") {
            SignAiText(
                text = text.ifBlank { "Aún no has escrito una frase." },
                style = SignAiTheme.typography.quote,
                color = SignAiTheme.colors.text,
            )
        }

        ConversationBlock(label = "TRADUCCIÓN") {
            SignAiText(
                text = gloss,
                style = SignAiTheme.typography.gloss,
                color = SignAiTheme.colors.text,
            )
        }

        Row(
            modifier = Modifier.padding(vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Surface(
                shape = SignAiShapes.cornerIcon,
                color = SignAiTheme.colors.soft,
                modifier = Modifier.size(SignAiSizes.miniAvatarSize),
            ) {
                Box(
                    modifier = Modifier.size(SignAiSizes.miniAvatarSize),
                    contentAlignment = Alignment.Center,
                ) {
                    SignAiIcon(
                        imageVector = SignAiIcons.Sparkle,
                        contentDescription = null,
                        size = 18.dp,
                        tint = SignAiTheme.colors.text,
                    )
                }
            }
            Column {
                SignAiText(
                    text = "AVATAR",
                    style = SignAiTheme.typography.label,
                    color = SignAiTheme.colors.muted,
                )
                SignAiText(
                    text = avatarStatus,
                    style = SignAiTheme.typography.body2,
                    color = SignAiTheme.colors.text,
                )
            }
        }

        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = onRepeat,
            enabled = text.isNotBlank(),
            shape = SignAiShapes.cornerTile,
            color = SignAiTheme.colors.soft,
            interactionSource = interaction,
            modifier = Modifier
                .size(SignAiSizes.repeatButtonSize)
                .pressScale(interaction)
                .semantics {
                    role = Role.Button
                    contentDescription = "Repetir reproducción"
                },
        ) {
            Box(
                modifier = Modifier.size(SignAiSizes.repeatButtonSize),
                contentAlignment = Alignment.Center,
            ) {
                SignAiIcon(
                    imageVector = SignAiIcons.Repeat,
                    contentDescription = null,
                    size = 18.dp,
                    tint = SignAiTheme.colors.text,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        SignAiText(
            text = "Desliza desde el borde para volver",
            style = SignAiTheme.typography.body3,
            color = SignAiTheme.colors.muted,
        )
    }
}

@Composable
private fun ConversationBlock(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
    ) {
        SignAiText(
            text = label,
            style = SignAiTheme.typography.label,
            color = SignAiTheme.colors.muted,
        )
        Spacer(Modifier.height(9.dp))
        content()
    }
}

