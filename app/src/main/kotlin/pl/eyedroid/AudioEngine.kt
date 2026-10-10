package pl.eyedroid

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*

class AudioEngine(private val scope: CoroutineScope) {
    private var recorder: AudioRecord? = null
    private var job: Job? = null

    @SuppressLint("MissingPermission") // Service starts only after runtime permissions succeed.
    fun start() {
        check(recorder == null)
        val minimum = AudioRecord.getMinBufferSize(48_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "48 kHz PCM is unavailable" }
        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(48_000)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minimum * 2, 7680)).build()
        recorder = record
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Microphone initialization failed" }
        record.startRecording()
        check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING)
        job = scope.launch(Dispatchers.IO) {
            val bytes = ByteArray(1920)
            while (isActive) {
                val n = record.read(bytes, 0, bytes.size, AudioRecord.READ_BLOCKING)
                if (!isActive) break
                if (n < 0) {
                    StreamState.fail("Mikrofon został przerwany ($n). Zatrzymaj i uruchom sesję ponownie.")
                    break
                }
                if (n > 0) StreamState.audio.tryEmit(bytes.copyOf(n - n % 2))
            }
        }
    }

    suspend fun close() {
        job?.cancel()
        recorder?.let { runCatching { it.stop() } }
        job?.join()
        recorder?.release()
        recorder = null; job = null
    }
}
