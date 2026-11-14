package com.signall.app.ui.screens

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.signall.app.domain.Character
import com.signall.app.domain.Setting
import com.signall.app.domain.Settings
import com.signall.app.testing.Themed
import com.signall.app.testing.tap
import com.signall.app.ui.screens.settings.SettingsActions
import com.signall.app.ui.screens.settings.SettingsSidebar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El drawer de ajustes: avatar, accesibilidad y apariencia. Lo que importa es
 * que cada control exista, diga lo que está puesto y avise el cambio con el
 * ajuste correcto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class SettingsSidebarTest {

    @get:Rule
    val compose = createComposeRule()

    private val toggles = mutableListOf<Pair<Setting, Boolean>>()
    private var character: Character? = null
    private var speed: Int? = null
    private var interfaceSize: Int? = null
    private var pickedSign: String? = null
    private var closed = 0
    private var about = 0

    private fun render(
        settings: Settings = Settings(),
        canSwitchAvatar: Boolean = true,
        signs: List<String> = listOf("0000_0000_0000", "0001_0000_0000"),
    ) {
        compose.setContent {
            Themed {
                SettingsSidebar(
                    settings = settings,
                    canSwitchAvatar = canSwitchAvatar,
                    sign = signs.first(),
                    signs = signs,
                    actions =
                        SettingsActions(
                            onToggle = { setting, on -> toggles += setting to on },
                            onCharacter = { character = it },
                            onSpeed = { speed = it },
                            onInterfaceSize = { interfaceSize = it },
                            onPickSign = { pickedSign = it },
                            onClose = { closed++ },
                            onOpenAbout = { about++ },
                        ),
                )
            }
        }
    }

    @Test
    fun `las tres secciones y la marca estan`() {
        render()
        compose.onNodeWithText("AVATAR").assertExists()
        compose.onNodeWithText("ACCESIBILIDAD").assertExists()
        compose.onNodeWithText("APARIENCIA").assertExists()
        compose.onNodeWithText("Personaje").assertExists()
        compose.onNodeWithText("Velocidad de señas").assertExists()
        compose.onNodeWithText("Tamaño de interfaz").assertExists()
        compose.onNodeWithText("Acerca de").assertExists()
    }

    @Test
    fun `en release no existe el bloque avanzado`() {
        render()
        // `DEV_TOOLS` está apagado en esta compilación: ni la sección ni sus filas.
        compose.onNodeWithText("AVANZADO").assertDoesNotExist()
        compose.onNodeWithText("Seña de ejemplo").assertDoesNotExist()
    }

    @Test
    fun `los interruptores avisan que ajuste cambio`() {
        render()
        // Los switches se dibujan en orden: negrita, contraste, texto grande,
        // reducir animaciones, modo oscuro.
        val switches = compose.onAllNodes(isToggleable())
        switches[0].tap()
        switches[1].tap()
        switches[2].tap()
        switches[3].tap()
        switches[4].tap()
        assertEquals(
            listOf(
                Setting.Bold to true,
                Setting.Contrast to true,
                Setting.Large to true,
                Setting.ReduceMotion to true,
                Setting.DarkTheme to false,
            ),
            toggles,
        )
    }

    @Test
    fun `el modo oscuro se llama por su nombre segun lo que este puesto`() {
        render(settings = Settings(darkTheme = false))
        compose.onNodeWithText("Modo claro").assertExists()
    }

    @Test
    fun `el selector de personaje abre el menu y avisa la eleccion`() {
        render()
        compose.onNodeWithText("Juan").tap()
        compose.onNodeWithText("Pauline").tap()
        assertEquals(Character.Pauline, character)
    }

    @Test
    fun `con la app ocupada el selector de personaje no abre`() {
        render(canSwitchAvatar = false)
        compose.onNodeWithText("Juan").assertIsNotEnabled()
    }

    @Test
    fun `la velocidad y el tamano de interfaz avisan su nuevo valor`() {
        render(settings = Settings(speed = 50, interfaceSize = 50))
        compose.onNodeWithContentDescription("Aumentar la velocidad de las señas").tap()
        compose.onNodeWithContentDescription("Reducir el tamaño de la interfaz").tap()
        assertEquals(60, speed)
        assertEquals(45, interfaceSize)
    }

    @Test
    fun `cerrar y acerca de avisan al host`() {
        render()
        compose.onNodeWithContentDescription("Cerrar").tap()
        compose.onNodeWithText("Acerca de").performScrollTo().tap()
        assertEquals(1, closed)
        assertTrue(about == 1)
    }
}
