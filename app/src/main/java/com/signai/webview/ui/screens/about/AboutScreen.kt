package com.signai.webview.ui.screens.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signai.webview.BuildConfig
import com.signai.webview.ui.SignAiBranding
import com.signai.webview.ui.components.SignAiSmallIconButton
import com.signai.webview.ui.designsystem.components.SignAiText
import com.signai.webview.ui.designsystem.foundation.SignAiShapes
import com.signai.webview.ui.icons.SignAiIcons
import com.signai.webview.ui.theme.SignAiTheme

/**
 * About screen (spec `NAV-3`, `BRD-2/3/4`): logo, copy and thesis/version meta.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SignAiTheme.colors.background)
            .statusBarsPadding()
            .padding(horizontal = 22.dp, vertical = 26.dp),
    ) {
        SignAiSmallIconButton(
            icon = SignAiIcons.ArrowLeft,
            contentDescription = "Volver",
            onClick = onBack,
            size = 50.dp,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, end = 26.dp, top = 115.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(SignAiTheme.colors.strong, SignAiShapes.cornerPanel),
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

            Spacer(Modifier.height(17.dp))

            SignAiText(
                text = SignAiBranding.Eyebrow,
                style = SignAiTheme.typography.eyebrow,
                color = SignAiTheme.colors.muted,
            )

            Spacer(Modifier.height(7.dp))

            SignAiText(
                text = SignAiBranding.Name,
                style = SignAiTheme.typography.h1.copy(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2.5).sp,
                ),
                color = SignAiTheme.colors.text,
            )

            Spacer(Modifier.height(17.dp))

            SignAiText(
                text = "Una herramienta que transforma tus frases en español a " +
                    "Lengua de Señas Colombiana mediante un avatar 3D.",
                style = SignAiTheme.typography.body1.copy(
                    textAlign = TextAlign.Center,
                    lineHeight = 23.sp,
                ),
                color = SignAiTheme.colors.muted,
            )

            Spacer(Modifier.height(37.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SignAiTheme.colors.outline),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 17.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetaText("Proyecto de tesis")
                MetaText("Pontificia Universidad Javeriana")
                MetaText("Versión ${BuildConfig.VERSION_NAME}")
            }
        }
    }
}

@Composable
private fun MetaText(text: String) {
    SignAiText(
        text = text,
        style = SignAiTheme.typography.body3,
        color = SignAiTheme.colors.muted,
    )
}
