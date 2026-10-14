package com.signai.webview.domain

/**
 * Everything the backend can tell us over `ws://…/ws/translate`.
 *
 * The backend speaks two shapes of the same message, and [Segment.stream] is
 * what tells them apart:
 *
 * - **Whole phrase** (`stream = false`, the default): the backend waited for the
 *   full transcript and sends one `segment` per sentence with `total` known
 *   from the first message. The app collects them all and signs the phrase as a
 *   unit, so it never signs half a sentence.
 * - **Streamed** (`stream = true`): the backend translated a fragment as soon as
 *   the transcriber confirmed those words had been said, and sends it straight
 *   away. `total` is 0 because, in an utterance still open, nobody knows yet how
 *   many fragments there will be. The app appends to the playback queue instead
 *   of waiting, so the avatar signs while the user is still talking.
 *
 * Both shapes are the same `segment` frame with additive fields, and both are
 * produced by the same backend: `REALTIME_ENABLED=false` gives the first,
 * `true` the second. A backend older than this client sends neither `stream` nor
 * `total: 0`, and lands on the whole-phrase path, which is the safe default.
 *
 * Frames tagged with a `type` this client does not know (`trace` and
 * `sentence_trace`, both debug-only) are dropped by the parser, so the backend
 * can add messages without breaking the app.
 */
sealed interface BackendEvent {
    data object Connected : BackendEvent

    data class Transcript(val text: String, val final: Boolean) : BackendEvent

    /**
     * One translated piece of the phrase in flight.
     *
     * [index] is its position. [total] is how many pieces the phrase has, and is
     * 0 while that is still unknown — which is the normal case for a streamed
     * fragment. [provisional] marks a fragment computed from a hypothesis the
     * transcriber could still revise; [sourceText] is the text it came from,
     * which is what the caption and the history record.
     */
    data class Segment(
        val index: Int,
        val total: Int,
        val segment: TranslationSegment,
        val stream: Boolean = false,
        val provisional: Boolean = false,
        val sourceText: String = "",
    ) : BackendEvent

    /** End of the phrase: every fragment has been sent. */
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