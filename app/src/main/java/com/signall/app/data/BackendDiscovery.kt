package com.signall.app.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.measureTimeMillis

/**
 * Quien sabe a qué backend hay que abrirle el WebSocket.
 *
 * La interfaz existe para que [com.signall.app.ui.state.MainScreenState] no
 * dependa de Android: en las pruebas se puede resolver sin red y sin NSD.
 */
interface BackendEndpointResolver {
    /** El endpoint que se debe usar ahora mismo. */
    val current: SignAllBackendConfig

    /**
     * Busca un backend y actualiza [current]. Nunca devuelve `null`: si no
     * encuentra nada, cae al fallback compilado.
     */
    suspend fun resolve(): SignAllBackendConfig

    /** La sesión abrió contra [current]: vale la pena recordarlo. */
    fun rememberConnected()
}

/**
 * Descubrimiento automático del backend, en el orden que pide el dueño:
 *
 * 1. **El último endpoint exitoso** guardado en preferencias. Es el caso normal
 *    al abrir la app: la red no cambió y el backend contesta en el primer
 *    intento.
 * 2. **mDNS**, con el tipo propio [SIGNALL_MDNS_SERVICE_TYPE] y una ventana de
 *    [MDNS_TIMEOUT_MS]. Si el servicio se anuncia, no hace falta escanear nada.
 * 3. **La subred local**, solo si no apareció por mDNS. Se prueban los hosts en
 *    paralelo con concurrencia limitada ([SCAN_CONCURRENCY]) y un `GET /health`
 *    corto por host, y se acepta solo la respuesta propia de SignAll.
 * 4. **`BuildConfig.BACKEND_WS_URL`**, únicamente como último recurso. Es el
 *    comportamiento viejo, y sigue sirviendo para el emulador
 *    (`10.0.2.2`) y para `adb reverse` (`127.0.0.1`).
 *
 * Cada paso exitoso se guarda, y [rememberConnected] vuelve a guardar el
 * endpoint cuando el WebSocket abre. Así el siguiente arranque empieza por el
 * que de verdad funcionó, sin recompilar la app cuando el DHCP cambia la IP.
 *
 * El esquema lo decide el build: un APK `debug` busca `http://…:8080` (el
 * backend en Docker); uno `demo`/`release` busca `https://…:443` (Caddy). El
 * descubrimiento nunca propone `ws://` en un build cifrado ni al revés.
 */
class BackendDiscovery(
    private val store: BackendEndpointStore,
    private val fallback: SignAllBackendConfig,
    private val mdns: MdnsDiscovery,
    private val localNetwork: LocalSubnetProvider,
    private val probe: BackendHealthProbe,
    private val mdnsTimeoutMs: Long = MDNS_TIMEOUT_MS,
    private val scanConcurrency: Int = SCAN_CONCURRENCY,
) : BackendEndpointResolver {

    @Volatile
    override var current: SignAllBackendConfig = fallback
        private set

    override suspend fun resolve(): SignAllBackendConfig = withContext(Dispatchers.IO) {
        Log.i(TAG, "buscando backend: recordado=${store.lastSuccessfulUrl() ?: "ninguno"}")

        remembered()?.let { return@withContext it }
        byMdns()?.let { return@withContext it }
        byScanningSubnet()?.let { return@withContext it }

        Log.i(TAG, "sin resultados: se usa el fallback ${fallback.url}")
        current = fallback
        fallback
    }

    override fun rememberConnected() {
        store.save(current.url)
        Log.i(TAG, "encontrado: sesión abierta contra ${current.url}")
    }

    /**
     * El endpoint guardado se **prueba**, no se confía: la IP pudo cambiar de
     * dueño en el DHCP, o el backend pudo mudarse a otra máquina. Si no
     * responde, se vuelve a descubrir.
     */
    private suspend fun remembered(): SignAllBackendConfig? {
        val saved = store.lastSuccessfulUrl() ?: return null
        val config = SignAllBackendConfig(saved)
        if (probeConfig(config)) {
            adopt(config, "recordado")
            return config
        }
        Log.i(TAG, "intento fallido: el endpoint recordado $saved no responde; se vuelve a descubrir")
        return null
    }

    private suspend fun byMdns(): SignAllBackendConfig? {
        val services =
            try {
                mdns.discover(mdnsTimeoutMs)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w(TAG, "mDNS falló: ${e.message}")
                emptyList()
            }
        var winner: SignAllBackendConfig? = null
        val elapsed = measureTimeMillis {
            for (service in services) {
                // Un anuncio mDNS también puede venir de otra red o de un
                // servicio ajeno: se exige IPv4 privada y se confirma por /health.
                if (!NetworkCandidates.isPrivateIpv4(service.host)) {
                    Log.i(TAG, "intento fallido: mDNS ${service.name} apunta fuera de la red local")
                    continue
                }
                val config = configFor(service)
                if (probeConfig(config)) {
                    adopt(config, "mDNS ${service.name}")
                    winner = config
                    break
                }
                Log.i(
                    TAG,
                    "intento fallido: mDNS ${service.name} en ${service.host}:${service.port} " +
                        "no es el backend de SignAll",
                )
            }
        }
        if (winner == null) {
            Log.i(
                TAG,
                if (services.isEmpty()) {
                    "sin resultados de mDNS en $mdnsTimeoutMs ms"
                } else {
                    "intento fallido: ningún anuncio mDNS sirvió ($elapsed ms)"
                },
            )
        }
        return winner
    }

    /**
     * Escaneo de la subred, solo si mDNS no encontró nada.
     *
     * La subred sale de las direcciones locales del teléfono y
     * [NetworkCandidates] filtra: si la red activa es móvil o no es privada, la
     * lista queda vacía y no se escanea nada.
     */
    private suspend fun byScanningSubnet(): SignAllBackendConfig? {
        val addresses = runCatching { localNetwork.currentAddresses() }.getOrDefault(emptyList())
        val hosts = NetworkCandidates.merge(addresses)
        if (hosts.isEmpty()) {
            Log.i(TAG, "sin resultados: no hay una subred privada local que escanear")
            return null
        }

        val secure = fallback.isEncrypted
        val port = if (secure) HTTPS_PORT else HTTP_PORT
        Log.i(
            TAG,
            "buscando: ${hosts.size} hosts de la subred local " +
                "(${if (secure) "https" else "http"}://…:$port/health)",
        )

        val winner = AtomicReference<String?>(null)
        val elapsed = measureTimeMillis { winner.set(scan(hosts, port, secure)) }
        val host = winner.get()
        if (host == null) {
            Log.i(TAG, "intento fallido: ningún host respondió el /health de SignAll en $elapsed ms")
            return null
        }

        val scheme = if (secure) "wss" else "ws"
        val config = SignAllBackendConfig("$scheme://$host:$port/ws/translate")
        adopt(config, "escaneo de subred")
        return config
    }

    private suspend fun scan(hosts: List<String>, port: Int, secure: Boolean): String? = coroutineScope {
        val queue = Channel<String>(Channel.UNLIMITED)
        hosts.forEach { queue.trySend(it) }
        queue.close()

        val winner = AtomicReference<String?>(null)
        val workers = List(scanConcurrency.coerceIn(1, hosts.size)) {
            launch {
                for (host in queue) {
                    if (winner.get() != null) break
                    if (probe.isHealthy(host, port, secure)) {
                        winner.compareAndSet(null, host)
                        break
                    }
                }
            }
        }
        workers.forEach { it.join() }
        winner.get()
    }

    private suspend fun probeConfig(config: SignAllBackendConfig): Boolean {
        val hostAndPort = config.httpOrigin.substringAfter("://")
        val host = hostAndPort.substringBefore(":")
        val port = hostAndPort.substringAfter(":", "").substringBefore("/").toIntOrNull()
            ?: if (config.isEncrypted) HTTPS_PORT else HTTP_PORT
        return probe.isHealthy(host, port, config.isEncrypted)
    }

    private fun configFor(service: DiscoveredService): SignAllBackendConfig {
        val secure = service.secure
        val scheme = if (secure) "wss" else "ws"
        return SignAllBackendConfig("$scheme://${service.host}:${service.port}/ws/translate")
    }

    private fun adopt(config: SignAllBackendConfig, source: String) {
        current = config
        store.save(config.url)
        Log.i(TAG, "encontrado: ${config.url} ($source)")
    }

    companion object {
        const val TAG = "SignAllNet"

        /** Ventana de mDNS antes de pasar al escaneo (el pedido: 2–3 s). */
        const val MDNS_TIMEOUT_MS = 2_500L

        /** Hosts que se prueban a la vez; acota el pico de sockets del teléfono. */
        const val SCAN_CONCURRENCY = 32

        const val HTTP_PORT = 8080
        const val HTTPS_PORT = 443
    }
}
