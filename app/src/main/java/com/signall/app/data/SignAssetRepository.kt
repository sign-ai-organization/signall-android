package com.signall.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Turns a backend `glb_animation_path` into a URL the 3D scene can actually load.
 *
 * **Why the download happens here and not in JavaScript.** The scene runs on
 * `https://appassets.androidplatform.net`, so a `fetch("http://…:8080/…")` is
 * mixed content and Android blocks it — there is no permission that allows it.
 * So the native side downloads the file into the app cache and the scene reads
 * it back through [SIGNS_PATH_PREFIX], same origin as everything else it loads.
 * That is also what the SDD's process view describes ("the Android client
 * downloads the missing animation files").
 *
 * Downloading is best-effort: if the backend doesn't serve the file yet — the
 * static GLB endpoint is still pending in the backend's plan — resolution
 * returns null and the caller falls back (RF-CAT-03).
 */
class SignAssetRepository(
    private val cacheDir: File,
    private val httpOrigin: String,
) {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /** Same-origin path of an already-downloaded asset, or null. */
    fun cachedUrl(gloss: String, assetPath: String): String? {
        val file = fileFor(gloss, assetPath)
        return if (file.isFile) SIGNS_PATH_PREFIX + file.name else null
    }

    /**
     * Ensures the asset for [gloss] is on disk and returns its same-origin path,
     * or null when it cannot be fetched. Already-downloaded files are reused
     * (the SDD's "downloads the *missing* animation files").
     */
    suspend fun resolve(gloss: String, assetPath: String): String? {
        if (assetPath.isBlank()) return null
        cachedUrl(gloss, assetPath)?.let { return it }
        val target = fileFor(gloss, assetPath)
        val relative = assetPath.trimStart('/')
        // The request is built *inside* the runCatching on purpose: a
        // malformed URL (a bad `signall.backendWsUrl`) must degrade to
        // "asset unavailable", not take the app down mid-playback.
        return withContext(Dispatchers.IO) {
            target.parentFile?.mkdirs()
            runCatching {
                val request = Request.Builder()
                    .url(httpOrigin.trimEnd('/') + "/" + relative)
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "asset $relative -> HTTP ${response.code}")
                        return@use null
                    }
                    val body = response.body ?: return@use null
                    target.outputStream().use { out -> body.byteStream().copyTo(out) }
                    SIGNS_PATH_PREFIX + target.name
                }
            }.onFailure { Log.w(TAG, "asset $relative failed: ${it.message}") }
                .getOrNull()
        }
    }

    private fun fileFor(gloss: String, assetPath: String) =
        File(cacheDir, SIGNS_PATH_PREFIX.trim('/'))
            .let { File(it, fileNameFor(gloss, assetPath)) }

    companion object {
        const val TAG = "SignAllNet"

        /** Must stay in sync with the `PathHandler` registered in `AvatarWebView`. */
        const val SIGNS_PATH_PREFIX = "/signs/"

        /** Must stay in sync with the `WebViewAssetLoader` domain in `AvatarWebView`. */
        const val WEB_ORIGIN = "https://appassets.androidplatform.net"

        /**
         * Cached file name: the gloss plus a short fingerprint of the asset
         * path, e.g. `YO_a1b2c3.json`.
         *
         * The fingerprint is what keeps the cache honest. Keyed by gloss alone,
         * an already-downloaded file was reused forever, so a backend that
         * starts serving a *different* animation for a gloss looked like it had
         * no effect. Tying the name to the path means a new path means a new
         * file.
         *
         * Known limit: if the path stays the same and only its contents
         * change, the cache is still stale — that needs an ETag or a content
         * hash from the server.
         */
        fun fileNameFor(gloss: String, assetPath: String): String {
            val fingerprint = assetPath.hashCode().toUInt().toString(16).take(6)
            return gloss.uppercase().replace(UNSAFE, "_") + "_" + fingerprint + extensionFor(assetPath)
        }

        /** The corpus ships baked landmark frames (`.json`); `.glb` is the later format. */
        fun extensionFor(assetPath: String): String =
            if (assetPath.endsWith(".glb", ignoreCase = true)) ".glb" else ".json"

        private val UNSAFE = Regex("[^A-Z0-9Ñ_]")
    }
}
