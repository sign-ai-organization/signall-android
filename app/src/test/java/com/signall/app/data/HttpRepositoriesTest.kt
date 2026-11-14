package com.signall.app.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Los dos pedidos HTTP del cliente, que no son del socket: la descarga de la
 * animación de una glosa y la lectura del `/health` del backend.
 *
 * `MockWebServer` hace de backend en loopback, así que se prueba el cliente real
 * contra HTTP real sin depender de nada externo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HttpRepositoriesTest {

    private lateinit var cacheDir: File
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        cacheDir = File(System.getProperty("java.io.tmpdir"), "signall-test-${System.nanoTime()}")
        cacheDir.mkdirs()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
        cacheDir.deleteRecursively()
    }

    private val origin: String get() = "http://${server.hostName}:${server.port}"

    // ── Nombres de archivo en caché ──────────────────────────────────────────

    @Test
    fun `el nombre del archivo lleva la glosa y una huella del path`() {
        val first = SignAssetRepository.fileNameFor("yo", "signs/0001_0000_0000.glb")
        val same = SignAssetRepository.fileNameFor("yo", "signs/0001_0000_0000.glb")
        val other = SignAssetRepository.fileNameFor("yo", "signs/0002_0000_0000.glb")
        assertEquals(first, same)
        assertFalse(first == other)
        assertTrue(first, first.startsWith("YO_"))
        assertTrue(first, first.endsWith(".glb"))
    }

    @Test
    fun `la glosa se sanea para ser un nombre de archivo`() {
        val name = SignAssetRepository.fileNameFor("necesito agua/tú", "anim.json")
        // El acento y la barra no están en el alfabeto permitido: se reemplazan.
        assertTrue(name, name.startsWith("NECESITO_AGUA_T__"))
        assertFalse(name.contains(" "))
        assertFalse(name.contains("/"))
    }

    @Test
    fun `la extension sale del path y sin glb es json`() {
        assertEquals(".glb", SignAssetRepository.extensionFor("signs/0001_0000_0000.GLB"))
        assertEquals(".json", SignAssetRepository.extensionFor("landmarks/yo.json"))
    }

    // ── Descarga de animaciones ──────────────────────────────────────────────

    @Test
    fun `una glosa sin asset no se resuelve`() = runBlocking {
        val repo = SignAssetRepository(cacheDir, origin)
        assertNull(repo.resolve("yo", ""))
        assertNull(repo.cachedUrl("yo", "signs/0001_0000_0000.glb"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `una glosa se descarga una vez y despues sale de la cache`() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"frames\":[]}"))
        val repo = SignAssetRepository(cacheDir, origin)

        val url = repo.resolve("yo", "anims/yo.glb")
        assertNotNull(url)
        assertTrue(url!!, url.startsWith(SignAssetRepository.SIGNS_PATH_PREFIX))
        assertEquals(url, repo.cachedUrl("yo", "anims/yo.glb"))
        assertEquals(1, server.requestCount)
        assertEquals("/anims/yo.glb", server.takeRequest().path)

        // La segunda vez no vuelve a pedir: sale de la cache.
        assertEquals(url, repo.resolve("yo", "anims/yo.glb"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `con el servidor caido la glosa ya descargada sigue resolviendose`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        val repo = SignAssetRepository(cacheDir, origin)
        val url = repo.resolve("yo", "anims/yo.glb")
        server.shutdown()
        assertEquals(url, repo.resolve("yo", "anims/yo.glb"))
    }

    @Test
    fun `un 404 deja la glosa sin resolver`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val repo = SignAssetRepository(cacheDir, origin)
        assertNull(repo.resolve("yo", "anims/falta.glb"))
    }

    @Test
    fun `un origen mal escrito degrada a sin asset`() = runBlocking {
        val repo = SignAssetRepository(cacheDir, "no es una url")
        assertNull(repo.resolve("yo", "anims/yo.glb"))
    }

    // ── /health ──────────────────────────────────────────────────────────────

    @Test
    fun `el silencio del stt se lee del health`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"silence_timeout_ms":1500,"stt":"ok"}"""))
        assertEquals(1_500, BackendHealth(origin).silenceTimeoutMs())
        assertEquals("/health", server.takeRequest().path)
    }

    @Test
    fun `un health sin el dato no inventa un silencio`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"stt":"ok"}"""))
        assertNull(BackendHealth(origin).silenceTimeoutMs())
    }

    @Test
    fun `un health en cero tampoco es un silencio`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"silence_timeout_ms":0}"""))
        assertNull(BackendHealth(origin).silenceTimeoutMs())
    }

    @Test
    fun `un health que falla no rompe la app`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        assertNull(BackendHealth(origin).silenceTimeoutMs())
    }

    @Test
    fun `un health con basura no rompe la app`() = runBlocking {
        server.enqueue(MockResponse().setBody("no es json"))
        assertNull(BackendHealth(origin).silenceTimeoutMs())
    }

    @Test
    fun `sin backend el silencio es desconocido`() = runBlocking {
        server.shutdown()
        assertNull(BackendHealth(origin).silenceTimeoutMs())
        assertNull(BackendHealth("tampoco es una url").silenceTimeoutMs())
    }
}
