package com.signall.app.data

import com.signall.app.domain.BackendEvent
import com.signall.app.domain.PhraseType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * El contrato con el backend en el lado del cliente: cada frame se convierte en
 * un evento, lo que no se entiende se descarta sin tumbar la sesión, y una
 * sesión que se cierra se puede volver a abrir.
 *
 * El servidor es `MockWebServer`, que sabe hacer el saludo de WebSocket, así que
 * se prueba el cliente real contra un socket real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SignAllWebSocketClientTest {

    private fun serverWith(listener: WebSocketListener): MockWebServer {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(listener))
        server.start()
        return server
    }

    private fun clientFor(server: MockWebServer) =
        SignAllWebSocketClient(
            SignAllBackendConfig("ws://${server.hostName}:${server.port}/ws/translate"),
        )

    /**
     * Junta eventos hasta [expected] o hasta que se acabe el tiempo, y devuelve
     * lo que haya. El flujo de eventos no "termina" por sí solo —vive mientras
     * viva la sesión—, así que se lo corta desde afuera.
     */
    private fun collect(
        client: SignAllWebSocketClient,
        expected: Int,
        timeoutMs: Long = 8_000,
    ): List<BackendEvent> {
        val events = CopyOnWriteArrayList<BackendEvent>()
        val scope = CoroutineScope(Dispatchers.IO)
        val job = scope.launch { client.events().collect { events += it } }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (events.size < expected && System.currentTimeMillis() < deadline) Thread.sleep(10)
        job.cancel()
        return events.toList()
    }

    private fun awaitConnected(client: SignAllWebSocketClient, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!client.isConnected && System.currentTimeMillis() < deadline) Thread.sleep(10)
    }

    private fun shutdown(server: MockWebServer) {
        runCatching { server.shutdown() }
    }

    @Test
    fun `los frames del backend se convierten en eventos`() {
        val server =
            serverWith(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send("""{"type":"capability","audio":false,"reason":"stt"}""")
                        webSocket.send("""{"type":"transcript","text":"hola","final":true}""")
                        webSocket.send(
                            """{"type":"segment","index":0,"total":2,"turn":3,"segment":""" +
                                """{"type":"YES_NO_QUESTION","glosses":[""" +
                                """{"text":"YO","available":true,"glb_path":"signs/0000_0000_0000.glb"},""" +
                                """{"text":"AGUA","available":false,"glb_path":""}]}}""",
                        )
                        webSocket.send("""{"type":"done","turn":3}""")
                        webSocket.send("""{"type":"error","message":"boom","turn":3}""")
                        webSocket.send("""{"type":"error"}""")
                        // Un tipo que el cliente no conoce y una basura: se ignoran.
                        webSocket.send("""{"type":"trace","ms":1}""")
                        webSocket.send("no es json")
                        webSocket.send("[1,2]")
                    }
                },
            )
        val client = clientFor(server)
        val events = collect(client, expected = 7)
        client.close()
        shutdown(server)

        assertEquals(BackendEvent.Connected, events[0])
        assertEquals(BackendEvent.Capabilities(audio = false, reason = "stt"), events[1])
        assertEquals(BackendEvent.Transcript(text = "hola", final = true), events[2])
        val segment = events[3] as BackendEvent.Segment
        assertEquals(0, segment.index)
        assertEquals(2, segment.total)
        assertEquals(3, segment.turn)
        assertEquals(PhraseType.YES_NO_QUESTION, segment.segment.type)
        assertEquals("YO", segment.segment.glosses[0].text)
        assertTrue(segment.segment.glosses[0].available)
        assertFalse(segment.segment.glosses[1].available)
        assertEquals(BackendEvent.PhraseDone(turn = 3), events[4])
        assertEquals("boom", (events[5] as BackendEvent.Failure).message)
        // Sin mensaje, el error usa el texto por defecto y no rompe.
        assertTrue((events[6] as BackendEvent.Failure).message.isNotBlank())
    }

    @Test
    fun `un arreglo sin sobre es el formato viejo y sigue funcionando`() {
        val server =
            serverWith(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(
                            """[{"type":"AFFIRMATIVE","glosses":[""" +
                                """{"text":"HOLA","available":true,"glb_path":"signs/0001_0000_0000.glb"}]},""" +
                                """{"type":"NEGATIVE","glosses":[]}]""",
                        )
                    }
                },
            )
        val client = clientFor(server)
        val events = collect(client, expected = 3)
        client.close()
        shutdown(server)

        val segments = events.filterIsInstance<BackendEvent.Segment>()
        assertEquals(2, segments.size)
        assertEquals(0, segments[0].index)
        assertEquals(2, segments[0].total)
        assertNull(segments[0].turn)
        assertEquals(PhraseType.NEGATIVE, segments[1].segment.type)
        assertEquals("HOLA", segments[0].segment.glosses.single().text)
    }

    @Test
    fun `la frase y el audio viajan por el mismo socket`() {
        val received = LinkedBlockingQueue<String>()
        val opened = CountDownLatch(1)
        val server =
            serverWith(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        opened.countDown()
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        received.add(text)
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                        received.add("binario:${bytes.size}")
                    }
                },
            )
        val client = clientFor(server)
        val scope = CoroutineScope(Dispatchers.IO)
        val job = scope.launch { client.events().collect { } }
        awaitConnected(client)
        assertTrue(opened.await(5, TimeUnit.SECONDS))

        assertEquals(1, client.sendText("hola"))
        assertEquals(2, client.sendText("otra"))
        assertTrue(client.sendAudio(ByteArray(4)))
        assertTrue(client.sendEnd())
        // Se esperan los cuatro frames **antes** de cerrar: el cierre viaja por
        // la misma cola del socket y no se puede adivinar el orden.
        val deadline = System.currentTimeMillis() + 5_000
        while (received.size < 4 && System.currentTimeMillis() < deadline) Thread.sleep(10)
        job.cancel()
        client.close()
        shutdown(server)

        val first = received.poll(5, TimeUnit.SECONDS).orEmpty()
        assertTrue(first, first.contains("\"type\":\"text\""))
        assertTrue(first, first.contains("\"turn\":1"))
        assertTrue(received.poll(5, TimeUnit.SECONDS).orEmpty().contains("\"turn\":2"))
        assertEquals("binario:4", received.poll(5, TimeUnit.SECONDS))
        assertEquals("""{"type":"end"}""", received.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun `sin sesion no se puede mandar nada`() {
        val client = SignAllWebSocketClient(SignAllBackendConfig("ws://127.0.0.1:1/ws/translate"))
        assertFalse(client.isConnected)
        assertNull(client.sendText("hola"))
        assertFalse(client.sendAudio(ByteArray(2)))
        assertFalse(client.sendEnd())
    }

    @Test
    fun `probeVoice lee la capacidad de un socket aparte`() {
        val server =
            serverWith(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send("""{"type":"capability","audio":true}""")
                    }
                },
            )
        val client = clientFor(server)
        val voice = kotlinx.coroutines.runBlocking { client.probeVoice() }
        client.close()
        shutdown(server)
        assertEquals(true, voice)
    }

    @Test
    fun `probeVoice devuelve null si el servidor no contesta`() {
        val server = serverWith(object : WebSocketListener() {})
        val client = clientFor(server)
        val voice = kotlinx.coroutines.runBlocking { client.probeVoice() }
        client.close()
        shutdown(server)
        assertNull(voice)
    }

    @Test
    fun `cerrar la sesion suelta el socket`() {
        val server = serverWith(object : WebSocketListener() {})
        val client = clientFor(server)
        val scope = CoroutineScope(Dispatchers.IO)
        val job = scope.launch { client.events().collect { } }
        awaitConnected(client)
        assertTrue(client.isConnected)
        client.close()
        assertFalse(client.isConnected)
        job.cancel()
        shutdown(server)
    }
}
