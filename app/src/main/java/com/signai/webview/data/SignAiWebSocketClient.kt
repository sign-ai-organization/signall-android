package com.signai.webview.data

import android.util.Log
import com.signai.webview.domain.BackendEvent
import com.signai.webview.domain.PhraseType
import com.signai.webview.domain.TranslationGloss
import com.signai.webview.domain.TranslationSegment
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import okio.ByteString.Companion.toByteString

/**
 * WebSocket client for the backend's translation session (`/ws/translate`).
 *
 * Only the typed-phrase direction exists so far (plan paso 2): [sendText]
 * sends `{"type":"text","text":…}`. Audio chunks are paso 5 of the plan and
 * will reuse this same connection, not a second one.
 *
 * [events] is a function, not a property: every call is a fresh cold flow, so
 * reconnecting means collecting it again.
 */
class SignAiWebSocketClient(private val config: SignAiBackendConfig) {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // A translation session is long-lived: no read timeout.
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @Volatile
    private var socket: WebSocket? = null

    /** True while there is a live session to talk to. */
    val isConnected: Boolean
        get() = socket != null

    fun events(): Flow<BackendEvent> = callbackFlow {
        val producer = this
        val request = Request.Builder().url(config.url).build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                trySend(BackendEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseMessage(text)?.let { trySend(it) }
            }

            // Closing the flow on failure is what makes reconnecting possible:
            // the collector returns and opens a new session.
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "session failed: ${t.message}")
                producer.trySend(BackendEvent.Disconnected)
                producer.close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                producer.trySend(BackendEvent.Disconnected)
                producer.close()
            }
        }

        val ws = http.newWebSocket(request, listener)
        socket = ws
        awaitClose {
            ws.close(NORMAL_CLOSURE, "client closing")
            socket = null
        }
    }

    /** Sends a typed phrase. Returns false when there is no live session. */
    fun sendText(text: String): Boolean {
        val payload = JSONObject().put("type", "text").put("text", text).toString()
        return socket?.send(payload) ?: false
    }

    /**
     * Sends one raw audio chunk over the same session as [sendText].
     *
     * The backend is the only one that talks to the STT service, so this is
     * plain binary in and nothing else: the backend regroups the chunks for its
     * voice-activity detector and answers with transcripts and a result.
     */
    fun sendAudio(chunk: ByteArray): Boolean = socket?.send(chunk.toByteString()) ?: false

    fun close() {
        socket?.close(NORMAL_CLOSURE, "client closing")
        socket = null
    }

    /**
     * Tolerant parser: a result is a bare array, transcript and error are
     * objects tagged with `type`. Anything unreadable is dropped with a log
     * rather than thrown — a malformed frame must not take the session down.
     */
    private fun parseMessage(text: String): BackendEvent? {
        val trimmed = text.trim()
        return try {
            when {
                trimmed.startsWith("[") -> parseSegments(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    when (obj.optString("type")) {
                        "transcript" -> BackendEvent.Transcript(
                            text = obj.optString("text"),
                            final = obj.optBoolean("final"),
                        )

                        "error" -> BackendEvent.Failure(
                            message = obj.optString("message").ifBlank { DEFAULT_ERROR },
                        )

                        else -> null
                    }
                }

                else -> null
            }
        } catch (e: JSONException) {
            Log.w(TAG, "ignoring unreadable message: ${e.message}")
            null
        }
    }

    private fun parseSegments(raw: String): BackendEvent.Translation {
        val array = JSONArray(raw)
        val segments = (0 until array.length()).map { i ->
            val segment = array.getJSONObject(i)
            val glossArray = segment.optJSONArray("glosses") ?: JSONArray()
            val glosses = (0 until glossArray.length()).map { j ->
                val gloss = glossArray.getJSONObject(j)
                TranslationGloss(
                    text = gloss.optString("text"),
                    available = gloss.optBoolean("available"),
                    glbPath = gloss.optString("glb_path"),
                )
            }
            TranslationSegment(
                type = PhraseType.from(segment.optString("type")),
                glosses = glosses,
            )
        }
        return BackendEvent.Translation(segments)
    }

    private companion object {
        const val TAG = "SignAiNet"
        const val NORMAL_CLOSURE = 1000
        const val DEFAULT_ERROR = "Ocurrió un error en el servidor."
    }
}
