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

    /** HTTP origin of the same backend, for downloading sign assets over HTTPS. */
    val httpOrigin: String
        get() = url
            .substringBefore("://")
            .replace("ws", "http") + url.substringAfter("://").substringBeforeLast("/")

    companion object {
        const val DEFAULT = "ws://10.0.2.2:8080/ws/translate"

        fun fromBuildConfig(): SignAiBackendConfig =
            SignAiBackendConfig(BuildConfig.BACKEND_WS_URL.ifBlank { DEFAULT })
    }
}
