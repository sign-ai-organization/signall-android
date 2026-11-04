package com.signai.webview.domain

/**
 * Everything the backend can tell us over `ws://…/ws/translate`.
 *
 * One shape per message, tagged with a `type`: the client sends the phrase to
 * translate as `{"type":"text","text":"…"}`, and the backend answers with one
 * [Segment] per **sentence** of that phrase, `index` and `total` real, then
 * [PhraseDone].
 *
 * Audio and text are the same socket, but they are not the same thing: the
 * backend forwards transcripts ([Transcript]) and translates only what the client
 * sent as `text`. That is what makes RF-STT-03 hold by construction — there is no
 * way for a dictated phrase to reach the language module without the app having
 * sent it — and it is why this client has no "capture mode" to tell the backend
 * about. See `docs/android-integration.md` §3.1 in the backend repo.
 *
 * Frames tagged with a `type` this client does not know (`trace` and
 * `sentence_trace`, both debug-only) are dropped by the parser, so the backend
 * can add messages without breaking the app.
 */
sealed interface BackendEvent {
    data object Connected : BackendEvent

    /**
     * What the STT made of the audio so far.
     *
     * [final] true means the utterance closed: by the silence the STT heard, or
     * by the `end` the client sent when the user released the microphone. It can
     * come with an empty [text], which is an answer and not a lost phrase: the
     * utterance closed and held no words (noise, or the microphone was released
     * over silence). An empty transcript never blanks the input.
     *
     * Whether a transcript is input text or the source of a phrase to sign is
     * **this client's** decision, decided by [MicMode] and not by a flag from
     * the server. The backend does not know which mode the app is in, and it must
     * not: a flag it has to guess is a flag it can get wrong, and getting it
     * wrong signs a dictated phrase before the user confirmed it.
     */
    data class Transcript(
        val text: String,
        val final: Boolean,
    ) : BackendEvent

    /**
     * One translated sentence of the phrase in flight.
     *
     * [index] is its position and [total] how many sentences the phrase has. The
     * app collects all of them and signs the phrase as one queue, so a sentence
     * that needs an extra LLM round trip cannot leave the avatar signing half a
     * phrase.
     *
     * [turn] is the identifier the app put on the phrase (see
     * `SignAiWebSocketClient.sendText`); it comes back untouched. `null` means
     * the backend did not echo one, either because it is an older backend or
     * because this is a legacy array frame, and then the app falls back to its
     * own stale-turn bookkeeping.
     */
    data class Segment(
        val index: Int,
        val total: Int,
        val segment: TranslationSegment,
        val turn: Int? = null,
    ) : BackendEvent

    /**
     * End of the phrase: every sentence has been sent, whether it worked or not.
     *
     * [turn] identifies which phrase closed. Without it the app cannot tell the
     * `done` of a phrase it cancelled from the `done` of the phrase it sent
     * next.
     */
    data class PhraseDone(val turn: Int? = null) : BackendEvent

    data class Failure(
        val message: String,
        val turn: Int? = null,
    ) : BackendEvent

    data object Disconnected : BackendEvent
}

/**
 * One sign queued for playback, already resolved to a URL the WebView can load.
 *
 * The URL is always **same-origin** (`https://appassets.androidplatform.net/…`)
 * because the 3D scene cannot fetch the backend over plain HTTP — see
 * `SignAssetRepository`.
 */
data class SignQueueItem(
    val gloss: String,
    val type: PhraseType,
    val url: String,
    /** False when the backend's catalog doesn't know this gloss (RF-CAT-03). */
    val available: Boolean,
)
