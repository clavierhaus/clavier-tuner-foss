package at.clavierhaus.unisonmaster.audio

/**
 * The entire platform contract for audio input. Deliberately tiny:
 * "give me mono PCM float buffers at [sampleRateHz]". Everything above
 * this line is shared code; everything below it is ~100 lines per platform.
 *
 * Android actual: AudioRecord with MediaRecorder.AudioSource.UNPROCESSED
 * (fallback MIC), ENCODING_PCM_FLOAT.
 * iOS actual (later): AVAudioEngine input node with the audio session in
 * .measurement mode — Apple's equivalent of UNPROCESSED (no AGC, no
 * voice processing).
 */
interface AudioSource {
    val sampleRateHz: Int

    /**
     * Starts capture. [onBuffer] is invoked from a capture thread with
     * successive buffers of [bufferSize] mono float samples in [-1, 1].
     * The array is reused; copy it if you keep it beyond the callback.
     */
    fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit)

    fun stop()
}

/**
 * Creates the platform audio source. Requires the microphone permission to
 * have been granted before [AudioSource.start] is called.
 */
expect fun createAudioSource(sampleRateHz: Int = 48_000): AudioSource
