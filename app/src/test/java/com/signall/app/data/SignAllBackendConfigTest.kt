package com.signall.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La URL del backend se hornea en el APK y de ella dependen el socket y la
 * descarga de assets. Si el origen HTTP se calcula mal, las señas que no vienen
 * en el avatar fallan sin que nada lo diga.
 */
class SignAllBackendConfigTest {

    @Test
    fun `un socket en claro no es cifrado`() {
        assertFalse(SignAllBackendConfig("ws://10.0.2.2:8080/ws/translate").isEncrypted)
    }

    @Test
    fun `wss es cifrado`() {
        assertTrue(SignAllBackendConfig("wss://backend.ejemplo/ws/translate").isEncrypted)
    }

    @Test
    fun `el origen HTTP cambia solo el esquema y conserva host y puerto`() {
        val config = SignAllBackendConfig("ws://10.0.2.2:8080/ws/translate")
        assertEquals("http://10.0.2.2:8080", config.httpOrigin)
    }

    @Test
    fun `el origen HTTPS sale de un socket cifrado`() {
        val config = SignAllBackendConfig("wss://127.0.0.1:8443/ws/translate")
        assertEquals("https://127.0.0.1:8443", config.httpOrigin)
    }

    @Test
    fun `el origen no arrastra la ruta del socket`() {
        val config = SignAllBackendConfig("ws://192.168.1.10:8080/ws/translate?turn=3")
        assertEquals("http://192.168.1.10:8080", config.httpOrigin)
    }

    @Test
    fun `el default del emulador usa el alias del host`() {
        assertEquals("ws://10.0.2.2:8080/ws/translate", SignAllBackendConfig.DEFAULT)
        assertEquals("http://10.0.2.2:8080", SignAllBackendConfig(SignAllBackendConfig.DEFAULT).httpOrigin)
    }
}
