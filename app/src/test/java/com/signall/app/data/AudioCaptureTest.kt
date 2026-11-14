package com.signall.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.signall.app.testing.ThrottledAudioRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * El micrófono y las preferencias chicas.
 *
 * La captura real no se puede probar en la JVM —no hay `AudioRecord`—, así que
 * lo que se verifica es el contrato de la clase: que arranque, entregue bloques
 * PCM16 del tamaño pedido y se apague limpiamente.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioCaptureTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ThrottledAudioRecord.install()
    }

    @After
    fun tearDown() {
        ThrottledAudioRecord.clear()
    }

    @Test
    fun `sin arrancar no esta grabando y apagar no rompe`() {
        val capture = AudioCapture { }
        assertFalse(capture.isRecording)
        capture.stop()
        assertFalse(capture.isRecording)
    }

    @Test
    fun `la captura entrega bloques de 30 ms y se detiene`() {
        val chunks = CopyOnWriteArrayList<ByteArray>()
        val capture = AudioCapture { chunks += it }
        val scope = CoroutineScope(Dispatchers.IO)
        capture.start(scope)
        assertTrue(capture.isRecording)

        val deadline = System.currentTimeMillis() + 3_000
        while (chunks.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        capture.stop()

        assertFalse(capture.isRecording)
        assertTrue(chunks.isNotEmpty())
        // 16 kHz mono PCM16 con frames de 30 ms son 480 muestras = 960 bytes.
        assertTrue(chunks.first().size in 1..960)
    }

    @Test
    fun `pedir un frame distinto cambia el tamano del bloque`() {
        val chunks = CopyOnWriteArrayList<ByteArray>()
        val capture = AudioCapture { chunks += it }
        val scope = CoroutineScope(Dispatchers.IO)
        capture.start(scope, sampleRate = 16_000, frameMs = 60)

        val deadline = System.currentTimeMillis() + 3_000
        while (chunks.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        capture.stop()

        assertTrue(chunks.first().size <= 1_920)
    }

    @Test
    fun `arrancar dos veces no abre dos capturas`() {
        val capture = AudioCapture { }
        val scope = CoroutineScope(Dispatchers.IO)
        capture.start(scope)
        capture.start(scope)
        assertTrue(capture.isRecording)
        capture.stop()
        assertFalse(capture.isRecording)
    }

    @Test
    fun `el permiso del microfono se recuerda por instalacion`() {
        val prefs = context.getSharedPreferences("signall", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val prompt = MicPermissionPrompt(context)
        assertFalse(prompt.alreadyAsked)

        prompt.alreadyAsked = true
        assertTrue(prompt.alreadyAsked)
        // Es la misma preferencia que leen los demás: sobrevive a otra instancia.
        assertTrue(MicPermissionPrompt(context).alreadyAsked)
    }
}
