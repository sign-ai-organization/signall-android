package com.signai.webview.data

import com.signai.webview.BuildConfig

/**
 * Where the backend lives.
 *
 * The URL is a build config field so a physical device can point at the
 * development machine without editing code:
 *
 * ```
 * ./gradlew installDebug -Psignai.backendWsUrl=ws://192.168.1.50:8080/ws/translate
 * ```
 *
 * The default is `10.0.2.2`, the Android emulator's alias for the host machine.
 * On a real device use the host's LAN address, never `127.0.0.1` (that points
 * at the phone itself).
 */
data class SignAiBackendConfig(val url: String) {

    /**
     * HTTP origin of the same backend, for downloading sign assets.
     *
     * `ws://10.0.2.2:8080/ws/translate` → `http://10.0.2.2:8080`. Only the
     * scheme and the host/port are kept; the WebSocket path is dropped.
     */
    val httpOrigin: String
        get() {
            val scheme = if (url.startsWith("wss://")) "https://" else "http://"
            val hostAndPort = url.substringAfter("://").substringBefore("/")
            return scheme + hostAndPort
        }

    companion object {
        const val DEFAULT = "ws://10.0.2.2:8080/ws/translate"

        fun fromBuildConfig(): SignAiBackendConfig =
            SignAiBackendConfig(BuildConfig.BACKEND_WS_URL.ifBlank { DEFAULT })
    }
}
