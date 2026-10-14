package com.signai.webview.ui.state

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/**
 * Latency numbers only the device can produce.
 *
 * The backend and the STT each measure their own leg (`/metrics` on both), and
 * between them they know everything except what happens after the message lands
 * on the phone: how long a fragment took to become queue items, and how long a
 * whole turn took from the first text to the last fragment.
 *
 * Those two are what decide whether the avatar feels like it is keeping up with
 * the speaker, so they are worth having without a debugger attached. Everything
 * goes to logcat under `SignAiMetrics`; there is no in-app surface, because a
 * number nobody reads does not belong in the interface.
 *
 *     adb logcat -s SignAiMetrics
 */
class SignAiMetrics {

    private val fragments = AtomicLong()
    private val turns = AtomicLong()
    private val glosses = AtomicLong()
    private val unavailable = AtomicLong()
    private val dictationPhrases = AtomicLong()
    private val dictationWords = AtomicLong()

    private val fragmentToQueue = Latency()
    private val turnTotal = Latency()

    /** A streamed fragment arrived and became queue items. */
    fun onFragment(latencyMs: Long, queuedSigns: Int) {
        fragments.incrementAndGet()
        fragmentToQueue.record(latencyMs)
        Log.i(
            TAG,
            "fragmento #${fragments.get()}: $queuedSigns señas en ${latencyMs}ms " +
                "(${fragmentToQueue.summarise()})",
        )
    }

    /** A streamed turn closed: first text through last fragment. */
    fun onTurn(totalMs: Long, fragmentCount: Int, glossCount: Int) {
        turns.incrementAndGet()
        glosses.addAndGet(glossCount.toLong())
        turnTotal.record(totalMs)
        Log.i(
            TAG,
            "turno #${turns.get()}: ${totalMs}ms, $fragmentCount fragmentos, $glossCount glosas " +
                "(${turnTotal.summarise()})",
        )
    }

    /** A fragment produced no playable sign, so nothing was queued for it. */
    fun onUnavailableGloss() {
        unavailable.incrementAndGet()
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
        append("fragmentos=").append(fragments.get())
        append(" turnos=").append(turns.get())
        append(" glosas=").append(glosses.get())
        append(" sin_seña=").append(unavailable.get())
        append(" dictado_frases=").append(dictationPhrases.get())
        append(" dictado_palabras=").append(dictationWords.get())
        append(" fragmento_a_cola=").append(fragmentToQueue.summarise())
        append(" duracion_turno=").append(turnTotal.summarise())
    }

    /** Zeros every counter, so a snapshot describes one test run and not the session. */
    fun reset() {
        fragments.set(0)
        turns.set(0)
        glosses.set(0)
        unavailable.set(0)
        dictationPhrases.set(0)
        dictationWords.set(0)
        fragmentToQueue.clear()
        turnTotal.clear()
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