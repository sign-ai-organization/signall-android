package com.signall.app.testing

import org.robolectric.shadows.ShadowAudioRecord
import java.util.Arrays

/**
 * `AudioRecord` de prueba, **lento a propósito**.
 *
 * `MainScreenState` construye su propio [com.signall.app.data.AudioCapture] y no
 * se puede inyectar, así que prender el micrófono en una prueba arranca el bucle
 * real de lectura. Con el `AudioRecord` que simula Robolectric por defecto ese
 * bucle devuelve datos al instante y, en una prueba que deja el micrófono
 * prendido, se come la memoria del proceso de pruebas.
 *
 * Esta fuente entrega un bloque cada [READS_PER_BLOCK] lecturas y duerme
 * [BLOCKING_MS] entre lecturas vacías, que es lo que hace un micrófono de verdad
 * cuando nadie habla. El bucle sigue vivo mientras el modo lo pida, pero a un
 * ritmo que no afecta a la prueba.
 */
object ThrottledAudioRecord {

    private const val READS_PER_BLOCK = 25
    private const val BLOCKING_MS = 2L

    fun install() {
        ShadowAudioRecord.setSource(
            object : ShadowAudioRecord.AudioRecordSource {
                private var reads = 0

                override fun readInShortArray(
                    buffer: ShortArray,
                    offset: Int,
                    size: Int,
                    blocking: Boolean,
                ): Int {
                    reads++
                    if (reads % READS_PER_BLOCK == 0) {
                        Arrays.fill(buffer, offset, offset + size, 0)
                        return size
                    }
                    if (blocking) Thread.sleep(BLOCKING_MS)
                    return 0
                }
            },
        )
    }

    fun clear() = ShadowAudioRecord.clearSource()
}
