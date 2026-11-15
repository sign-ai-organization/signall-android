package com.signall.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicReference

/**
 * Avisa cuando la red por defecto cambia: aparece una, se cae o es otra.
 *
 * Existe para que la app no dependa del latido del WebSocket para enterarse de
 * que la red se movió. Sin esto, cambiar de Wi-Fi a datos —o llegar a otra
 * casa— dejaba el socket "abierto" contra una red que ya no existía hasta que
 * el ping fallaba, y recién ahí empezaba la reconexión: hasta 20 s de
 * [SignAllWebSocketClient.HeartbeatIntervalSec] más la espera creciente, que es
 * justo lo que se percibía como "hay que reiniciar la app para que agarre".
 *
 * La interfaz es una función para que [com.signall.app.ui.state.MainScreenState]
 * no dependa de Android: en las pruebas se emite a mano.
 */
fun interface NetworkWatcher {
    /**
     * Un flujo **frío**: cada colección registra el callback del sistema, y al
     * cancelarla se desregistra. Emite `Unit` sin más detalle: al consumidor no
     * le interesa qué red llegó, solo que lo que había dejado de ser válido.
     */
    fun changes(): Flow<Unit>
}

/**
 * Implementación con `ConnectivityManager.registerDefaultNetworkCallback`.
 *
 * Se registra el callback de la red **por defecto** y no un `NetworkRequest`:
 * lo que importa es por dónde sale el tráfico de la app, porque es la misma
 * ruta que usa el WebSocket.
 *
 * El estado inicial se toma de [ConnectivityManager.getActiveNetwork] antes de
 * registrar, así que el primer `onAvailable` del sistema (que llega enseguida
 * con la red que ya existía) **no** se confunde con un cambio: si no, abrir la
 * app cortaría la sesión recién abierta. Si la app arranca sin red, no hay
 * estado inicial y el primer aviso sí es real.
 */
class AndroidNetworkWatcher(context: Context) : NetworkWatcher {

    private val connectivity: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun changes(): Flow<Unit> = callbackFlow {
        val manager = connectivity
        if (manager == null) {
            // Sin servicio no hay nada que vigilar; la sesión sigue con el
            // latido como única red de seguridad.
            awaitClose { }
            return@callbackFlow
        }

        val last = AtomicReference(
            try {
                manager.activeNetwork
            } catch (e: Exception) {
                Log.w(TAG, "no se pudo leer la red activa: ${e.message}")
                null
            },
        )

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Misma red que la que ya había: el sistema repite este callback
                // en varios momentos y no es un cambio que justifique cortar.
                if (last.getAndSet(network) == network) return
                Log.i(TAG, "aviso de red: disponible $network")
                trySend(Unit)
            }

            override fun onLost(network: Network) {
                if (!last.compareAndSet(network, null)) return
                Log.i(TAG, "aviso de red: perdida $network")
                trySend(Unit)
            }
        }

        manager.registerDefaultNetworkCallback(callback)
        awaitClose {
            try {
                manager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.w(TAG, "no se pudo desregistrar el aviso de red: ${e.message}")
            }
        }
    }

    private companion object {
        const val TAG = "SignAllNet"
    }
}
