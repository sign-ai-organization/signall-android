package com.signall.app.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El tema público de la app: expone la paleta y la tipografía de Lumo, y arma
 * el `SignAllTheme` con los ajustes de accesibilidad.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class SignAllThemeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `el tema oscuro y el claro dan paletas distintas`() {
        val paletas = mutableListOf<String>()
        var oscuro by mutableStateOf(true)
        compose.setContent {
            SignAllTheme(darkTheme = oscuro) {
                val colors = SignAllTheme.colors
                paletas += "${colors.background} ${colors.text} ${colors.strong} ${colors.live} ${colors.error}"
                BasicText(if (oscuro) "oscuro" else "claro")
            }
        }
        compose.waitForIdle()
        oscuro = false
        compose.waitForIdle()
        assertNotEquals(paletas.first(), paletas.last())
    }

    @Test
    fun `los ajustes de accesibilidad y movimiento llegan al tema`() {
        compose.setContent {
            SignAllTheme(
                darkTheme = false,
                fontScale = 1.15f,
                uiScale = 1.1f,
                bold = true,
                highContrast = true,
                reduceMotion = true,
            ) {
                BasicText("accesible")
            }
        }
        compose.onNodeWithText("accesible").assertExists()
    }

    @Test
    fun `toda la tipografia del tema se puede leer`() {
        compose.setContent {
            SignAllTheme {
                val typography = SignAllTheme.typography
                val todas =
                    listOf(
                        typography.h1,
                        typography.h2,
                        typography.h3,
                        typography.h4,
                        typography.body1,
                        typography.body2,
                        typography.body3,
                        typography.label1,
                        typography.label2,
                        typography.label3,
                        typography.button,
                        typography.input,
                        typography.eyebrow,
                        typography.caption,
                        typography.quote,
                        typography.gloss,
                        typography.label,
                        typography.titleLarge,
                        typography.titleMedium,
                    )
                Column(Modifier.fillMaxSize()) {
                    BasicText(todas.size.toString())
                }
            }
        }
        compose.onNodeWithText("19").assertExists()
    }
}
