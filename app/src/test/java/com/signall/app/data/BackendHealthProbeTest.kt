package com.signall.app.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El probe es el que separa "puerto abierto" de "backend de SignAll". Contra un
 * servidor HTTP real se comprueba que solo la respuesta propia pasa, y que un
 * error o un JSON ajeno se descartan sin lanzar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackendHealthProbeTest {

    private lateinit var server: MockWebServer
    private val probe = HttpBackendHealthProbe()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `el health propio se acepta`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"status":"ok","cache_loaded":true,"cache_rows":3,"catalog_loaded":true,"catalog_glosses":50}""",
            ),
        )

        assertTrue(probe.isHealthy(server.hostName, server.port, secure = false))
        assertEquals("/health", server.takeRequest().path)
    }

    @Test
    fun `un 200 que no es de signall se rechaza`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"status":"ok","service":"router"}"""))

        assertFalse(probe.isHealthy(server.hostName, server.port, secure = false))
    }

    @Test
    fun `un backend degradado se rechaza`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"status":"degraded","cache_loaded":true,"catalog_loaded":false,"catalog_glosses":0}""",
            ),
        )

        assertFalse(probe.isHealthy(server.hostName, server.port, secure = false))
    }

    @Test
    fun `un error http o un cuerpo basura se rechazan`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        assertFalse(probe.isHealthy(server.hostName, server.port, secure = false))

        server.enqueue(MockResponse().setBody("no es json"))
        assertFalse(probe.isHealthy(server.hostName, server.port, secure = false))
    }

    @Test
    fun `un puerto cerrado no se confunde con un backend`() = runBlocking {
        val port = server.port
        server.shutdown()

        assertFalse(probe.isHealthy("127.0.0.1", port, secure = false))
    }
}
