package com.signall.app.data

/**
 * Una dirección IPv4 con su longitud de prefijo, tal como la entrega
 * `LinkProperties` para la red activa.
 */
data class Ipv4LinkAddress(val address: String, val prefixLength: Int)

/**
 * Genera los hosts de la red local que vale la pena probar durante el
 * descubrimiento del backend.
 *
 * Reglas, y el motivo de cada una:
 *
 * - **Solo IPv4 privado (RFC 1918)**: `10/8`, `172.16/12` y `192.168/16`. Un
 *   rango público nunca se escanea: la app no es un escáner de Internet y el
 *   backend siempre está en la misma red local.
 * - **Nunca la dirección propia**: el teléfono no corre el backend.
 * - **Nunca red ni broadcast**: en una `/24` son `.0` y `.255`, que no son hosts.
 * - **Se recorta a `/24` como máximo**: una `/16` tiene 65.534 hosts y el
 *   escaneo dejaría de caber en unos pocos segundos. El backend está en la
 *   misma subred inmediata del teléfono, así que la `/24` que lo contiene
 *   alcanza.
 * - **Deduplicación**: el teléfono puede reportar la misma red por
 *   `LinkProperties` y por `NetworkInterface`; se prueba una sola vez.
 *
 * El tope total ([MAX_HOSTS]) mantiene acotado el peor caso cuando hay más de
 * una interfaz local (Wi-Fi y Ethernet, por ejemplo).
 */
object NetworkCandidates {

    /** Tope de hosts por descubrimiento, contando todas las interfaces. */
    const val MAX_HOSTS = 508

    /** Prefijo más ancho que se acepta; por debajo se recorta a /24. */
    const val MIN_PREFIX_LENGTH = 24

    /** `true` si [ip] es una IPv4 privada RFC 1918 válida. */
    fun isPrivateIpv4(ip: String): Boolean {
        val octets = parseIpv4(ip) ?: return false
        val first = octets[0]
        val second = octets[1]
        return when {
            first == 10 -> true
            first == 172 && second in 16..31 -> true
            first == 192 && second == 168 -> true
            else -> false
        }
    }

    /** Hosts de una sola dirección, sin repetir. */
    fun hostsFor(address: Ipv4LinkAddress): List<String> = merge(listOf(address))

    /**
     * Hosts de todas las direcciones locales, sin repetir y en el orden en que
     * llegaron. Las direcciones inválidas o públicas se ignoran en silencio:
     * una interfaz celular con CGNAT o una IPv6 no son candidatas.
     */
    fun merge(addresses: List<Ipv4LinkAddress>): List<String> {
        val hosts = LinkedHashSet<String>()
        for (link in addresses) {
            val octets = parseIpv4(link.address) ?: continue
            if (!isPrivateIpv4(link.address)) continue

            val self = toLong(octets)
            val prefix = link.prefixLength.coerceIn(MIN_PREFIX_LENGTH, 30)
            val mask = maskFor(prefix)
            val network = self and mask
            val broadcast = network or (mask.inv() and MASK_32)
            val first = if (prefix >= 31) network else network + 1
            val last = if (prefix >= 31) broadcast else broadcast - 1

            for (host in first..last) {
                if (host == self) continue
                hosts += toIpv4(host)
                if (hosts.size >= MAX_HOSTS) return hosts.toList()
            }
        }
        return hosts.toList()
    }

    private fun parseIpv4(ip: String): IntArray? {
        val parts = ip.trim().split('.')
        if (parts.size != 4) return null
        val octets = IntArray(4)
        for ((index, part) in parts.withIndex()) {
            if (part.isEmpty() || part.length > 3) return null
            val value = part.toIntOrNull() ?: return null
            if (value !in 0..255) return null
            octets[index] = value
        }
        return octets
    }

    private fun toLong(octets: IntArray): Long =
        (octets[0].toLong() shl 24) or
            (octets[1].toLong() shl 16) or
            (octets[2].toLong() shl 8) or
            octets[3].toLong()

    private fun toIpv4(value: Long): String =
        listOf(
            (value shr 24) and 0xFF,
            (value shr 16) and 0xFF,
            (value shr 8) and 0xFF,
            value and 0xFF,
        ).joinToString(".")

    private fun maskFor(prefix: Int): Long = (MASK_32 shl (32 - prefix)) and MASK_32

    private const val MASK_32 = 0xFFFF_FFFFL
}
