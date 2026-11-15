package com.signall.app.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import kotlin.coroutines.resume

/**
 * El tipo DNS-SD con el que se anuncia el backend de SignAll.
 *
 * Se eligió un tipo **propio** y no `_http._tcp` porque `_http._tcp` lo
 * anuncian impresoras, routers y cámaras: el descubrimiento tendría que probar
 * servicios ajenos y confiar en el `/health` para distinguirlos. `_signall`
 * dice el nombre del producto y `_tcp` el transporte (HTTP y WebSocket van
 * sobre TCP). El punto final es obligatorio para `NsdManager`: es el nombre
 * completamente calificado (`_signall._tcp.`), no una cadena a medio armar.
 *
 * Del lado de la PC se publica con Avahi (o un contenedor) en el puerto de
 * entrada real: `8080` con el backend en Docker, `443` detrás de Caddy. Ver
 * `docs/DESCUBRIMIENTO-BACKEND.md`.
 */
const val SIGNALL_MDNS_SERVICE_TYPE = "_signall._tcp."

/** Un servicio ya resuelto a una dirección y un puerto. */
data class DiscoveredService(
    val name: String,
    val host: String,
    val port: Int,
    /** El anuncio pide TLS (`tls=1` o `scheme=wss`), o el puerto es el 443. */
    val secure: Boolean,
)

interface MdnsDiscovery {
    /**
     * Busca el servicio durante [timeoutMs] y devuelve lo que haya resuelto.
     * Nunca lanza por un fallo de NSD: "no apareció" es un resultado normal.
     */
    suspend fun discover(timeoutMs: Long): List<DiscoveredService>
}

/**
 * Descubrimiento mDNS con `NsdManager`.
 *
 * El `MulticastLock` no es opcional en la práctica: varios fabricantes —MIUI
 * entre ellos— filtran el multicast de la pantalla apagada o en ahorro de
 * energía, y sin el lock el `onServiceFound` no llega nunca aunque Avahi esté
 * anunciando. Se toma solo durante la ventana de búsqueda y se suelta siempre.
 *
 * La primera resolución buena corta la búsqueda: no hace falta juntar todos los
 * anuncios de la red si el que se necesita es uno. El health check que hace
 * [BackendDiscovery] después es el que decide si ese anuncio es realmente el
 * backend de SignAll.
 */
class NsdMdnsDiscovery(context: Context) : MdnsDiscovery {
    private val appContext = context.applicationContext

    override suspend fun discover(timeoutMs: Long): List<DiscoveredService> = withContext(Dispatchers.IO) {
        val nsd = appContext.getSystemService(NsdManager::class.java)
            ?: return@withContext emptyList()

        val multicastLock = acquireMulticastLock()
        val found = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val listener = discoveryListener(found)
        val results = mutableListOf<DiscoveredService>()

        try {
            val started = runCatching {
                nsd.discoverServices(SIGNALL_MDNS_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            }
            if (started.isFailure) {
                Log.w(TAG, "mDNS no pudo arrancar: ${started.exceptionOrNull()?.message}")
                return@withContext emptyList()
            }

            withTimeoutOrNull(timeoutMs) {
                for (info in found) {
                    val service = resolve(nsd, info) ?: continue
                    results += service
                    // Con uno alcanza: el health check dirá si sirve.
                    break
                }
            }
        } finally {
            found.close()
            runCatching { nsd.stopServiceDiscovery(listener) }
            multicastLock?.let { runCatching { it.release() } }
        }
        results.toList()
    }

    private fun discoveryListener(found: Channel<NsdServiceInfo>) =
        object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "buscando por mDNS: $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                found.trySend(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "mDNS no arrancó ($serviceType): error $errorCode")
                found.close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "mDNS no se pudo detener ($serviceType): error $errorCode")
            }
        }

    // `resolveService` es obsoleto desde la API 34 en favor de la variante con
    // `Executor`, pero el proyecto compila contra minSdk 26 y la versión vieja
    // sigue disponible y funcionando. Cambiarla obliga a dos caminos por versión
    // sin ganar nada.
    @Suppress("DEPRECATION")
    private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): DiscoveredService? =
        withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : NsdManager.ResolveListener {
                    override fun onServiceResolved(service: NsdServiceInfo) {
                        val host = service.resolvedHost()
                        val result = host?.let {
                            DiscoveredService(
                                name = service.serviceName.orEmpty(),
                                host = it,
                                port = service.port,
                                secure = service.requestsTls(),
                            )
                        }
                        if (continuation.isActive) continuation.resume(result)
                    }

                    override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "mDNS no resolvió ${service.serviceName}: error $errorCode")
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                runCatching { nsd.resolveService(info, listener) }
                    .onFailure {
                        Log.w(TAG, "mDNS no pudo resolver: ${it.message}")
                        if (continuation.isActive) continuation.resume(null)
                    }
            }
        }

    /**
     * La IP del anuncio, prefiriendo IPv4.
     *
     * Un mismo servicio puede resolver a varias direcciones (IPv4 e IPv6). El
     * escaneo y el `/health` de SignAll son IPv4, así que quedarse con la
     * primera que devuelva el sistema —a veces una IPv6 link-local— haría
     * fallar el mDNS aunque el anuncio sea correcto. Desde la API 34 se leen
     * todas; antes solo existe `host`, que en la práctica es IPv4.
     */
    @Suppress("DEPRECATION")
    private fun NsdServiceInfo.resolvedHost(): String? {
        val addresses =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                hostAddresses
            } else {
                listOfNotNull(host)
            }
        return addresses.firstOrNull { it is Inet4Address }?.hostAddress
            ?: addresses.firstOrNull()?.hostAddress
    }

    /**
     * El TXT del anuncio puede pedir TLS. `tls=1` lo dice explícitamente y
     * `scheme=wss` lo dice por nombre; el puerto 443 también, porque es el
     * puerto que publica Caddy. Sin ninguna de las tres, se asume texto claro,
     * que es el caso del backend en Docker en el 8080.
     */
    private fun NsdServiceInfo.requestsTls(): Boolean {
        val attributes = attributes.orEmpty()
        val tls = attributes.valueFor("tls")
        val scheme = attributes.valueFor("scheme")
        return tls == "1" || tls.equals("true", ignoreCase = true) ||
            scheme.equals("wss", ignoreCase = true) ||
            scheme.equals("https", ignoreCase = true) ||
            port == HTTPS_PORT
    }

    private fun Map<String, ByteArray>.valueFor(key: String): String? =
        entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
            ?.value
            ?.toString(Charsets.UTF_8)

    private fun acquireMulticastLock(): WifiManager.MulticastLock? = runCatching {
        appContext.getSystemService(WifiManager::class.java)
            ?.createMulticastLock(MULTICAST_LOCK_TAG)
            ?.apply {
                setReferenceCounted(false)
                acquire()
            }
    }.onFailure { Log.w(TAG, "sin MulticastLock: ${it.message}") }.getOrNull()

    private companion object {
        const val TAG = "SignAllNet"
        const val MULTICAST_LOCK_TAG = "signall-mdns"
        const val RESOLVE_TIMEOUT_MS = 1_500L
        const val HTTPS_PORT = 443
    }
}
