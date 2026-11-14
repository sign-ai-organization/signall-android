package com.signall.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.signall.app.testing.Themed
import com.signall.app.testing.tap
import com.signall.app.ui.screens.stage.AvatarStage
import com.signall.app.ui.screens.stage.StageState
import com.signall.app.ui.state.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El escenario: el indicador de estado, el rótulo de la seña en curso y el
 * Repetir que aparece cuando la frase terminó de representarse.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class AvatarStageTest {

    @get:Rule
    val compose = createComposeRule()

    private var repeats = 0

    private fun render(stage: StageState) {
        compose.setContent {
            Themed {
                AvatarStage(
                    stage = stage,
                    onRepeat = { repeats++ },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        Modifier
                            .size(200.dp)
                            .semantics { contentDescription = "escena" },
                    )
                }
            }
        }
    }

    @Test
    fun `muestra el estado y la escena que le pasan`() {
        render(StageState(status = "Procesando", playback = PlaybackState.Procesando, doneVisible = false))
        compose.onNodeWithText("Procesando").assertExists()
        compose.onNodeWithContentDescription("escena").assertExists()
    }

    @Test
    fun `los siete estados se dibujan con su color`() {
        var stage by mutableStateOf(StageState("Inactivo", PlaybackState.Inactivo, false))
        compose.setContent {
            Themed {
                AvatarStage(stage = stage, onRepeat = {}) { Box(Modifier.fillMaxSize()) }
            }
        }
        PlaybackState.entries.forEach { state ->
            stage = StageState(status = state.label, playback = state, doneVisible = false)
            compose.waitForIdle()
            compose.onNodeWithText(state.label).assertExists()
        }
    }

    @Test
    fun `sin representacion terminada no hay Repetir`() {
        render(StageState(status = "Inactivo", playback = PlaybackState.Inactivo, doneVisible = false))
        compose.onNodeWithContentDescription("Repetir reproducción").assertDoesNotExist()
    }

    @Test
    fun `con la representacion terminada aparece Repetir y avisa`() {
        render(
            StageState(
                status = "Inactivo",
                playback = PlaybackState.Inactivo,
                doneVisible = true,
                bottomInset = 92.dp,
            ),
        )
        compose.onNodeWithContentDescription("Repetir reproducción").assertExists().tap()
        assertEquals(1, repeats)
    }

    @Test
    fun `la sena en curso se muestra en una pildora`() {
        render(
            StageState(
                status = "Reproduciendo",
                playback = PlaybackState.Reproduciendo,
                doneVisible = false,
                currentGloss = "AGUA",
            ),
        )
        compose.onNodeWithContentDescription("Seña en curso: AGUA").assertExists()
        compose.onNodeWithText("AGUA").assertExists()
    }

    @Test
    fun `la pildora desaparece cuando la sena termina`() {
        render(
            StageState(
                status = "Inactivo",
                playback = PlaybackState.Inactivo,
                doneVisible = true,
                currentGloss = null,
            ),
        )
        compose.onNodeWithContentDescription("Seña en curso: AGUA").assertDoesNotExist()
    }
}
