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
 * One session carries both directions the app needs, and both are plain frames
 * on the same socket:
 *
 * - [sendAudio] streams the microphone as binary PCM; the backend owns the STT
 *   connection and answers with transcripts.
 * - [sendText] sends the phrase to translate. **This is the only thing that gets
 *   translated**: a transcript coming back the other way is text for the input,
 *   and turning it into a phrase is this app's decision, not the server's (see
 *   `BackendEvent.Transcript`).
 * - [sendEnd] tells the backend the utterance is over, so it can close the
 *   segment the STT has open (see [sendEnd]).
 *
 * There is no "capture mode" frame. The app used to send one so the backend would
 * know whether to transcribe or to sign; the backend does not need to know, and
 * guessing wrong signed dictated phrases before the user confirmed them.
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
                // RF-INT-COM-03 se verifica "por inspección del tráfico de red", así
                // que la app dice cuál de los dos ethernetos abrió. En un build de
                // release esta línea nunca puede decir SIN CIFRAR.
                Log.i(
                    TAG,
                    "sesión abierta: ${config.url} (${if (config.isEncrypted) "TLS" else "SIN CIFRAR"})",
                )
                trySend(BackendEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseMessage(text).forEach { trySend(it) }
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

    /**
     * Sends the phrase to translate.
     *
     * Everything the avatar signs goes through here, whatever produced it: a
     * phrase typed in the field, one the user confirmed from dictation, or one
     * the `TR` switch sent on its own when the phrase closed. The backend only
     * translates what arrives in this frame, which is what keeps RF-STT-03 true.
     */
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

    /**
     * Closes the utterance the transcriber has open.
     *
     * The STT decides a segment ended by hearing silence, but if the user
     * releases the mic first, that silence is never sent — the capture stops
     * the moment it is toggled off — and the segment would stay open forever.
     * The STT protocol has a frame for exactly this: send `end` to force the
     * final transcript of the segment in progress.
     */
    fun sendEnd(): Boolean {
        val payload = JSONObject().put("type", "end").toString()
        return socket?.send(payload) ?: false
    }

    fun close() {
        socket?.close(NORMAL_CLOSURE, "client closing")
        socket = null
    }

    /**
     * Tolerant parser: a message is a JSON object tagged with `type`, and
     * anything unreadable is dropped with a log rather than thrown — a
     * malformed frame must not take the session down.
     *
     * Returns a list because an older backend sent the phrase's segments as one
     * bare array with no envelope, which turns into one event per segment. The
     * backend is deployed from its own repo and can lag behind the app, so both
     * shapes are accepted and produce the same events — nothing downstream has
     * to know which one arrived.
     */
    private fun parseMessage(raw: String): List<BackendEvent> {
        val trimmed = raw.trim()
        return try {
            when {
                trimmed.startsWith("{") -> listOfNotNull(parseEnvelope(JSONObject(trimmed)))
                trimmed.startsWith("[") -> parseLegacyArray(JSONArray(trimmed))
                else -> emptyList()
            }
        } catch (e: JSONException) {
            Log.w(TAG, "ignoring unreadable message: ${e.message}")
            emptyList()
        }
    }

    /** One tagged message, or null when its `type` is one we ignore. */
    private fun parseEnvelope(obj: JSONObject): BackendEvent? = when (obj.optString("type")) {
        "transcript" -> BackendEvent.Transcript(
            text = obj.optString("text"),
            final = obj.optBoolean("final"),
        )

        "segment" -> {
            val segment = obj.optJSONObject("segment") ?: return null
            val event = BackendEvent.Segment(
                index = obj.optInt("index"),
                total = obj.optInt("total"),
                segment = parseSegment(segment),
            )
            Log.d(
                TAG,
                "segment ${event.index}/${event.total} -> " +
                    event.segment.glosses.joinToString(",") { it.text },
            )
            event
        }

        "done" -> BackendEvent.PhraseDone

        "error" -> BackendEvent.Failure(
            message = obj.optString("message").ifBlank { DEFAULT_ERROR },
        )

        // `trace` / `sentence_trace` are the debug-only pipeline timings. Ignoring
        // every unknown type is what lets the backend add messages without
        // breaking this client — including the `mode` frame an older backend
        // expects, which is now simply not sent and, if it arrives, not read.
        else -> null
    }

    private fun parseSegment(segment: JSONObject): TranslationSegment {
        val glossArray = segment.optJSONArray("glosses") ?: JSONArray()
        val glosses = (0 until glossArray.length()).map { j ->
            val gloss = glossArray.getJSONObject(j)
            TranslationGloss(
                text = gloss.optString("text"),
                available = gloss.optBoolean("available"),
                glbPath = gloss.optString("glb_path"),
            )
        }
        return TranslationSegment(
            type = PhraseType.from(segment.optString("type")),
            glosses = glosses,
        )
    }

    private fun parseLegacyArray(array: JSONArray): List<BackendEvent> {
        val total = array.length()
        return (0 until total).mapNotNull { i ->
            val segment = array.optJSONObject(i) ?: return@mapNotNull null
            BackendEvent.Segment(index = i, total = total, segment = parseSegment(segment))
        }
    }

    private companion object {
        const val TAG = "SignAiNet"
        const val NORMAL_CLOSURE = 1000
        const val DEFAULT_ERROR = "Ocurrió un error en el servidor."
    }
}
