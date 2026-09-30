package at.clavierhaus.unisonmaster.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.thread

/**
 * The microphone, or a recording played in its place (docs/PROBE.md).
 *
 * A probe, not a feature. Away from a piano there is no way to see what the
 * engine makes of a real string, and a bug seen once at the instrument cannot
 * be chased without it. Because this *is* an [AudioSource], everything above
 * it — the tap, the readers, the identifier, the screen — runs exactly as it
 * does on the microphone: nothing is stubbed and nothing knows the difference.
 * That is the whole point: what the probe shows is what the piano would show.
 *
 * The buffers are handed over in real time, which matters. The strike
 * detector, the reader's memory and the settling skip are all written in
 * seconds, so a file poured in as fast as the processor allows would be read
 * quite differently from the same file played at its own speed.
 *
 * A fed file repeats, with [GAP_S] of silence between passes so that each
 * one begins with a clean onset rather than a splice.
 */
class FeedableAudioSource(val live: AudioSource) : AudioSource {

    override val sampleRateHz: Int get() = live.sampleRateHz

    private val _feeding = MutableStateFlow<String?>(null)

    /** The name of the recording being played, or null on the microphone. */
    val feeding: StateFlow<String?> = _feeding.asStateFlow()

    private var samples: FloatArray? = null
    private var pump: Thread? = null
    @Volatile private var running = false

    /**
     * Plays [pcm] in place of the microphone from the next [start] on.
     * Refuses a file recorded at another rate rather than resampling it:
     * a resampler would be one more thing between the recording and the
     * reading, and every corpus here is already at the phone's rate.
     */
    fun feed(name: String, pcm: WavPcm.Pcm) {
        require(pcm.sampleRateHz == live.sampleRateHz) {
            "This recording is ${pcm.sampleRateHz} Hz; the engine runs at ${live.sampleRateHz} Hz."
        }
        val wasRunning = running
        if (wasRunning) stop()
        samples = pcm.samples
        _feeding.value = name
    }

    /** Back to the microphone. */
    fun useMicrophone() {
        if (running) stop()
        samples = null
        _feeding.value = null
    }

    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
        val s = samples
        if (s == null) { live.start(bufferSize, onBuffer); return }
        if (running) return
        running = true
        pump = thread(name = "audio-feed", isDaemon = true) {
            val buf = FloatArray(bufferSize)
            val gap = (GAP_S * sampleRateHz).toInt()
            val perBufferNs = 1_000_000_000L * bufferSize / sampleRateHz
            var due = System.nanoTime()
            var p = 0
            while (running) {
                // the file, then a silence, then the file again
                if (p < s.size) {
                    val n = minOf(bufferSize, s.size - p)
                    s.copyInto(buf, 0, p, p + n)
                    if (n < bufferSize) java.util.Arrays.fill(buf, n, bufferSize, 0f)
                } else {
                    java.util.Arrays.fill(buf, 0f)
                }
                p += bufferSize
                if (p >= s.size + gap) p = 0
                onBuffer(buf)
                due += perBufferNs
                val wait = due - System.nanoTime()
                if (wait > 0) {
                    try { Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt()) }
                    catch (e: InterruptedException) { break }
                } else if (wait < -perBufferNs * 8) {
                    due = System.nanoTime()      // fell far behind: do not sprint to catch up
                }
            }
        }
    }

    override fun stop() {
        if (samples == null) { live.stop(); return }
        running = false
        pump?.interrupt()
        pump = null
    }

    companion object {
        /** Silence between passes, so each pass starts with an onset and not a splice. */
        const val GAP_S = 1.0
    }
}
