package com.signall.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Aceptar el backend equivocado es peor que no encontrar ninguno: el socket se
 * abriría contra un router o una impresora y la app diría "conectada" sin poder
 * traducir. Estas pruebas fijan qué cuenta como "el `/health` de SignAll".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SignAllHealthTest {

    private val valid =
        """
        {"status":"ok","cache_loaded":true,"cache_rows":17,"catalog_loaded":true,
         "catalog_glosses":50,"silence_timeout_ms":1500}
        """.trimIndent()

    @Test
    fun `el health completo de signall es sano y se lee`() {
        val health = SignAllHealthParser.parse(valid)

        assertEquals("ok", health?.status)
        assertTrue(health?.cacheLoaded == true)
        assertEquals(17, health?.cacheRows)
        assertTrue(health?.catalogLoaded == true)
        assertEquals(50, health?.catalogGlosses)
        assertTrue(health?.isHealthy == true)
    }

    @Test
    fun `los campos extra del stt no estorban`() {
        assertTrue(SignAllHealthParser.parse(valid)?.isHealthy == true)
    }

    @Test
    fun `un backend degradado responde pero no se acepta`() {
        val health =
            SignAllHealthParser.parse(
                """{"status":"degraded","cache_loaded":true,"catalog_loaded":false,"catalog_glosses":0}""",
            )

        assertEquals("degraded", health?.status)
        assertFalse(health?.isHealthy == true)
    }

    @Test
    fun `un ok sin catalogo no es un backend util`() {
        assertFalse(SignAllHealthParser.parse(valid.replace("\"catalog_glosses\":50", "\"catalog_glosses\":0"))?.isHealthy == true)
    }

    @Test
    fun `un json ajeno con status ok no pasa`() {
        assertNull(SignAllHealthParser.parse("""{"status":"ok","uptime":123}"""))
        assertNull(SignAllHealthParser.parse("""{"status":"ok","cache_loaded":true}"""))
        assertNull(SignAllHealthParser.parse("""{"catalog_loaded":true,"catalog_glosses":50}"""))
    }

    @Test
    fun `un tipo equivocado en un campo propio no se adivina`() {
        assertNull(
            SignAllHealthParser.parse(
                """{"status":"ok","cache_loaded":"si","catalog_loaded":true,"catalog_glosses":50}""",
            ),
        )
        assertNull(
            SignAllHealthParser.parse(
                """{"status":200,"cache_loaded":true,"catalog_loaded":true,"catalog_glosses":50}""",
            ),
        )
    }

    @Test
    fun `basura, arreglos y vacio devuelven null`() {
        assertNull(SignAllHealthParser.parse(null))
        assertNull(SignAllHealthParser.parse(""))
        assertNull(SignAllHealthParser.parse("no es json"))
        assertNull(SignAllHealthParser.parse("[]"))
    }

    @Test
    fun `sin cache_rows se asume cero, pero no es obligatorio`() {
        val health =
            SignAllHealthParser.parse(
                """{"status":"ok","cache_loaded":true,"catalog_loaded":true,"catalog_glosses":50}""",
            )

        assertEquals(0, health?.cacheRows)
        assertTrue(health?.isHealthy == true)
    }
}
