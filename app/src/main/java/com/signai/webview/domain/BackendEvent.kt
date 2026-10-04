package com.signai.webview.domain

/**
 * Everything the backend can tell us over `ws://…/ws/translate`.
 *
 * The backend streams a phrase as one `segment` message per sentence, each
 * carrying its position and how many sentences the phrase has in total, then a
 * `done`. Segments therefore arrive while the phrase is still being
 * translated — `MainScreenState` collects them and only builds the playback
 * queue once the phrase is complete, so the avatar never signs half a phrase.
 *
 * Frames tagged with a `type` this client does not know (`trace` and
 * `sentence_trace`, both debug-only) are dropped by the parser, so the backend
 * can add messages without breaking the app.
 */
sealed interface BackendEvent {
    data object Connected : BackendEvent

    data class Transcript(val text: String, val final: Boolean) : BackendEvent

    /**
     * One translated sentence of the phrase in flight.
     *
     * [index] is its position and [total] how many sentences the phrase has;
     * both are known from the very first message. They arrive in order, but the
     * queue still waits for all of them, because a sentence that takes an extra
     * LLM round trip must not cut the phrase short.
     */
    data class Segment(
        val index: Int,
        val total: Int,
        val segment: TranslationSegment,
    ) : BackendEvent

    /** End of the phrase: every segment has been sent. */
    data object PhraseDone : BackendEvent

    data class Failure(val message: String) : BackendEvent

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