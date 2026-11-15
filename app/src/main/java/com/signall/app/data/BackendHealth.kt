package com.signall.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * El `SILENCE_TIMEOUT_MS` que está usando el STT, leído del `/health` del backend.
 *
 * Por qué no lo lee la app del STT: la app nunca habla con el STT, y ese valor
 * no está en el backend — vive en el entorno del STT. El backend lo copia a su
 * propio `/health` (`app/main.py`, `_watch_stt_config`) y por eso el número
 * llega por acá.
 *
 * Por qué la app lo necesita: es el segundo bloque más grande de la latencia de
 * punta a punta —con 1500 ms son 1,5 s entre que la persona deja de hablar y
 * que la frase existe— y hasta ahora no aparecía en ninguna parte de la app.
 * Verlo en el overlay de depurar evita depender de que alguien lo anote a mano
 * en cada corrida (decisión D-6).
 *
 * Es best-effort como [SignAssetRepository.resolve]: si no se puede leer, el
 * overlay muestra "?" y la app sigue igual, porque un valor que no se conoce no
 * es motivo para romper nada.
 */
class BackendHealth(private val originProvider: () -> String) {

    /** Constructor para quien tiene un origen fijo (pruebas y usos puntuales). */
    constructor(httpOrigin: String) : this({ httpOrigin })

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /** `silence_timeout_ms` del STT, o null si no se pudo leer. */
    suspend fun silenceTimeoutMs(): Int? = withContext(Dispatchers.IO) {
        // La URL se arma adentro del runCatching: un `signall.backendWsUrl` mal
        // escrito tiene que degradar a "no se sabe", no tirar la app.
        runCatching {
            val request = Request.Builder().url(originProvider().trimEnd('/') + "/health").build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                JSONObject(body).optInt("silence_timeout_ms").takeIf { it > 0 }
            }
        }.onFailure { Log.i(TAG, "no se pudo leer /health: ${it.message}") }.getOrNull()
    }

    private companion object {
        const val TAG = "SignAllHealth"
    }
}
