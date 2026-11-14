package com.signall.app.ui.screens.conversation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signall.app.ui.components.SignAllSmallIconButton
import com.signall.app.ui.designsystem.components.SignAllIcon
import com.signall.app.ui.designsystem.components.SignAllText
import com.signall.app.ui.designsystem.components.Surface
import com.signall.app.ui.designsystem.components.SurfaceStyle
import com.signall.app.ui.designsystem.foundation.LocalReduceMotion
import com.signall.app.ui.designsystem.foundation.SignAllShapes
import com.signall.app.ui.designsystem.foundation.SignAllSizes
import com.signall.app.ui.designsystem.foundation.SignAllSpacing
import com.signall.app.ui.designsystem.foundation.horizontalDragBlocked
import com.signall.app.ui.designsystem.foundation.pressScale
import com.signall.app.ui.icons.SignAllIcons
import com.signall.app.ui.state.ConversationTurn
import com.signall.app.ui.theme.SignAllTheme

/**
 * Left drawer — Conversation (spec `CNV-*`): header plus a session log that
 * grows as phrases are sent. Each turn appears when it happens (no empty
 * placeholders, no "Esperando"), with a repeat button attached to its
 * translation to replay that turn's sign. The log lives as long as the app
 * process (`CNV-4`).
 *
 * The log is laid out as a conversation rather than as two stacked blocks
 * (`CNV-2`/`CNV-3`): the phrase sits on the end side, filled with `soft`, and
 * its LSC gloss on the start side, outlined on `surface`. The brand blue stays
 * out of it — it belongs to the main action and the controls — so the two sides
 * are told apart by side, fill and the small label, never by color alone.
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

    // Keep the newest turn in view as the conversation grows (`CNV-4`).
    //
    // Scrolling to the item aligns its top, and a turn is two bubbles tall: with
    // a long gloss the last translation would end below the fold, which is the
    // one place the reader looks. So the rest of the turn is brought in after the
    // scroll, and the bottom edge ends up on the end of the newest turn.
    LaunchedEffect(turns.size, reduceMotion) {
        if (turns.isEmpty()) return@LaunchedEffect
        if (reduceMotion) {
            listState.scrollToItem(turns.lastIndex)
        } else {
            listState.animateScrollToItem(turns.lastIndex)
        }
        listState.sitOnNewestTurn(animated = !reduceMotion)
    }

    // A turn is born with the gloss of its first sentence and grows as the rest
    // arrive (`CNV-3`). Every growth re-sits the bottom; otherwise the turn keeps
    // growing after it was scrolled to and the translation stays cut by the edge.
    // The type size is watched for the same reason: a bigger interface (ACC-2)
    // makes every bubble taller without changing the list.
    val newestGloss = turns.lastOrNull()?.gloss
    val phraseTextSize = SignAllTheme.typography.quote.fontSize
    LaunchedEffect(newestGloss, phraseTextSize, reduceMotion) {
        if (turns.isEmpty()) return@LaunchedEffect
        listState.sitOnNewestTurn(animated = !reduceMotion)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 28.dp),
    ) {
        ConversationHeader(onClose = onClose)

        Spacer(Modifier.height(38.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (turns.isEmpty()) {
                ConversationEmptyState()
            } else {
                ConversationLog(
                    turns = turns,
                    listState = listState,
                    reduceMotion = reduceMotion,
                    onRepeat = onRepeat,
                )
            }
        }

        SignAllText(
            text = "Desliza desde el borde para volver",
            style = SignAllTheme.typography.body3.copy(fontSize = 11.sp),
            color = SignAllTheme.colors.muted,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

@Composable
private fun ConversationHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SignAllText(
                text = "CONTEXTO",
                style = SignAllTheme.typography.eyebrow,
                color = SignAllTheme.colors.muted,
            )
            SignAllText(
                text = "Conversación",
                style = SignAllTheme.typography.h2,
                color = SignAllTheme.colors.text,
            )
        }
        SignAllSmallIconButton(
            icon = SignAllIcons.X,
            contentDescription = "Cerrar",
            onClick = onClose,
        )
    }
}

@Composable
private fun ConversationEmptyState() {
    SignAllText(
        text = "Aún no hay frases en esta conversación.",
        style = SignAllTheme.typography.body2,
        color = SignAllTheme.colors.muted,
    )
}

@Composable
private fun ConversationLog(
    turns: List<ConversationTurn>,
    listState: LazyListState,
    reduceMotion: Boolean,
    onRepeat: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        // The list is vertical: hand horizontal drags back to the
        // drawer, so swiping over the turns closes it like swiping
        // over the header does (spec `EDG-9`).
        modifier = Modifier
            .fillMaxSize()
            .horizontalDragBlocked(),
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

/**
 * Brings the part of the newest turn that hangs below the bottom edge into view.
 *
 * The layout is only measured once it stops moving: `animateItem` keeps sliding
 * the turns into place after the scroll ends, and a measurement taken mid-flight
 * is a position the list no longer has a frame later. The loop is capped so a
 * layout that never settles cannot keep the effect alive.
 */
private suspend fun LazyListState.sitOnNewestTurn(animated: Boolean) {
    var previous = Int.MIN_VALUE
    repeat(120) {
        withFrameNanos { }
        val info = layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return
        val overflow = lastVisible.offset + lastVisible.size - info.viewportEndOffset
        if (overflow == previous) {
            if (overflow <= 0) return
            if (animated) animateScrollBy(overflow.toFloat()) else scrollBy(overflow.toFloat())
            return
        }
        previous = overflow
    }
}

@Composable
private fun ConversationTurnItem(
    turn: ConversationTurn,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SignAllSpacing.smLg),
    ) {
        // The phrase on the speaker's side. `fillMaxWidth(fraction)` caps how
        // wide a long phrase can get, and `wrapContentWidth` lets a short one
        // stay as narrow as its text instead of stretching into a block.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            ConversationBubble(
                label = "TÚ",
                color = SignAllTheme.colors.soft,
                shape = SignAllShapes.cornerBubbleEnd,
                modifier = Modifier
                    .fillMaxWidth(SignAllSizes.conversationBubbleMaxWidthFraction)
                    .wrapContentWidth(Alignment.End),
            ) {
                SignAllText(
                    text = turn.text,
                    style = SignAllTheme.typography.quote,
                    color = SignAllTheme.colors.text,
                )
            }
        }

        Spacer(Modifier.height(SignAllSpacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The gloss bubble takes what it needs but always leaves room for the
            // repeat button: `weight(fill = false)` is its max width, not its
            // width, so a short gloss ("HOLA") stays a small bubble.
            ConversationBubble(
                label = "TRADUCCIÓN",
                color = SignAllTheme.colors.surface,
                shape = SignAllShapes.cornerBubbleStart,
                modifier = Modifier.weight(1f, fill = false),
            ) {
                SignAllText(
                    text = turn.gloss,
                    style = SignAllTheme.typography.gloss,
                    color = SignAllTheme.colors.text,
                )
            }
            Spacer(Modifier.width(SignAllSpacing.smLg))
            TurnRepeatButton(phrase = turn.text, onClick = onRepeat)
        }
    }
}

/**
 * One side of a turn: a hairline bubble with the small label on top and its
 * text below. `color` and `shape` are passed in so the two sides keep their
 * own identity (filled + squared bottom corner on the speaker's side).
 */
@Composable
private fun ConversationBubble(
    label: String,
    color: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        style =
            SurfaceStyle(
                shape = shape,
                color = color,
                border = BorderStroke(1.dp, SignAllTheme.colors.outline),
            ),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = SignAllSpacing.md,
                vertical = SignAllSpacing.smLg,
            ),
        ) {
            SignAllText(
                text = label,
                style = SignAllTheme.typography.label,
                color = SignAllTheme.colors.muted,
            )
            Spacer(Modifier.height(SignAllSpacing.xs))
            content()
        }
    }
}

/** Per-turn repeat: replays the sign of this translation (`CNV-5`). */
@Composable
private fun TurnRepeatButton(phrase: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        style =
            SurfaceStyle(
                shape = SignAllShapes.cornerRepeat,
                color = SignAllTheme.colors.soft,
                border = BorderStroke(1.dp, SignAllTheme.colors.outline),
            ),
        interactionSource = interaction,
        modifier = Modifier
            .size(SignAllSizes.repeatButtonSize)
            .pressScale(interaction)
            .semantics {
                role = Role.Button
                contentDescription = "Repetir la seña de $phrase"
            },
    ) {
        Box(
            modifier = Modifier.size(SignAllSizes.repeatButtonSize),
            contentAlignment = Alignment.Center,
        ) {
            SignAllIcon(
                imageVector = SignAllIcons.Repeat,
                contentDescription = null,
                size = 18.dp,
                tint = SignAllTheme.colors.text,
            )
        }
    }
}
