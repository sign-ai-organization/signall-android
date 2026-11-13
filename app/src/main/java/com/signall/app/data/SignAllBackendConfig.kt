package com.signall.app.data

import com.signall.app.BuildConfig

/**
 * Where the backend lives.
 *
 * The URL comes from `BuildConfig.BACKEND_WS_URL`, which `build.gradle.kts`
 * fills **per build type**:
 *
 * - `debug` takes `-Psignall.backendWsUrl=…`, so a physical device can point at
 *   the development machine without editing code:
 *
 *   ```
 *   ./gradlew installDebug -Psignall.backendWsUrl=ws://127.0.0.1:8080/ws/translate
 *   ```
 *
 *   The default is `10.0.2.2`, the Android emulator's alias for the host machine.
 *   On a real device use the host's LAN address, never `127.0.0.1` (that points
 *   at the phone itself) — or `adb reverse tcp:8080 tcp:8080`, which makes
 *   `127.0.0.1` the host again.
 *
 * - `release` **ignores that property unless it is `wss://`**. The SRS asks for
 *   everything leaving the device to be encrypted (`main.tex:1095`) and for the
 *   app↔backend link to be WSS (`:1100`), so a signed build must not be able to
 *   be pointed at a plain `ws://` by a build flag. No domain is deployed yet,
 *   which is why its default is a host that cannot resolve instead of a guess.
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
