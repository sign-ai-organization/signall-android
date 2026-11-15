package com.signall.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La lista de candidatos del escaneo es la que decide si el descubrimiento
 * puede tocar una red ajena. Por eso se prueba lo que **no** puede aparecer
 * (rangos públicos, red, broadcast, la propia IP) además de lo que sí.
 */
class NetworkCandidatesTest {

    @Test
    fun `una 24 excluye red broadcast y la ip propia`() {
        val hosts = NetworkCandidates.hostsFor(Ipv4LinkAddress("192.168.43.1", 24))

        assertEquals(253, hosts.size)
        assertFalse(hosts.contains("192.168.43.0"))
        assertFalse(hosts.contains("192.168.43.1"))
        assertFalse(hosts.contains("192.168.43.255"))
        assertTrue(hosts.contains("192.168.43.2"))
        assertTrue(hosts.contains("192.168.43.254"))
    }

    @Test
    fun `los candidatos no se repiten`() {
        val hosts = NetworkCandidates.hostsFor(Ipv4LinkAddress("192.168.43.1", 24))
        assertEquals(hosts.size, hosts.toSet().size)
    }

    @Test
    fun `una mascara ancha se recorta a la 24 que contiene la ip`() {
        val hosts = NetworkCandidates.hostsFor(Ipv4LinkAddress("172.20.5.10", 16))

        assertEquals(253, hosts.size)
        assertFalse(hosts.contains("172.20.5.10"))
        // Solo la /24 de la IP, no las 256 redes de la /16.
        assertTrue(hosts.all { it.startsWith("172.20.5.") })
    }

    @Test
    fun `un rango publico no produce candidatos`() {
        assertTrue(NetworkCandidates.hostsFor(Ipv4LinkAddress("8.8.8.8", 24)).isEmpty())
        assertTrue(NetworkCandidates.hostsFor(Ipv4LinkAddress("172.32.0.10", 24)).isEmpty())
        assertTrue(NetworkCandidates.hostsFor(Ipv4LinkAddress("192.169.1.10", 24)).isEmpty())
        assertTrue(NetworkCandidates.hostsFor(Ipv4LinkAddress("127.0.0.1", 24)).isEmpty())
    }

    @Test
    fun `una direccion invalida no produce candidatos`() {
        assertTrue(NetworkCandidates.hostsFor(Ipv4LinkAddress("192.168.1", 24)).isEmpty())
        assertTrue(NetworkCandidates.hostsFor(Ipv4LinkAddress("192.168.1.999", 24)).isEmpty())
        assertTrue(NetworkCandidates.hostsFor(Ipv4LinkAddress("no es una ip", 24)).isEmpty())
    }

    @Test
    fun `la misma red reportada por dos interfaces se prueba una sola vez`() {
        val address = Ipv4LinkAddress("192.168.43.1", 24)
        val hosts = NetworkCandidates.merge(listOf(address, address))

        assertEquals(253, hosts.size)
    }

    @Test
    fun `dos subredes distintas se suman sin repetir`() {
        val hosts =
            NetworkCandidates.merge(
                listOf(
                    Ipv4LinkAddress("192.168.43.1", 24),
                    Ipv4LinkAddress("10.0.0.5", 24),
                ),
            )

        assertEquals(253 + 253, hosts.size)
        assertTrue(hosts.first().startsWith("192.168.43."))
        assertTrue(hosts.any { it.startsWith("10.0.0.") })
    }

    @Test
    fun `una 30 deja los dos hosts utiles`() {
        val hosts = NetworkCandidates.hostsFor(Ipv4LinkAddress("192.168.1.10", 30))

        assertEquals(listOf("192.168.1.9"), hosts)
    }

    @Test
    fun `el tope acota el peor caso con varias subredes`() {
        val wide =
            listOf(
                Ipv4LinkAddress("10.0.0.10", 16),
                Ipv4LinkAddress("10.1.0.10", 16),
                Ipv4LinkAddress("10.2.0.10", 16),
            )
        val hosts = NetworkCandidates.merge(wide)

        assertEquals(NetworkCandidates.MAX_HOSTS, hosts.size)
    }

    @Test
    fun `solo las tres redes privadas son privadas`() {
        assertTrue(NetworkCandidates.isPrivateIpv4("10.0.0.1"))
        assertTrue(NetworkCandidates.isPrivateIpv4("172.16.0.1"))
        assertTrue(NetworkCandidates.isPrivateIpv4("172.31.255.254"))
        assertTrue(NetworkCandidates.isPrivateIpv4("192.168.1.1"))

        assertFalse(NetworkCandidates.isPrivateIpv4("172.15.0.1"))
        assertFalse(NetworkCandidates.isPrivateIpv4("172.32.0.1"))
        assertFalse(NetworkCandidates.isPrivateIpv4("192.169.1.1"))
        assertFalse(NetworkCandidates.isPrivateIpv4("169.254.1.1"))
        assertFalse(NetworkCandidates.isPrivateIpv4("8.8.8.8"))
        assertFalse(NetworkCandidates.isPrivateIpv4(""))
    }
}
