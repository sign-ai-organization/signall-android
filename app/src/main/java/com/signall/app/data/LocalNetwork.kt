package com.signall.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * De dónde salen las direcciones de la red local que se van a escanear.
 *
 * Se usa `ConnectivityManager.getLinkProperties` de la red activa porque es la
 * única fuente que entrega **dirección y máscara juntas** (el prefijo, que es lo
 * que define la subred). `WifiManager.connectionInfo` está obsoleto, no sirve
 * para Ethernet y tampoco da el prefijo con la misma claridad.
 *
 * Se agrega `NetworkInterface` porque en el caso del **hotspot del propio
 * teléfono** la red activa es la de datos móviles —una dirección pública que se
 * descarta— mientras que la interfaz del punto de acceso (`ap0`, `swlan0`, …)
 * tiene la subred donde está conectada la PC con el backend. Sin esto, el
 * descubrimiento funcionaría con el teléfono como cliente de un Wi-Fi pero no
 * cuando el teléfono es el que comparte su conexión.
 *
 * Las interfaces celulares (`rmnet*`, `ccmni*`, …) y las de túnel se excluyen a
 * propósito: además de no ser la LAN del backend, un CGNAT con `10.x` pasaría
 * el filtro de "privado" y se escanearía una red que no es la nuestra.
 */
interface LocalSubnetProvider {
    fun currentAddresses(): List<Ipv4LinkAddress>
}

class AndroidLocalSubnetProvider(context: Context) : LocalSubnetProvider {
    private val appContext = context.applicationContext

    override fun currentAddresses(): List<Ipv4LinkAddress> {
        val found = mutableListOf<Ipv4LinkAddress>()
        found += activeNetworkAddresses()
        found += interfaceAddresses()
        return found
    }

    private fun activeNetworkAddresses(): List<Ipv4LinkAddress> = runCatching {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val network = manager.activeNetwork ?: return emptyList()
        val properties = manager.getLinkProperties(network) ?: return emptyList()
        // La red activa puede ser la de datos móviles. Se exige que la interfaz
        // sea local antes de usar sus direcciones: un CGNAT con 10.x pasaría el
        // filtro de "privado" y haría escanear una red que no es la LAN.
        if (!isLocalInterfaceName(properties.interfaceName.orEmpty())) return emptyList()
        properties.linkAddresses.mapNotNull { it.toIpv4OrNull() }
    }.getOrDefault(emptyList())

    private fun interfaceAddresses(): List<Ipv4LinkAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces()
            ?.toList()
            .orEmpty()
            .filter { it.isUp && !it.isLoopback && isLocalInterfaceName(it.name) }
            .flatMap { nic ->
                nic.interfaceAddresses.mapNotNull { address ->
                    val inet = address.address
                    if (inet !is Inet4Address || inet.isLoopbackAddress) return@mapNotNull null
                    inet.hostAddress?.let { Ipv4LinkAddress(it, address.networkPrefixLength.toInt()) }
                }
            }
    }.getOrDefault(emptyList())

    private fun LinkAddress.toIpv4OrNull(): Ipv4LinkAddress? {
        val inet = address
        if (inet !is Inet4Address || inet.isLoopbackAddress) return null
        return inet.hostAddress?.let { Ipv4LinkAddress(it, prefixLength) }
    }

    private fun isLocalInterfaceName(name: String): Boolean {
        val lower = name.lowercase()
        if (CELLULAR_PREFIXES.any { lower.startsWith(it) }) return false
        return LOCAL_PREFIXES.any { lower.startsWith(it) }
    }

    private companion object {
        /** Wi-Fi, hotspot, Ethernet, USB y Wi-Fi Direct. */
        val LOCAL_PREFIXES = listOf("wlan", "ap", "softap", "swlan", "eth", "usb", "rndis", "p2p", "br")

        /** Datos móviles y túneles: nunca son la red local del backend. */
        val CELLULAR_PREFIXES = listOf("rmnet", "ccmni", "pdp", "clat", "tun", "tap", "dummy", "sit", "ip6")
    }
}
