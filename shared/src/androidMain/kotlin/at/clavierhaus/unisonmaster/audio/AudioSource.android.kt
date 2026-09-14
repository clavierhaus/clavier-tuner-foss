package at.clavierhaus.unisonmaster.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread

/**
 * Android capture path. Ties directly into the app's core requirement:
 * MediaRecorder.AudioSource.UNPROCESSED bypasses AGC, noise suppression and
 * speech EQ, delivering the linear signal the partial measurement needs.
 * Falls back to MIC only if UNPROCESSED cannot be initialised on the device.
 */
class AndroidAudioSource(override val sampleRateHz: Int) : AudioSource {

    @Volatile
    private var record: AudioRecord? = null

    @Volatile
    private var running = false

    /** Which source was actually opened — surface this in a debug UI later. */
    var usedUnprocessed: Boolean = false
        private set

    @SuppressLint("MissingPermission") // RECORD_AUDIO is requested by the app before start()
    private fun open(): AudioRecord {
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_FLOAT
        val minBytes = AudioRecord.getMinBufferSize(sampleRateHz, channel, encoding)
        val bufferBytes = maxOf(minBytes, sampleRateHz * 4) // >= 1 s headroom, float = 4 bytes

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRateHz)
            .setChannelMask(channel)
            .setEncoding(encoding)
            .build()

        fun build(source: Int) = AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferBytes)
            .build()

        val unprocessed = runCatching { build(MediaRecorder.AudioSource.UNPROCESSED) }
            .getOrNull()
            ?.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        if (unprocessed != null) {
            usedUnprocessed = true
            return unprocessed
        }

        usedUnprocessed = false
        return build(MediaRecorder.AudioSource.MIC).also {
            check(it.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialise" }
        }
    }

    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
        if (running) return
        val rec = open()
        record = rec
        running = true
        rec.startRecording()
        thread(name = "unisonmaster-capture", isDaemon = true) {
            val buffer = FloatArray(bufferSize)
            while (running) {
                var filled = 0
                while (running && filled < bufferSize) {
                    val n = rec.read(buffer, filled, bufferSize - filled, AudioRecord.READ_BLOCKING)
                    if (n <= 0) break
                    filled += n
                }
                if (running && filled == bufferSize) onBuffer(buffer)
            }
        }
    }

    override fun stop() {
        running = false
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null
    }
}

actual fun createAudioSource(sampleRateHz: Int): AudioSource = AndroidAudioSource(sampleRateHz)
