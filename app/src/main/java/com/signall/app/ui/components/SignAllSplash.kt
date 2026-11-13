package com.signall.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signall.app.ui.designsystem.components.SignAllText
import com.signall.app.ui.designsystem.components.progressindicators.LinearProgressIndicator
import com.signall.app.ui.theme.SignAllTheme

/**
 * Splash (spec `SPL-*`): wordmark + real progress bar with numeric percentage.
 * Progress is fed from `WebChromeClient.onProgressChanged`.
 *
 * The hero is the logo and not a letter tile: the wordmark already says the name, so
 * the uppercase eyebrow that used to sit under the tile was a second copy of it.
 */
@Composable
fun SignAllSplash(
    progress: Int,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0, 100)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SignAllTheme.colors.background)
            .semantics { contentDescription = "Cargando…" },
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SignAllWordmark(height = 32.dp)

            Box(Modifier.size(34.dp))

            LinearProgressIndicator(
                progress = clamped / 100f,
                modifier = Modifier.width(148.dp),
                color = SignAllTheme.colors.strong,
                trackColor = SignAllTheme.colors.outline,
            )

            Box(Modifier.size(11.dp))

            SignAllText(
                text = "$clamped%",
                style = SignAllTheme.typography.body3.copy(fontSize = 11.sp),
                color = SignAllTheme.colors.muted,
            )
        }
    }
}
