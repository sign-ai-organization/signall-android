package com.signall.app.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.signall.app.testing.Themed
import com.signall.app.testing.tap
import com.signall.app.ui.screens.conversation.ConversationPanel
import com.signall.app.ui.state.ConversationTurn
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La conversación de la sesión (drawer izquierdo): un turno por frase enviada,
 * con su glosa y su botón de repetir (`CNV-*`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class ConversationPanelTest {

    @get:Rule
    val compose = createComposeRule()

    private val repeated = mutableListOf<String>()
    private var closed = 0

    private fun render(turns: List<ConversationTurn>, reduceMotion: Boolean = false) {
        compose.setContent {
            Themed(reduceMotion = reduceMotion) {
                ConversationPanel(
                    turns = turns,
                    onRepeat = { repeated += it },
                    onClose = { closed++ },
                )
            }
        }
    }

    private val turns =
        listOf(
            ConversationTurn(id = 1, text = "necesito agua", gloss = "AGUA YO NECESITAR"),
            ConversationTurn(id = 2, text = "buenos días", gloss = "BUENOS DÍAS"),
        )

    @Test
    fun `sin frases muestra el estado vacio`() {
        render(emptyList())
        compose.onNodeWithText("Aún no hay frases en esta conversación.").assertExists()
        compose.onNodeWithText("Conversación").assertExists()
    }

    @Test
    fun `cada turno muestra la frase y su glosa`() {
        render(turns)
        compose.onNodeWithText("necesito agua").assertExists()
        compose.onNodeWithText("AGUA YO NECESITAR").assertExists()
        compose.onNodeWithText("buenos días").assertExists()
        // Un turno por frase: las dos etiquetas aparecen una vez por turno.
        compose.onAllNodesWithText("TÚ").assertCountEquals(2)
        compose.onAllNodesWithText("TRADUCCIÓN").assertCountEquals(2)
    }

    @Test
    fun `el boton de repetir de un turno avisa cual`() {
        render(turns)
        compose.onNodeWithContentDescription("Repetir la seña de buenos días").tap()
        assertEquals(listOf("buenos días"), repeated)
    }

    @Test
    fun `cerrar avisa al host`() {
        render(turns)
        compose.onNodeWithContentDescription("Cerrar").tap()
        assertEquals(1, closed)
    }

    @Test
    fun `con las animaciones reducidas la lista se dibuja igual`() {
        render(turns, reduceMotion = true)
        compose.onNodeWithText("necesito agua").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Repetir la seña de necesito agua").tap()
        assertEquals(listOf("necesito agua"), repeated)
    }
}
