package com.signai.webview.data

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
    fun cachedUrl(gloss: String, extension: String): String? {
        val file = fileFor(gloss, extension)
        return if (file.isFile) SIGNS_PATH_PREFIX + file.name else null
    }

    /**
     * Ensures the asset for [gloss] is on disk and returns its same-origin path,
     * or null when it cannot be fetched. Already-downloaded files are reused
     * (the SDD's "downloads the *missing* animation files").
     */
    suspend fun resolve(gloss: String, assetPath: String): String? {
        if (assetPath.isBlank()) return null
        val extension = extensionFor(assetPath)
        cachedUrl(gloss, extension)?.let { return it }

        return withContext(Dispatchers.IO) {
            val dir = File(cacheDir, SIGNS_PATH_PREFIX.trim('/')).apply { mkdirs() }
            val target = File(dir, fileNameFor(gloss, extension))
            val relative = assetPath.trimStart('/')
            // The request is built *inside* the runCatching on purpose: a
            // malformed URL (a bad `signai.backendWsUrl`) must degrade to
            // "asset unavailable", not take the app down mid-playback.
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

    private fun fileFor(gloss: String, extension: String) =
        File(cacheDir, SIGNS_PATH_PREFIX.trim('/')).let { File(it, fileNameFor(gloss, extension)) }

    companion object {
        const val TAG = "SignAiNet"

        /** Must stay in sync with the `PathHandler` registered in `AvatarWebView`. */
        const val SIGNS_PATH_PREFIX = "/signs/"

        /** Must stay in sync with the `WebViewAssetLoader` domain in `AvatarWebView`. */
        const val WEB_ORIGIN = "https://appassets.androidplatform.net"

        fun fileNameFor(gloss: String, extension: String): String =
            gloss.uppercase().replace(UNSAFE, "_") + extension

        /** The corpus ships baked landmark frames (`.json`); `.glb` is the later format. */
        fun extensionFor(assetPath: String): String =
            if (assetPath.endsWith(".glb", ignoreCase = true)) ".glb" else ".json"

        private val UNSAFE = Regex("[^A-Z0-9Ñ_]")
    }
}
