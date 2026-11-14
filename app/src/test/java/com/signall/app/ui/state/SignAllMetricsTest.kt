package com.signall.app.ui.state

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Las métricas del dispositivo: son las que se comparan con `/metrics` del
 * backend, así que el resumen tiene que decir la verdad y aguantar una sesión
 * larga sin crecer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SignAllMetricsTest {

    @Test
    fun `un turno y una representacion entran en el resumen`() {
        val metrics = SignAllMetrics()
        metrics.onTurn(totalMs = 800, sentenceCount = 2, glossCount = 5)
        metrics.onPlayback(totalMs = 1_200, signCount = 5)
        metrics.onSignStart(segmentToMoveMs = 90)
        metrics.onFirstSign(totalMs = 650)
        metrics.onUnavailableGloss()
        metrics.onDictationUtterance("necesito agua  ya")

        val snapshot = metrics.snapshot()
        assertTrue(snapshot, snapshot.contains("frases=1"))
        assertTrue(snapshot, snapshot.contains("oraciones=2"))
        assertTrue(snapshot, snapshot.contains("glosas=5"))
        assertTrue(snapshot, snapshot.contains("sin_seña=1"))
        assertTrue(snapshot, snapshot.contains("dictado_frases=1"))
        assertTrue(snapshot, snapshot.contains("dictado_palabras=3"))
    }

    @Test
    fun `sin muestras el resumen dice n a`() {
        assertTrue(SignAllMetrics().snapshot().contains("envio_a_primera_sena=n/a"))
    }

    @Test
    fun `reiniciar deja los contadores en cero`() {
        val metrics = SignAllMetrics()
        repeat(3) { metrics.onTurn(100, 1, 1) }
        metrics.reset()
        val snapshot = metrics.snapshot()
        assertTrue(snapshot, snapshot.contains("frases=0"))
        assertTrue(snapshot, snapshot.contains("oraciones=0"))
        assertTrue(snapshot, snapshot.contains("duracion_frase=n/a"))
    }

    @Test
    fun `mas de 256 muestras no hacen crecer el resumen`() {
        val metrics = SignAllMetrics()
        repeat(300) { metrics.onTurn(it.toLong(), 1, 1) }
        val snapshot = metrics.snapshot()
        // La ventana es un anillo: el resumen siempre dice n=256.
        assertTrue(snapshot, snapshot.contains("n=256"))
    }

    @Test
    fun `el dictado cuenta palabras y no frases`() {
        val metrics = SignAllMetrics()
        metrics.onDictationUtterance("hola")
        metrics.onDictationUtterance("")
        val snapshot = metrics.snapshot()
        assertTrue(snapshot, snapshot.contains("dictado_frases=2"))
        assertTrue(snapshot, snapshot.contains("dictado_palabras=1"))
    }
}
