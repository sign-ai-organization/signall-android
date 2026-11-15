package com.signall.app.data

import com.signall.app.BuildConfig

/**
 * Un endpoint del backend.
 *
 * La app ya **no** depende de una IP horneada: al arrancar, y cada vez que se
 * pierde la conexión, `BackendDiscovery` busca el backend con el endpoint
 * guardado, mDNS y un escaneo de la subred local, y recién si todo eso falla
 * usa `BuildConfig.BACKEND_WS_URL` como último recurso.
 *
 * `build.gradle.kts` sigue llenando esa constante por build type, y sigue
 * sirviendo para los casos en los que el descubrimiento no aplica: el emulador
 * (`10.0.2.2`), `adb reverse` (`127.0.0.1`) y una URL de release sin descubrir.
 *
 * - `debug` toma `-Psignall.backendWsUrl=…`:
 *
 *   ```
 *   ./gradlew installDebug -Psignall.backendWsUrl=ws://127.0.0.1:8080/ws/translate
 *   ```
 *
 * - `release` **ignora esa propiedad a menos que sea `wss://`**. El SRS pide
 *   que todo lo que sale del dispositivo vaya cifrado (`main.tex:1095`) y que
 *   el enlace con el backend sea WSS (`:1100`), así que un APK firmado no puede
 *   quedar apuntando a `ws://` por una propiedad de build.
 *
 * El esquema de [url] también decide qué busca el descubrimiento: `ws` prueba
 * `http://…:8080` (backend en Docker) y `wss` prueba `https://…:443` (Caddy).
 */
data class SignAllBackendConfig(val url: String) {

    /** True when the session is encrypted, which is what the SRS asks for. */
    val isEncrypted: Boolean get() = url.startsWith("wss://")

    /**
     * HTTP origin of the same backend, for downloading sign assets.
     *
     * `ws://10.0.2.2:8080/ws/translate` → `http://10.0.2.2:8080`, and
     * `wss://…` → `https://…`. Only the scheme and the host/port are kept; the
     * WebSocket path is dropped.
     */
    val httpOrigin: String
        get() {
            val scheme = if (isEncrypted) "https://" else "http://"
            val hostAndPort = url.substringAfter("://").substringBefore("/")
            return scheme + hostAndPort
        }

    companion object {
        /** Only reached if a build somehow ships without the property. */
        const val DEFAULT = "ws://10.0.2.2:8080/ws/translate"

        fun fromBuildConfig(): SignAllBackendConfig =
            SignAllBackendConfig(BuildConfig.BACKEND_WS_URL.ifBlank { DEFAULT })
    }
}
