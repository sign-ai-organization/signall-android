package com.signai.webview.ui.state

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/**
     * Latency numbers only the device can produce.
 *
 * The backend and the STT each measure their own leg (`/metrics` on both), and
 * between them they know everything except what happens after the message lands
 * on the phone: how long a whole phrase took from the moment the user sent it to
 * the last sentence coming back, and how long the avatar took to sign it.
 *
 * Those two are what decide whether the avatar feels like it is keeping up with
 * the speaker, so they are worth having without a debugger attached. Everything
 * goes to logcat under `SignAiMetrics`; there is no in-app surface, because a
 * number nobody reads does not belong in the interface.
 *
 *     adb logcat -s SignAiMetrics
 */
class SignAiMetrics {

    private val phrases = AtomicLong()
    private val sentences = AtomicLong()
    private val glosses = AtomicLong()
    private val unavailable = AtomicLong()
    private val dictationPhrases = AtomicLong()
    private val dictationWords = AtomicLong()

    private val turnTotal = Latency()
    private val signTotal = Latency()
    private val signStart = Latency()

    /**
     * Una frase se representó: desde que el usuario la mandó hasta que el backend
     * devolvió su última oración.
     *
     * El punto de partida es el **envío**, no el primer texto: es la "confirmación
     * de envío del usuario" de RNF-DES-01, o sea el reloj que el usuario percibe.
     * En el modo en vivo la frase la manda la propia app al cerrarse, y ahí lo que
     * se cuenta es desde ese `final`.
     *
     * No incluye lo que se representa después: para eso está [onPlayback], que mide la
     * animación y solo se mueve cuando cambia la escena.
     */
    fun onTurn(totalMs: Long, sentenceCount: Int, glossCount: Int) {
        phrases.incrementAndGet()
        sentences.addAndGet(sentenceCount.toLong())
        glosses.addAndGet(glossCount.toLong())
        turnTotal.record(totalMs)
        Log.i(
            TAG,
            "frase #$phrases: ${totalMs}ms, $sentenceCount oraciones, $glossCount glosas " +
                "(${turnTotal.summarise()})",
        )
    }

    /**
     * A turn finished signing: first sign started, last sign done.
     *
     * The only one of the two that measures the **animation**, and the one that
     * says whether the avatar is keeping up or signing into the past: a phrase
     * whose signs take 9 s to play is already wrong by the third one.
     *
     * It is deliberately not [onTurn]. That one runs from the user sending the
     * phrase to the last sentence coming back, so it measures the NLP and does
     * not move at all when the scene changes: the crossfade shortens how long the
     * queue takes to play, not how long the backend takes to send it. Measuring
     * the same thing twice with two names would make the second one look broken.
     */
    fun onPlayback(totalMs: Long, signCount: Int) {
        signTotal.record(totalMs)
        Log.i(
            TAG,
            "representado: $signCount señas en ${totalMs}ms (${signTotal.summarise()})",
        )
    }

    /** A fragment produced no playable sign, so nothing was queued for it. */
    fun onUnavailableGloss() {
        unavailable.incrementAndGet()
    }

    /**
     * La primera oración del turno ya se está moviendo en pantalla.
     *
     * El tramo que hasta ahora no tenía número en ningún lado. El backend mide
     * desde que recibe la frase hasta que manda el `segment` (`rt_first_sign_ms`),
     * y acá [onTurn] mide desde el envío del usuario hasta la última oración de
     * vuelta — pero entre que el `segment` llega al teléfono y que el muñeco se
     * mueve hay resolver assets, mandar la cola a la escena y bajar el `.glb`,
     * y nada de eso estaba cronometrado.
     *
     * Solo se cuenta la primera oración del turno: las siguientes llegan con el
     * avatar ya sonando, y para esas lo que manda es cuánto tardó el backend.
     */
    fun onSignStart(segmentToMoveMs: Long) {
        signStart.record(segmentToMoveMs)
        Log.i(
            TAG,
            "oración→avatar: $segmentToMoveMs ms (${signStart.summarise()})",
        )
    }

    /**
     * Una frase del dictado se cerró, sea por el silencio que oyó el STT o
     * porque el usuario apagó el micrófono.
     *
     * Es el equivalente del `turno` de tiempo real para el otro interruptor, y
     * el número que dice si el dictado está sirviendo: si el usuario apaga el
     * micrófono después de tres frases, hay tres líneas acá y una sola frase
     * larga, y esas dos coisas se feltían distinto.
     */
    fun onDictationUtterance(text: String) {
        val words = text.split(' ', '\n', '\t').count { it.isNotBlank() }
        val index = dictationPhrases.incrementAndGet()
        dictationWords.addAndGet(words.toLong())
        Log.i(TAG, "dictado #$index: ${words} palabras, ${text.length} caracteres")
    }

    /** One line with everything, for logcat after a test run. */
    fun snapshot(): String = buildString {
        append("frases=").append(phrases.get())
        append(" oraciones=").append(sentences.get())
        append(" glosas=").append(glosses.get())
        append(" sin_seña=").append(unavailable.get())
        append(" dictado_frases=").append(dictationPhrases.get())
        append(" dictado_palabras=").append(dictationWords.get())
        append(" duracion_frase=").append(turnTotal.summarise())
        append(" duracion_firmado=").append(signTotal.summarise())
        append(" oracion_a_avatar=").append(signStart.summarise())
    }

    /** Zeros every counter, so a snapshot describes one test run and not the session. */
    fun reset() {
        phrases.set(0)
        sentences.set(0)
        glosses.set(0)
        unavailable.set(0)
        dictationPhrases.set(0)
        dictationWords.set(0)
        turnTotal.clear()
        signTotal.clear()
        signStart.clear()
    }

    private companion object {
        const val TAG = "SignAiMetrics"
    }
}

/**
 * Min / p50 / p95 / max over a bounded ring buffer.
 *
 * Keeping the whole history is not worth it on a phone that also has to decode
 * a 10 MB avatar: 256 samples is far more than a test session produces, and a
 * ring buffer means the cost stays flat however long the app stays open.
 */
private class Latency(private val capacity: Int = 256) {
    private val samples = LongArray(capacity)
    private var cursor = 0
    private var filled = 0

    fun record(ms: Long) {
        samples[cursor] = ms
        cursor = (cursor + 1) % capacity
        if (filled < capacity) filled++
    }

    fun clear() {
        cursor = 0
        filled = 0
    }

    fun summarise(): String {
        if (filled == 0) return "n/a"
        val sorted = samples.copyOf(filled)
        sorted.sort()
        return "p50=${percentile(sorted, 0.50f)}ms p95=${percentile(sorted, 0.95f)}ms " +
            "max=${sorted[filled - 1]}ms n=$filled"
    }

    private fun percentile(sorted: LongArray, q: Float): Long {
        val index = ((filled - 1) * q).toInt().coerceIn(0, filled - 1)
        return sorted[index]
    }
}