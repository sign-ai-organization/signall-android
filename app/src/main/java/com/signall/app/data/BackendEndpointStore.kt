package com.signall.app.data

import android.content.Context

/**
 * El último endpoint que funcionó, para probarlo primero en el siguiente
 * arranque.
 *
 * Se guarda en las `SharedPreferences` que ya usa la app (`signall`, la misma
 * del historial y los ajustes) en vez de estrenar DataStore: es un solo valor,
 * la escritura ocurre una vez por conexión y no hay nada que migrar. Si algún
 * día hay más estado del backend, se cambia de almacenamiento sin tocar a quien
 * lo consume.
 */
interface BackendEndpointStore {
    fun lastSuccessfulUrl(): String?

    fun save(url: String)
}

class SharedPrefsBackendEndpointStore(context: Context) : BackendEndpointStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun lastSuccessfulUrl(): String? =
        prefs.getString(KEY_LAST_ENDPOINT, null)?.takeIf { it.isNotBlank() }

    override fun save(url: String) {
        prefs.edit().putString(KEY_LAST_ENDPOINT, url).apply()
    }

    private companion object {
        const val PREFS_NAME = "signall"
        const val KEY_LAST_ENDPOINT = "backend_ws_url"
    }
}
