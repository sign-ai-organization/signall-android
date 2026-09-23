package com.signai.webview.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signai.webview.ui.SignAiBranding
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.components.progressindicators.LinearProgressIndicator
import com.signai.webview.ui.theme.SignAiTheme

/**
 * Splash (spec `SPL-*`): logo tile + eyebrow + real progress bar with numeric
 * percentage. Progress is fed from `WebChromeClient.onProgressChanged`.
 */
@Composable
fun SignAiSplash(
    progress: Int,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0, 100)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SignAiTheme.colors.background)
            .semantics { contentDescription = "Cargando…" },
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(SignAiTheme.colors.strong),
                contentAlignment = Alignment.Center,
            ) {
                SignAiText(
                    text = SignAiBranding.LogoLetter,
                    style = SignAiTheme.typography.h1.copy(
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-3).sp,
                    ),
                    color = SignAiTheme.colors.onStrong,
                )
            }

            Box(Modifier.size(16.dp))

            SignAiText(
                text = SignAiBranding.Eyebrow,
                style = SignAiTheme.typography.eyebrow,
                color = SignAiTheme.colors.text,
            )

            Box(Modifier.size(36.dp))

            LinearProgressIndicator(
                progress = clamped / 100f,
                modifier = Modifier.width(148.dp),
                color = SignAiTheme.colors.strong,
                trackColor = SignAiTheme.colors.outline,
            )

            Box(Modifier.size(11.dp))

            SignAiText(
                text = "$clamped%",
                style = SignAiTheme.typography.body3,
                color = SignAiTheme.colors.muted,
            )
        }
    }
}
