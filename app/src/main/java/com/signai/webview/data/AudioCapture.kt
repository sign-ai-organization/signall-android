package com.signai.webview.data

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Microphone capture, in the exact shape the backend expects.
 *
 * The backend forwards binary frames straight to the STT service, which wants
 * **raw PCM16LE, mono, 16 kHz** with no WAV header. That is also what
 * `AudioRecord` produces natively, so there is no resampling and no conversion
 * here — the buffer is just read as shorts and written out little-endian.
 *
 * Chunks are 30 ms (480 samples, 960 bytes), the same size
 * `scripts/manual_translation_client.py` in the backend uses and the same the
 * STT's voice-activity detector works on.
 */
class AudioCapture(private val onChunk: (ByteArray) -> Unit) {

    private var record: AudioRecord? = null
    private var job: Job? = null

    val isRecording: Boolean
        get() = record?.recordingState == AudioRecord.RECORDSTATE_RECORDING

    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked by the caller
    fun start(scope: CoroutineScope, sampleRate: Int = SAMPLE_RATE, frameMs: Int = FRAME_MS) {
        if (isRecording) return
        val frameSamples = sampleRate * frameMs / 1000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            Log.w(TAG, "AudioRecord reports no usable buffer size ($minBuffer)")
            return
        }

        // VOICE_RECOGNITION rather than MIC: it is the source tuned for speech
        // recognition, so the STT hears the voice instead of the AGC pumping.
        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, frameSamples * BYTES_PER_SAMPLE * 2),
            )
        }.getOrElse {
            Log.w(TAG, "could not create AudioRecord: ${it.message}")
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord not initialized (state=${recorder.state})")
            recorder.release()
            return
        }

        record = recorder
        val buffer = ShortArray(frameSamples)
        val bytes = ByteArray(frameSamples * BYTES_PER_SAMPLE)

        try {
            recorder.startRecording()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "startRecording failed: ${e.message}")
            recorder.release()
            record = null
            return
        }
        Log.i(TAG, "recording ${sampleRate}Hz mono, chunks of ${frameMs}ms")

        job = scope.launch(Dispatchers.IO) {
            while (isRecording) {
                val read = recorder.read(buffer, 0, frameSamples)
                if (read <= 0) {
                    if (read < 0) Log.w(TAG, "AudioRecord.read -> $read")
                    continue
                }
                // Only the samples actually read; a short read must not send
                // leftover audio from the previous chunk.
                for (i in 0 until read) {
                    val sample = buffer[i].toInt()
                    bytes[i * 2] = (sample and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
                }
                val used = read * BYTES_PER_SAMPLE
                if (used < bytes.size) onChunk(bytes.copyOf(used)) else onChunk(bytes)
            }
        }
    }

    fun stop() {
        // `stop()` is called from several places and most of the time the
        // recorder is already idle. Saying "recording stopped" when nothing was
        // recording turns a no-op into a mystery in logcat.
        val wasRecording = isRecording
        job?.cancel()
        job = null
        record?.let { recorder ->
            runCatching { recorder.stop() }
            recorder.release()
        }
        record = null
        Log.i(TAG, if (wasRecording) "recording stopped" else "stop() sin grabación activa")
    }

    companion object {
        const val TAG = "SignAiNet"
        const val SAMPLE_RATE = 16_000
        const val FRAME_MS = 30
        const val BYTES_PER_SAMPLE = 2
    }
}
