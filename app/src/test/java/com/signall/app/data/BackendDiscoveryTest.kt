package com.signall.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El orden del descubrimiento es el contrato con el dueño: recordado → mDNS →
 * subred → fallback. Si el orden se cambia, el arranque normal paga un escaneo
 * de 254 hosts que no hacía falta, o peor: se acepta un host antes de preguntar
 * por el que ya funcionaba.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackendDiscoveryTest {

    private class FakeStore(var url: String? = null) : BackendEndpointStore {
        var saved = 0
            private set

        override fun lastSuccessfulUrl(): String? = url

        override fun save(url: String) {
            this.url = url
            saved++
        }
    }

    private class FakeMdns(var services: List<DiscoveredService> = emptyList()) : MdnsDiscovery {
        var calls = 0
            private set

        override suspend fun discover(timeoutMs: Long): List<DiscoveredService> {
            calls++
            return services
        }
    }

    private class FakeSubnets(var addresses: List<Ipv4LinkAddress> = emptyList()) : LocalSubnetProvider {
        var calls = 0
            private set

        override fun currentAddresses(): List<Ipv4LinkAddress> {
            calls++
            return addresses
        }
    }

    /** Sano por host; anota cada host/puerto/scheme que se probó. */
    private class FakeProbe(var healthy: Set<String> = emptySet()) : BackendHealthProbe {
        // Varios workers del escaneo llaman a la vez: una lista común se
        // corrompe. En el código real el probe no guarda estado.
        val attempts = java.util.concurrent.CopyOnWriteArrayList<String>()

        override suspend fun isHealthy(host: String, port: Int, secure: Boolean): Boolean {
            attempts += "$host:$port/${if (secure) "https" else "http"}"
            return healthy.contains(host)
        }
    }

    private val fallback = SignAllBackendConfig("ws://10.0.2.2:8080/ws/translate")

    private fun discovery(
        store: FakeStore,
        mdns: FakeMdns = FakeMdns(),
        subnets: FakeSubnets = FakeSubnets(),
        probe: FakeProbe = FakeProbe(),
        wsFallback: SignAllBackendConfig = fallback,
    ) = BackendDiscovery(store, wsFallback, mdns, subnets, probe)

    @Test
    fun `el endpoint recordado se prueba primero y no se escanea nada`() = runBlocking {
        val store = FakeStore("ws://192.168.43.100:8080/ws/translate")
        val mdns = FakeMdns()
        val subnets = FakeSubnets()
        val probe = FakeProbe(healthy = setOf("192.168.43.100"))
        val resolver = discovery(store, mdns, subnets, probe)

        val result = resolver.resolve()

        assertEquals("ws://192.168.43.100:8080/ws/translate", result.url)
        assertEquals("192.168.43.100:8080/http", probe.attempts.single())
        assertEquals(0, mdns.calls)
        assertEquals(0, subnets.calls)
        assertEquals("ws://192.168.43.100:8080/ws/translate", store.url)
    }

    @Test
    fun `si el recordado falla se busca por mDNS antes de escanear`() = runBlocking {
        val store = FakeStore("ws://192.168.43.50:8080/ws/translate")
        val mdns = FakeMdns(listOf(DiscoveredService("signall", "192.168.43.100", 8080, secure = false)))
        val subnets = FakeSubnets(listOf(Ipv4LinkAddress("192.168.43.5", 24)))
        val probe = FakeProbe(healthy = setOf("192.168.43.100"))
        val resolver = discovery(store, mdns, subnets, probe)

        val result = resolver.resolve()

        assertEquals("ws://192.168.43.100:8080/ws/translate", result.url)
        assertEquals(1, mdns.calls)
        assertEquals(0, subnets.calls)
        assertTrue(probe.attempts.first().startsWith("192.168.43.50"))
        assertEquals("ws://192.168.43.100:8080/ws/translate", store.url)
    }

    @Test
    fun `si mDNS no aparece se escanea la subred local`() = runBlocking {
        val store = FakeStore()
        val subnets = FakeSubnets(listOf(Ipv4LinkAddress("192.168.43.5", 24)))
        val probe = FakeProbe(healthy = setOf("192.168.43.100"))
        val resolver = discovery(store, FakeMdns(), subnets, probe)

        val result = resolver.resolve()

        assertEquals("ws://192.168.43.100:8080/ws/translate", result.url)
        assertEquals(1, subnets.calls)
        // La subred se probó con el puerto y el esquema del backend en Docker.
        assertTrue(probe.attempts.all { it.endsWith(":8080/http") })
        assertTrue(probe.attempts.contains("192.168.43.100:8080/http"))
    }

    @Test
    fun `un anuncio mDNS fuera de la red privada se descarta sin probarlo`() = runBlocking {
        val store = FakeStore()
        val mdns =
            FakeMdns(
                listOf(
                    DiscoveredService("ajeno", "203.0.113.10", 8080, secure = false),
                ),
            )
        val subnets = FakeSubnets(listOf(Ipv4LinkAddress("192.168.43.5", 24)))
        val probe = FakeProbe(healthy = setOf("192.168.43.100"))
        val resolver = discovery(store, mdns, subnets, probe)

        val result = resolver.resolve()

        assertEquals("ws://192.168.43.100:8080/ws/translate", result.url)
        assertFalse(probe.attempts.any { it.startsWith("203.0.113.10") })
    }

    @Test
    fun `sin nada encontrado se usa el fallback y no se guarda`() = runBlocking {
        val store = FakeStore()
        val probe = FakeProbe()
        val resolver = discovery(store, subnets = FakeSubnets(), probe = probe)

        val result = resolver.resolve()

        assertEquals(fallback.url, result.url)
        assertEquals(fallback.url, resolver.current.url)
        assertEquals(0, store.saved)
    }

    @Test
    fun `un build cifrado no busca en la red y usa el compilado`() = runBlocking {
        val secureFallback = SignAllBackendConfig("wss://127.0.0.1:8443/ws/translate")
        val store = FakeStore()
        val mdns = FakeMdns(listOf(DiscoveredService("signall", "192.168.43.100", 443, secure = true)))
        val subnets = FakeSubnets(listOf(Ipv4LinkAddress("192.168.43.5", 24)))
        val probe = FakeProbe(healthy = setOf("192.168.43.100"))
        val resolver = discovery(store, mdns, subnets, probe, wsFallback = secureFallback)

        val result = resolver.resolve()

        assertEquals("wss://127.0.0.1:8443/ws/translate", result.url)
        assertEquals(secureFallback.url, resolver.current.url)
        assertEquals(0, mdns.calls)
        assertEquals(0, subnets.calls)
        assertTrue(probe.attempts.isEmpty())
        assertEquals(0, store.saved)
    }

    @Test
    fun `un build cifrado prueba el recordado wss antes del compilado`() = runBlocking {
        val secureFallback = SignAllBackendConfig("wss://127.0.0.1:8443/ws/translate")
        val store = FakeStore("wss://192.168.43.100:443/ws/translate")
        val mdns = FakeMdns()
        val subnets = FakeSubnets()
        val probe = FakeProbe(healthy = setOf("192.168.43.100"))
        val resolver = discovery(store, mdns, subnets, probe, wsFallback = secureFallback)

        val result = resolver.resolve()

        assertEquals("wss://192.168.43.100:443/ws/translate", result.url)
        assertEquals("192.168.43.100:443/https", probe.attempts.single())
        assertEquals(0, mdns.calls)
        assertEquals(0, subnets.calls)
    }

    @Test
    fun `un build cifrado con el recordado caido usa el compilado sin escanear`() = runBlocking {
        val secureFallback = SignAllBackendConfig("wss://127.0.0.1:8443/ws/translate")
        val store = FakeStore("wss://192.168.43.100:443/ws/translate")
        val mdns = FakeMdns()
        val subnets = FakeSubnets(listOf(Ipv4LinkAddress("192.168.43.5", 24)))
        val probe = FakeProbe()
        val resolver = discovery(store, mdns, subnets, probe, wsFallback = secureFallback)

        val result = resolver.resolve()

        assertEquals(secureFallback.url, result.url)
        assertEquals(listOf("192.168.43.100:443/https"), probe.attempts)
        assertEquals(0, mdns.calls)
        assertEquals(0, subnets.calls)
    }

    @Test
    fun `un build cifrado ignora un endpoint recordado en texto claro`() = runBlocking {
        val secureFallback = SignAllBackendConfig("wss://127.0.0.1:8443/ws/translate")
        val store = FakeStore("ws://192.168.43.100:8080/ws/translate")
        val probe = FakeProbe(healthy = setOf("192.168.43.100"))
        val resolver = discovery(store, probe = probe, wsFallback = secureFallback)

        val result = resolver.resolve()

        assertEquals(secureFallback.url, result.url)
        assertTrue(probe.attempts.isEmpty())
    }

    @Test
    fun `un anuncio con tls o en el 443 se abre con wss`() = runBlocking {
        val store = FakeStore()
        val mdns =
            FakeMdns(
                listOf(
                    DiscoveredService("caddy", "192.168.43.100", 443, secure = true),
                ),
            )
        val probe = FakeProbe(healthy = setOf("192.168.43.100"))
        val resolver = discovery(store, mdns = mdns, probe = probe)

        val result = resolver.resolve()

        assertEquals("wss://192.168.43.100:443/ws/translate", result.url)
        assertTrue(probe.attempts.contains("192.168.43.100:443/https"))
    }

    @Test
    fun `sin red privada no se escanea y no se inventan intentos`() = runBlocking {
        val store = FakeStore()
        val subnets = FakeSubnets(listOf(Ipv4LinkAddress("100.64.0.8", 24)))
        val probe = FakeProbe()
        val resolver = discovery(store, subnets = subnets, probe = probe)

        val result = resolver.resolve()

        assertEquals(fallback.url, result.url)
        assertTrue(probe.attempts.isEmpty())
    }

    @Test
    fun `rememberConnected guarda el endpoint vigente`() = runBlocking {
        val store = FakeStore()
        val subnets = FakeSubnets(listOf(Ipv4LinkAddress("192.168.43.5", 24)))
        val probe = FakeProbe(healthy = setOf("192.168.43.100"))
        val resolver = discovery(store, subnets = subnets, probe = probe)
        resolver.resolve()

        resolver.rememberConnected()

        assertEquals("ws://192.168.43.100:8080/ws/translate", store.url)
        assertEquals(2, store.saved)
    }
}
