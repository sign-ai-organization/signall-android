package com.signall.app.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Pregunta si en `host:port` hay un backend de SignAll.
 *
 * Es la pieza que convierte "hay algo escuchando" en "es el nuestro": abre
 * `GET /health` con un tiempo de espera corto y acepta solo la respuesta JSON
 * de [SignAllHealthParser].
 */
interface BackendHealthProbe {
    suspend fun isHealthy(host: String, port: Int, secure: Boolean): Boolean
}

/**
 * Implementación real con OkHttp.
 *
 * Los tiempos son cortos a propósito (300–500 ms): el escaneo prueba cientos de
 * direcciones y una que no contesta en medio segundo no es el backend de esta
 * red. La llamada se hace con `enqueue` para que la corrutina pueda cancelarse
 * de verdad: al salir de la pantalla se cancelan las llamadas en vuelo en vez de
 * esperar a que venza el timeout.
 */
class HttpBackendHealthProbe : BackendHealthProbe {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(PROBE_TIMEOUT_MS + 100, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    override suspend fun isHealthy(host: String, port: Int, secure: Boolean): Boolean {
        val scheme = if (secure) "https" else "http"
        val request = runCatching {
            Request.Builder().url("$scheme://$host:$port/health").build()
        }.getOrNull() ?: return false
        val body = fetchBody(request) ?: return false
        return SignAllHealthParser.parse(body)?.isHealthy == true
    }

    private suspend fun fetchBody(request: Request): String? = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.use { if (it.isSuccessful) it.body?.string() else null }
                    if (continuation.isActive) continuation.resume(body)
                }
            },
        )
    }

    companion object {
        /** Tope de espera por host; el rango pedido es 300–500 ms. */
        const val PROBE_TIMEOUT_MS = 400L
    }
}
