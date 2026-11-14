package com.signall.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.ui.unit.sp
import com.signall.app.ui.designsystem.components.SignAllIcon
import com.signall.app.ui.designsystem.components.SignAllText
import com.signall.app.ui.designsystem.components.Surface
import com.signall.app.ui.designsystem.components.SurfaceStyle
import com.signall.app.ui.designsystem.foundation.LocalReduceMotion
import com.signall.app.ui.designsystem.foundation.SignAllShapes
import com.signall.app.ui.designsystem.foundation.SignAllSizes
import com.signall.app.ui.icons.SignAllIcons
import com.signall.app.ui.theme.SignAllTheme

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
    val reduceMotion = LocalReduceMotion.current
    val scrimEnter = if (reduceMotion) EnterTransition.None else fadeIn()
    val scrimExit = if (reduceMotion) ExitTransition.None else fadeOut()
    val sheetEnter = if (reduceMotion) EnterTransition.None else slideInVertically(initialOffsetY = { it })
    val sheetExit = if (reduceMotion) ExitTransition.None else slideOutVertically(targetOffsetY = { it })

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scrimEnter,
            exit = scrimExit,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SignAllTheme.colors.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDismiss() },
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = sheetEnter,
            exit = sheetExit,
        ) {
            val topShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Surface(
                style =
                    SurfaceStyle(
                        shape = topShape,
                        color = SignAllTheme.colors.surface,
                    ),
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
                            .background(SignAllTheme.colors.outline, SignAllShapes.cornerPill),
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            SignAllText(
                                text = "TUS FRASES",
                                style = SignAllTheme.typography.eyebrow,
                                color = SignAllTheme.colors.muted,
                            )
                            SignAllText(
                                text = "Historial reciente",
                                style = SignAllTheme.typography.h2,
                                color = SignAllTheme.colors.text,
                            )
                        }
                        SignAllSmallIconButton(
                            icon = SignAllIcons.X,
                            contentDescription = "Cerrar",
                            onClick = onDismiss,
                        )
                    }

                    Spacer(Modifier.height(22.dp))

                    if (history.isEmpty()) {
                        SignAllText(
                            text = "Aún no hay frases recientes.",
                            style = SignAllTheme.typography.body2,
                            color = SignAllTheme.colors.muted,
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
        style =
            SurfaceStyle(
                shape = SignAllShapes.cornerControl,
                color = SignAllTheme.colors.transparent,
                border = BorderStroke(1.dp, SignAllTheme.colors.outline),
            ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SignAllSizes.historyItemMinHeight)
            .semantics { contentDescription = "Reutilizar $phrase" },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SignAllSizes.historyItemMinHeight)
                .padding(start = 16.dp, end = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SignAllText(
                text = phrase,
                style = SignAllTheme.typography.body2,
                color = SignAllTheme.colors.text,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SignAllText(
                    text = "Reutilizar",
                    style = SignAllTheme.typography.body3.copy(fontSize = 11.sp),
                    color = SignAllTheme.colors.muted,
                )
                SignAllIcon(
                    imageVector = SignAllIcons.CaretRight,
                    contentDescription = null,
                    size = 14.dp,
                    tint = SignAllTheme.colors.muted,
                )
            }
        }
    }
}
