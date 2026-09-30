package at.clavierhaus.unisonmaster.audio

/**
 * A WAV file as the engine wants it: mono floats at one sample rate.
 *
 * For the probe that feeds a recording into the app in place of the
 * microphone (docs/PROBE.md). The point of the probe is that the engine
 * cannot tell the two apart, so this produces exactly what
 * [AudioSource] promises — mono PCM float in [-1, 1] — and nothing else
 * changes anywhere above it.
 *
 * Understands what the recorder writes and what the corpora hold: PCM
 * 16-bit and 32-bit float, mono or stereo (the left channel is taken, as
 * the phone records mono anyway). Anything else is refused by name rather
 * than played as noise.
 */
object WavPcm {

    data class Pcm(val sampleRateHz: Int, val samples: FloatArray) {
        val seconds: Double get() = samples.size.toDouble() / sampleRateHz
    }

    class NotSupported(message: String) : Exception(message)

    private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, i: Int) =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) or
            ((b[i + 2].toInt() and 0xFF) shl 16) or ((b[i + 3].toInt() and 0xFF) shl 24)

    private fun tag(b: ByteArray, i: Int) =
        buildString { for (j in 0 until 4) append((b[i + j].toInt() and 0xFF).toChar()) }

    /** Parses [bytes]; throws [NotSupported] with a sentence a tuner can act on. */
    fun read(bytes: ByteArray): Pcm {
        if (bytes.size < 44 || tag(bytes, 0) != "RIFF" || tag(bytes, 8) != "WAVE") {
            throw NotSupported("Not a WAV file.")
        }
        var format = -1
        var channels = 0
        var rate = 0
        var bits = 0
        var dataAt = -1
        var dataLen = 0
        // walk the chunks: the recorder's header is plain, the corpora's may not be
        var p = 12
        while (p + 8 <= bytes.size) {
            val id = tag(bytes, p)
            val len = u32(bytes, p + 4)
            val body = p + 8
            if (len < 0 || body + len > bytes.size + 1) break
            when (id) {
                "fmt " -> {
                    format = u16(bytes, body)
                    channels = u16(bytes, body + 2)
                    rate = u32(bytes, body + 4)
                    bits = u16(bytes, body + 14)
                }
                "data" -> { dataAt = body; dataLen = minOf(len, bytes.size - body) }
            }
            if (dataAt >= 0 && format > 0) break
            p = body + len + (len and 1)     // chunks are word-aligned
        }
        if (format < 0 || dataAt < 0) throw NotSupported("The WAV file has no audio in it.")
        if (channels < 1) throw NotSupported("The WAV file names no channels.")
        // 1 = PCM integer, 3 = IEEE float, 0xFFFE = extensible (the subformat is in the
        // extension; the recorder never writes it, so say so rather than guess)
        val frames: Int
        val out: FloatArray
        when {
            format == 1 && bits == 16 -> {
                val step = 2 * channels
                frames = dataLen / step
                out = FloatArray(frames)
                for (i in 0 until frames) {
                    val v = u16(bytes, dataAt + i * step)
                    val s = if (v >= 0x8000) v - 0x10000 else v
                    out[i] = s / 32768f
                }
            }
            format == 3 && bits == 32 -> {
                val step = 4 * channels
                frames = dataLen / step
                out = FloatArray(frames)
                for (i in 0 until frames) {
                    out[i] = Float.fromBits(u32(bytes, dataAt + i * step))
                }
            }
            else -> throw NotSupported("WAV format $format at $bits bits is not read here; 16-bit PCM or 32-bit float.")
        }
        if (frames == 0) throw NotSupported("The WAV file is empty.")
        return Pcm(rate, out)
    }
}
