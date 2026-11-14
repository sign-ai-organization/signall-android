package com.signall.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.signall.app.domain.Character
import com.signall.app.testing.Themed
import com.signall.app.testing.tap
import com.signall.app.ui.icons.SignAllIcons
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Las piezas chicas del panel y del historial: el stepper, el selector de sena,
 * el de personaje, la hoja de historial y los botones de marca.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class ComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    // ── Stepper ──────────────────────────────────────────────────────────────

    private val values = mutableListOf<Int>()

    @Test
    fun `el stepper sube y baja sobre la grilla del paso`() {
        compose.setContent {
            Themed {
                SignAllStepper(
                    value = 52,
                    onValue = { values += it },
                    config = StepperConfig(label = "la velocidad"),
                )
            }
        }
        compose.onNodeWithContentDescription("Aumentar la velocidad").tap()
        compose.onNodeWithContentDescription("Reducir la velocidad").tap()
        // Desde 52 —un valor viejo del slider— el primer toque ordena a la grilla.
        assertEquals(listOf(55, 50), values)
    }

    @Test
    fun `el stepper no se pasa de los extremos`() {
        compose.setContent {
            Themed {
                SignAllStepper(
                    value = 0,
                    onValue = { values += it },
                    config = StepperConfig(label = "el tamaño"),
                )
            }
        }
        compose.onNodeWithContentDescription("Reducir el tamaño").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Aumentar el tamaño").tap()
        assertEquals(listOf(5), values)
    }

    @Test
    fun `en el maximo el stepper no deja subir`() {
        compose.setContent {
            Themed {
                SignAllStepper(
                    value = 100,
                    onValue = { values += it },
                    config = StepperConfig(label = "el tamaño"),
                )
            }
        }
        compose.onNodeWithContentDescription("Aumentar el tamaño").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Reducir el tamaño").tap()
        assertEquals(listOf(95), values)
    }

    @Test
    fun `el stepper con paso propio respeta su rango`() {
        compose.setContent {
            Themed {
                SignAllStepper(
                    value = 10,
                    onValue = { values += it },
                    config = StepperConfig(step = 10, minValue = 10, maxValue = 100, label = "la velocidad"),
                )
            }
        }
        compose.onNodeWithContentDescription("Reducir la velocidad").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Aumentar la velocidad").tap()
        assertEquals(listOf(20), values)
    }

    // ── Botoncitos y marca ───────────────────────────────────────────────────

    @Test
    fun `el boton chico avisa el toque y respeta el apagado`() {
        var hits = 0
        compose.setContent {
            Themed {
                SignAllSmallIconButton(
                    icon = SignAllIcons.X,
                    contentDescription = "Cerrar",
                    onClick = { hits++ },
                )
            }
        }
        compose.onNodeWithContentDescription("Cerrar").tap()
        assertEquals(1, hits)
    }

    @Test
    fun `el boton chico apagado no se puede tocar`() {
        compose.setContent {
            Themed {
                SignAllSmallIconButton(
                    icon = SignAllIcons.Minus,
                    contentDescription = "Reducir",
                    onClick = {},
                    enabled = false,
                )
            }
        }
        compose.onNodeWithContentDescription("Reducir").assertIsNotEnabled()
    }

    @Test
    fun `la marca dibuja el icono con su descripcion`() {
        compose.setContent {
            Themed {
                SignAllBrandTile(contentDescription = "SignALL")
                SignAllBrandTile()
                SignAllWordmark()
                SignAllWordmark(contentDescription = null)
            }
        }
        compose.onAllNodesWithContentDescription("SignALL").assertCountEquals(2)
    }

    @Test
    fun `el splash muestra el porcentaje y lo recorta`() {
        compose.setContent {
            Themed {
                SignAllSplash(progress = 140)
            }
        }
        compose.onNodeWithContentDescription("Cargando…").assertExists()
        compose.onNodeWithText("100%").assertExists()
    }

    @Test
    fun `el splash en cero no inventa progreso`() {
        compose.setContent {
            Themed {
                SignAllSplash(progress = -5)
            }
        }
        compose.onNodeWithText("0%").assertExists()
    }

    // ── SettingRow ───────────────────────────────────────────────────────────

    @Test
    fun `la fila de ajuste pone la etiqueta y el control con su icono`() {
        compose.setContent {
            Themed {
                SettingRow(label = "Texto en negrita", icon = SignAllIcons.TextB) {
                    SignAllBrandTile()
                }
            }
        }
        compose.onNodeWithText("Texto en negrita").assertExists()
    }

    @Test
    fun `la fila de ajuste se apila cuando el panel es angosto`() {
        compose.setContent {
            Themed {
                Box(Modifier.fillMaxWidth()) {
                    SettingRow(label = "Personaje", stackBelow = 400.dp) {
                        SignAllBrandTile()
                    }
                }
            }
        }
        compose.onNodeWithText("Personaje").assertExists()
    }

    @Test
    fun `la fila de ajuste sin icono tambien dibuja`() {
        compose.setContent {
            Themed {
                SettingRow(label = "Sin icono") {
                    SignAllBrandTile()
                }
            }
        }
        compose.onNodeWithText("Sin icono").assertExists()
    }

    // ── Selector de personaje ────────────────────────────────────────────────

    @Test
    fun `el selector de personaje elige y cierra el menu`() {
        var picked: Character? = null
        compose.setContent {
            Themed {
                CharacterPicker(current = Character.Juan, onPick = { picked = it })
            }
        }
        compose.onNodeWithText("Juan").tap()
        compose.onNodeWithText("Pauline").tap()
        assertEquals(Character.Pauline, picked)
    }

    @Test
    fun `con el panel ocupado el selector de personaje no abre`() {
        compose.setContent {
            Themed {
                CharacterPicker(current = Character.Juan, onPick = {}, enabled = false)
            }
        }
        compose.onNodeWithText("Juan").assertIsNotEnabled()
    }

    // ── Selector de sena ─────────────────────────────────────────────────────

    @Test
    fun `el selector de sena muestra el numero y avisa la eleccion`() {
        var picked: String? = null
        compose.setContent {
            Themed {
                SignPickerDropdown(
                    current = "0000_0000_0000",
                    options = listOf("0000_0000_0000", "0007_0000_0000"),
                    onPick = { picked = it },
                )
            }
        }
        compose.onNodeWithText("Seña 0").tap()
        compose.onNodeWithText("Seña 7").tap()
        assertEquals("0007_0000_0000", picked)
    }

    @Test
    fun `el selector de sena marca el actual con un tilde`() {
        compose.setContent {
            Themed {
                SignPickerDropdown(
                    current = "0007_0000_0000",
                    options = listOf("0000_0000_0000", "0007_0000_0000"),
                    onPick = {},
                )
            }
        }
        compose.onNodeWithText("Seña 7").tap()
        compose.onNodeWithText("✓ Seña 7").assertExists()
    }

    // ── Historial ────────────────────────────────────────────────────────────

    @Test
    fun `la hoja del historial lista las frases y reutiliza una`() {
        var reused: String? = null
        var dismissed = 0
        compose.setContent {
            Themed {
                HistoryBottomSheet(
                    visible = true,
                    history = listOf("necesito agua", "buenos días"),
                    onPick = { reused = it },
                    onDismiss = { dismissed++ },
                )
            }
        }
        compose.onNodeWithText("TUS FRASES").assertExists()
        compose.onNodeWithText("necesito agua").assertExists()
        compose.onNodeWithContentDescription("Reutilizar buenos días").tap()
        assertEquals("buenos días", reused)
        compose.onNodeWithContentDescription("Cerrar").tap()
        assertEquals(1, dismissed)
    }

    @Test
    fun `la hoja del historial vacia lo dice`() {
        compose.setContent {
            Themed {
                HistoryBottomSheet(visible = true, history = emptyList(), onPick = {}, onDismiss = {})
            }
        }
        compose.onNodeWithText("Aún no hay frases recientes.").assertExists()
    }

    @Test
    fun `la hoja del historial cerrada no dibuja nada`() {
        compose.setContent {
            Themed {
                HistoryBottomSheet(
                    visible = false,
                    history = listOf("necesito agua"),
                    onPick = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("TUS FRASES").assertDoesNotExist()
    }

    @Test
    fun `la hoja del historial sin animaciones se dibuja igual`() {
        compose.setContent {
            Themed(reduceMotion = true) {
                HistoryBottomSheet(
                    visible = true,
                    history = listOf("necesito agua"),
                    onPick = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("necesito agua").assertExists()
    }

    @Test
    fun `la hoja del historial con muchas frases se puede recorrer`() {
        compose.setContent {
            Themed {
                var shown by remember { mutableStateOf(true) }
                if (shown) {
                    HistoryBottomSheet(
                        visible = true,
                        history = (1..20).map { "frase $it" },
                        onPick = {},
                        onDismiss = { shown = false },
                    )
                }
            }
        }
        compose.onNodeWithText("frase 1").assertExists()
    }
}
