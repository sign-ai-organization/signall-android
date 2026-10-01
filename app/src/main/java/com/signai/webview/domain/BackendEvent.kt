package com.signai.webview.domain

/**
 * Everything the backend can tell us over `ws://…/ws/translate`.
 *
 * The protocol is deliberately asymmetric: a transcript and an error are JSON
 * objects tagged with `type`, while a translation result is a **bare JSON
 * array** of segments with no envelope. That is why a result cannot be told
 * apart by a `type` field.
 */
sealed interface BackendEvent {
    data object Connected : BackendEvent

    data class Transcript(val text: String, val final: Boolean) : BackendEvent

    data class Translation(val segments: List<TranslationSegment>) : BackendEvent

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
