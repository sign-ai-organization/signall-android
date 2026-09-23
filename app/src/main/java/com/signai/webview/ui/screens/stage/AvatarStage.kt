package com.signai.webview.ui.screens.stage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.signai.webview.ui.designsystem.components.SignAiIcon
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.components.Surface
import com.signai.webview.ui.designsystem.foundation.SignAiShapes
import com.signai.webview.ui.designsystem.foundation.SignAiSpacing
import com.signai.webview.ui.designsystem.foundation.pressScale
import com.signai.webview.ui.icons.SignAiIcons
import com.signai.webview.ui.theme.SignAiTheme

/**
 * Avatar stage (spec `STG-*`): the 3D WebView is the protagonist, overlaid with
 * the status hint (top-left) and the Repetir button (bottom-center, just above
 * the control bar, only when the last playback is `done`). [bottomInset] keeps
 * the button clear of the overlaid control bar.
 *
 * The WebView itself is passed as [content] so this stays a presentation
 * component and never touches the bridge directly.
 */
@Composable
fun AvatarStage(
    status: String,
    doneVisible: Boolean,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        content()

        // stage-hint: live dot + current playback status.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 25.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SignAiSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(SignAiTheme.colors.strong, CircleShape),
            )
            SignAiText(
                text = status,
                style = SignAiTheme.typography.body3,
                color = SignAiTheme.colors.muted,
            )
        }

        // Repetir: only after a completed playback, centered a little above the
        // overlaid control bar.
        AnimatedVisibility(
            visible = doneVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp + bottomInset),
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
        ) {
            val interaction = remember { MutableInteractionSource() }
            Surface(
                onClick = onRepeat,
                shape = SignAiShapes.cornerPill,
                color = SignAiTheme.colors.surface,
                border = BorderStroke(1.dp, SignAiTheme.colors.outline),
                interactionSource = interaction,
                modifier = Modifier
                    .pressScale(interaction)
                    .semantics { contentDescription = "Repetir reproducción" },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SignAiIcon(
                        imageVector = SignAiIcons.Repeat,
                        contentDescription = null,
                        size = 18.dp,
                        tint = SignAiTheme.colors.text,
                    )
                    SignAiText(
                        text = "Repetir",
                        style = SignAiTheme.typography.body3,
                        color = SignAiTheme.colors.text,
                    )
                }
            }
        }
    }
}
