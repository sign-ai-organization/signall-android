package com.signall.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.signall.app.testing.Themed
import com.signall.app.testing.tap
import com.signall.app.ui.screens.bottombar.BottomBarActions
import com.signall.app.ui.screens.bottombar.BottomBarAvailability
import com.signall.app.ui.screens.bottombar.BottomBarInput
import com.signall.app.ui.screens.bottombar.SignAllBottomBar
import com.signall.app.ui.state.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El panel de control es donde el usuario hace todo: escribe, dicta, manda y
 * abre el historial. Lo que se prueba acá es qué se puede tocar en cada estado
 * —y que el toque llegue al callback correcto—, que es lo que el SRS describe
 * en las filas de `tab:estados-ui`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class SignAllBottomBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val asked = mutableListOf<String>()

    private fun render(
        input: BottomBarInput = BottomBarInput(query = ""),
        availability: BottomBarAvailability = BottomBarAvailability(),
        playbackState: PlaybackState = PlaybackState.Inactivo,
        editable: Boolean = false,
    ) {
        compose.setContent {
            Themed {
                var query by remember { mutableStateOf(input.query) }
                SignAllBottomBar(
                    input = if (editable) input.copy(query = query) else input,
                    availability = availability,
                    playbackState = playbackState,
                    actions =
                        BottomBarActions(
                            onQueryChange = {
                                asked += "query:$it"
                                if (editable) query = it
                            },
                            onSend = { asked += "send:$it" },
                            onMic = { asked += "mic" },
                            onOpenHistory = { asked += "history" },
                            onLive = { asked += "live" },
                        ),
                )
            }
        }
    }

    @Test
    fun `el campo vacio muestra el placeholder y Traducir apagado`() {
        render()
        compose.onNodeWithText("Escribe una frase...").assertExists()
        compose.onNodeWithContentDescription("Traducir").assertIsNotEnabled()
    }

    @Test
    fun `con texto Traducir manda la frase sin espacios de mas`() {
        render(input = BottomBarInput(query = "  hola  "))
        compose.onNodeWithContentDescription("Traducir").assertIsEnabled().tap()
        assertEquals(listOf("send:hola"), asked)
    }

    @Test
    fun `escribir en el campo avisa cada cambio`() {
        render(editable = true)
        compose.onNodeWithText("Escribe una frase...").performTextInput("agua")
        assertEquals(listOf("query:agua"), asked)
        compose.onNodeWithText("agua").assertExists()
    }

    @Test
    fun `el microfono y el historial avisan su toque`() {
        render()
        compose.onNodeWithContentDescription("Entrada por voz").tap()
        compose.onNodeWithContentDescription("Historial reciente").tap()
        assertEquals(listOf("mic", "history"), asked)
    }

    @Test
    fun `el interruptor de tiempo real avisa su toque`() {
        render()
        compose.onNodeWithContentDescription("Modo en vivo").tap()
        assertEquals(listOf("live"), asked)
    }

    @Test
    fun `dictando el campo dice Dictando y el microfono queda prendido`() {
        render(input = BottomBarInput(query = "", dictationActive = true))
        compose.onNodeWithText("Dictando...").assertExists()
        // Prendido no se deshabilita: es el botón de parar.
        compose.onNodeWithContentDescription("Entrada por voz").assertIsEnabled()
        // Con el micrófono escuchando no se manda ni se abre el historial.
        compose.onNodeWithContentDescription("Traducir").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Historial reciente").assertIsNotEnabled()
        // Y el otro interruptor queda deshabilitado.
        compose.onNodeWithContentDescription("Modo en vivo").assertIsNotEnabled()
    }

    @Test
    fun `en vivo el campo dice Escuchando y el microfono de dictado queda apagado`() {
        render(input = BottomBarInput(query = "", liveActive = true))
        compose.onNodeWithText("Escuchando...").assertExists()
        compose.onNodeWithContentDescription("Entrada por voz").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Modo en vivo").assertIsEnabled()
    }

    @Test
    fun `el aviso del microfono se lee en el panel`() {
        render(availability = BottomBarAvailability(micNotice = "Sin permiso de micrófono"))
        compose.onNodeWithText("Sin permiso de micrófono").assertExists()
    }

    @Test
    fun `sin servicio de voz el microfono se apaga pero el texto sigue`() {
        render(availability = BottomBarAvailability(micAvailable = false))
        compose.onNodeWithContentDescription("Entrada por voz").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Modo en vivo").assertIsNotEnabled()
        compose.onNodeWithText("Escribe una frase...").assertExists()
    }

    @Test
    fun `mientras procesa el panel queda deshabilitado`() {
        render(input = BottomBarInput(query = "hola"), playbackState = PlaybackState.Procesando)
        compose.onNodeWithContentDescription("Traducir").assertIsNotEnabled()
        compose.onNodeWithText("hola").assertExists()
    }

    @Test
    fun `con la frase retenida no se puede reenviar`() {
        render(input = BottomBarInput(query = "hola", sentTextLocked = true))
        compose.onNodeWithContentDescription("Traducir").assertIsNotEnabled()
    }

    @Test
    fun `un panel deshabilitado no dispara nada`() {
        render(
            input = BottomBarInput(query = "hola"),
            availability = BottomBarAvailability(enabled = false),
        )
        compose.onNodeWithContentDescription("Traducir").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Entrada por voz").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Modo en vivo").assertIsNotEnabled()
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `el texto dictado listo para enviar cambia la etiqueta del microfono`() {
        render(input = BottomBarInput(query = "hola", dictationReadyToSend = true))
        compose
            .onNodeWithContentDescription("Entrada por voz")
            .assertExists()
            .tap()
        assertEquals(listOf("mic"), asked)
    }

    @Test
    fun `el campo se puede reemplazar entero mientras se escribe`() {
        render(editable = true)
        compose.onNodeWithText("Escribe una frase...").performTextInput("uno")
        compose.onNodeWithText("uno").performTextReplacement("dos")
        assertEquals(listOf("query:uno", "query:dos"), asked)
    }
}
