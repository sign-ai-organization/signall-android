package com.signai.webview.ui.screens.conversation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signai.webview.ui.components.SignAiSmallIconButton
import com.signai.webview.ui.designsystem.components.SignAiIcon
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.components.Surface
import com.signai.webview.ui.designsystem.foundation.LocalReduceMotion
import com.signai.webview.ui.designsystem.foundation.SignAiShapes
import com.signai.webview.ui.designsystem.foundation.SignAiSizes
import com.signai.webview.ui.designsystem.foundation.pressScale
import com.signai.webview.ui.icons.SignAiIcons
import com.signai.webview.ui.state.ConversationTurn
import com.signai.webview.ui.theme.SignAiTheme

/**
 * Left drawer — Conversation (spec `CNV-*`): header plus a session log that
 * grows as phrases are sent. Each turn appears when it happens (no empty
 * placeholders, no "Esperando"), with a repeat button on the right of its
 * translation to replay that turn's sign. The log lives as long as the app
 * process (`CNV-4`).
 */
@Composable
fun ConversationPanel(
    turns: List<ConversationTurn>,
    onRepeat: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val listState = rememberLazyListState()

    // Keep the newest turn in view as the conversation grows.
    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.lastIndex)
    }

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

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (turns.isEmpty()) {
                SignAiText(
                    text = "Aún no hay frases en esta conversación.",
                    style = SignAiTheme.typography.body2,
                    color = SignAiTheme.colors.muted,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = turns, key = { it.id }) { turn ->
                        ConversationTurnItem(
                            turn = turn,
                            onRepeat = { onRepeat(turn.text) },
                            modifier = if (reduceMotion) Modifier else Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        SignAiText(
            text = "Desliza desde el borde para volver",
            style = SignAiTheme.typography.body3.copy(fontSize = 11.sp),
            color = SignAiTheme.colors.muted,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

@Composable
private fun ConversationTurnItem(
    turn: ConversationTurn,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ConversationBlock(label = "TÚ") {
            SignAiText(
                text = turn.text,
                style = SignAiTheme.typography.quote,
                color = SignAiTheme.colors.text,
            )
        }
        ConversationBlock(label = "TRADUCCIÓN") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SignAiText(
                    text = turn.gloss,
                    style = SignAiTheme.typography.gloss,
                    color = SignAiTheme.colors.text,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                TurnRepeatButton(phrase = turn.text, onClick = onRepeat)
            }
        }
    }
}

/** Per-turn repeat: replays the sign of this translation (`CNV-5`). */
@Composable
private fun TurnRepeatButton(phrase: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = SignAiShapes.cornerRepeat,
        color = SignAiTheme.colors.soft,
        border = BorderStroke(1.dp, SignAiTheme.colors.outline),
        interactionSource = interaction,
        modifier = Modifier
            .size(SignAiSizes.repeatButtonSize)
            .pressScale(interaction)
            .semantics {
                role = Role.Button
                contentDescription = "Repetir la seña de $phrase"
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
}

@Composable
private fun ConversationBlock(
    label: String,
    content: @Composable () -> Unit,
) {
    val lineColor = SignAiTheme.colors.outline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    color = lineColor,
                    start = Offset(0f, size.height - stroke / 2f),
                    end = Offset(size.width, size.height - stroke / 2f),
                    strokeWidth = stroke,
                )
            }
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
