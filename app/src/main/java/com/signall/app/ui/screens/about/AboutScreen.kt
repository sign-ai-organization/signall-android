package com.signall.app.ui.screens.about

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
import com.signall.app.BuildConfig
import com.signall.app.ui.SignAllBranding
import com.signall.app.ui.components.SignAllBrandTile
import com.signall.app.ui.components.SignAllSmallIconButton
import com.signall.app.ui.designsystem.components.SignAllText
import com.signall.app.ui.designsystem.foundation.SignAllShapes
import com.signall.app.ui.icons.SignAllIcons
import com.signall.app.ui.theme.SignAllTheme

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
            .background(SignAllTheme.colors.background)
            .statusBarsPadding()
            .padding(horizontal = 22.dp, vertical = 26.dp),
    ) {
        SignAllSmallIconButton(
            icon = SignAllIcons.ArrowLeft,
            contentDescription = "Volver",
            onClick = onBack,
            size = 50.dp,
            shape = SignAllShapes.cornerControl,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, end = 26.dp, top = 115.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SignAllBrandTile(
                size = 68.dp,
                contentDescription = SignAllBranding.Name,
            )

            Spacer(Modifier.height(17.dp))

            SignAllText(
                text = SignAllBranding.Eyebrow,
                style = SignAllTheme.typography.eyebrow,
                color = SignAllTheme.colors.muted,
            )

            Spacer(Modifier.height(7.dp))

            SignAllText(
                text = SignAllBranding.Name,
                style = SignAllTheme.typography.h1.copy(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2.5).sp,
                ),
                color = SignAllTheme.colors.text,
            )

            Spacer(Modifier.height(17.dp))

            SignAllText(
                text = "Una herramienta que transforma tus frases en español a " +
                    "Lengua de Señas Colombiana mediante un avatar 3D.",
                style = SignAllTheme.typography.body1.copy(
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 23.sp,
                ),
                color = SignAllTheme.colors.muted,
            )

            Spacer(Modifier.height(37.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SignAllTheme.colors.outline),
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
                // El SHA es lo que dice qué APK está instalado sin conectar el
                // teléfono: la página de descarga publica el mismo valor
                // (`tools/wifi/publicar-apk.sh`).
                MetaText("Compilación ${BuildConfig.GIT_SHA}")
            }
        }
    }
}

@Composable
private fun MetaText(text: String) {
    SignAllText(
        text = text,
        style = SignAllTheme.typography.body3,
        color = SignAllTheme.colors.muted,
    )
}
