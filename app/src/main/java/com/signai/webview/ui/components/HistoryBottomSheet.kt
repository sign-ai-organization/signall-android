package com.signai.webview.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.designsystem.components.SignAiIcon
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.components.Surface
import com.signai.webview.ui.designsystem.foundation.SignAiShapes
import com.signai.webview.ui.designsystem.foundation.SignAiSizes
import com.signai.webview.ui.icons.SignAiIcons
import com.signai.webview.ui.theme.SignAiTheme

/**
 * History bottom sheet (spec `HIS-*`): handle, `TUS FRASES` eyebrow, phrase list
 * with `Reutilizar →`, and an empty state. Replaces the old anchored dropdown.
 */
@Composable
fun HistoryBottomSheet(
    visible: Boolean,
    history: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SignAiTheme.colors.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDismiss() },
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            val topShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Surface(
                shape = topShape,
                color = SignAiTheme.colors.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Historial reciente" }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* consume taps so the scrim does not close the sheet */ },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 21.dp, end = 21.dp, top = 10.dp, bottom = 24.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(38.dp)
                            .height(4.dp)
                            .background(SignAiTheme.colors.outline, SignAiShapes.cornerPill),
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            SignAiText(
                                text = "TUS FRASES",
                                style = SignAiTheme.typography.eyebrow,
                                color = SignAiTheme.colors.muted,
                            )
                            SignAiText(
                                text = "Historial reciente",
                                style = SignAiTheme.typography.h2,
                                color = SignAiTheme.colors.text,
                            )
                        }
                        SignAiSmallIconButton(
                            icon = SignAiIcons.X,
                            contentDescription = "Cerrar",
                            onClick = onDismiss,
                        )
                    }

                    Spacer(Modifier.height(22.dp))

                    if (history.isEmpty()) {
                        SignAiText(
                            text = "Aún no hay frases recientes.",
                            style = SignAiTheme.typography.body2,
                            color = SignAiTheme.colors.muted,
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            history.forEach { phrase ->
                                HistoryItem(phrase = phrase, onClick = { onPick(phrase) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(phrase: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = SignAiShapes.cornerControl,
        color = SignAiTheme.colors.transparent,
        border = BorderStroke(1.dp, SignAiTheme.colors.outline),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SignAiSizes.historyItemMinHeight)
            .semantics { contentDescription = "Reutilizar $phrase" },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SignAiSizes.historyItemMinHeight)
                .padding(start = 16.dp, end = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SignAiText(
                text = phrase,
                style = SignAiTheme.typography.body2,
                color = SignAiTheme.colors.text,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SignAiText(
                    text = "Reutilizar",
                    style = SignAiTheme.typography.body3,
                    color = SignAiTheme.colors.muted,
                )
                SignAiIcon(
                    imageVector = SignAiIcons.CaretRight,
                    contentDescription = null,
                    size = 14.dp,
                    tint = SignAiTheme.colors.muted,
                )
            }
        }
    }
}
