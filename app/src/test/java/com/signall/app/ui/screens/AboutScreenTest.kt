package com.signall.app.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.signall.app.BuildConfig
import com.signall.app.testing.Themed
import com.signall.app.testing.tap
import com.signall.app.ui.screens.about.AboutScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Acerca de: la marca, el texto de la tesis y la versión que quedó instalada. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class AboutScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `muestra la marca la tesis y la version compilada`() {
        var backs = 0
        compose.setContent {
            Themed {
                AboutScreen(onBack = { backs++ })
            }
        }
        compose.onNodeWithText("SignALL").assertExists()
        compose.onNodeWithText("SIGNALL").assertExists()
        compose.onNodeWithText("Proyecto de tesis").assertExists()
        compose.onNodeWithText("Pontificia Universidad Javeriana").assertExists()
        compose.onNodeWithText("Versión ${BuildConfig.VERSION_NAME}").assertExists()
        compose.onNodeWithText("Compilación ${BuildConfig.GIT_SHA}").assertExists()
        compose.onNodeWithContentDescription("Volver").tap()
        assertEquals(1, backs)
    }
}
