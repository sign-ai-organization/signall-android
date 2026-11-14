package com.signall.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los comandos son el contrato Kotlin → JavaScript (`BRG-8`): un nombre mal
 * escrito no lo detecta ningún compilador del otro lado, así que se fijan acá
 * letra por letra.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SignAllCommandsTest {

    @Test
    fun `play no lleva argumentos y mantiene el guard`() {
        assertEquals("window.SignAll?.play()", SignAllCommands.PLAY)
        assertEquals("window.SignAll?.stop()", SignAllCommands.STOP)
    }

    @Test
    fun `los interruptores van como booleano de JavaScript`() {
        assertEquals("window.SignAll?.setGrid(!!true)", SignAllCommands.setGrid(true))
        assertEquals("window.SignAll?.setBackground(!!false)", SignAllCommands.setBackground(false))
        assertEquals("window.SignAll?.setFreeCamera(!!true)", SignAllCommands.setFreeCamera(true))
        assertEquals("window.SignAll?.setTheme(!!true)", SignAllCommands.setTheme(true))
        assertEquals("window.SignAll?.setDebug(!!false)", SignAllCommands.setDebug(false))
        assertEquals("window.SignAll?.setReduceMotion(!!true)", SignAllCommands.setReduceMotion(true))
        assertEquals("window.SignAll?.toggle(!!false)", SignAllCommands.jsToggle("toggle", false))
    }

    @Test
    fun `la velocidad se recorta a 0 y 1`() {
        assertEquals("window.SignAll?.setSpeed(0.5)", SignAllCommands.setSpeed(0.5f))
        assertEquals("window.SignAll?.setSpeed(0.0)", SignAllCommands.setSpeed(-3f))
        assertEquals("window.SignAll?.setSpeed(1.0)", SignAllCommands.setSpeed(4f))
    }

    @Test
    fun `el silencio del stt viaja como numero o como null`() {
        assertEquals("window.SignAll?.setSilenceTimeoutMs(1500)", SignAllCommands.setSilenceTimeoutMs(1500))
        assertEquals("window.SignAll?.setSilenceTimeoutMs(null)", SignAllCommands.setSilenceTimeoutMs(null))
    }

    @Test
    fun `la cola se cita entera para que el JS la parsee`() {
        val json = """[{"gloss":"YO"}]"""
        assertEquals(
            """window.SignAll?.setQueue("[{\"gloss\":\"YO\"}]")""",
            SignAllCommands.setQueue(json),
        )
        assertEquals(
            """window.SignAll?.appendQueue("[{\"gloss\":\"YO\"}]")""",
            SignAllCommands.appendQueue(json),
        )
    }

    @Test
    fun `una comilla en la glosa no puede romper el script`() {
        val json = """[{"gloss":"\"comillas\""}]"""
        val command = SignAllCommands.setQueue(json)
        assertTrue(command.startsWith("window.SignAll?.setQueue(\""))
        // Las comillas quedan escapadas dentro del literal de JavaScript.
        assertTrue(command.contains("\\\""))
    }

    @Test
    fun `playFrom nunca pide un indice negativo`() {
        assertEquals("window.SignAll?.playFrom(3)", SignAllCommands.playFrom(3))
        assertEquals("window.SignAll?.playFrom(0)", SignAllCommands.playFrom(-1))
    }

    @Test
    fun `la sena y el avatar viajan citados`() {
        assertEquals(
            """window.SignAll?.setDefaultSign("signs\/0007_0000_0000.glb")""",
            SignAllCommands.setDefaultSign("signs/0007_0000_0000.glb"),
        )
        assertEquals("""window.SignAll?.setAvatar("pauline")""", SignAllCommands.setAvatar("pauline"))
    }
}
