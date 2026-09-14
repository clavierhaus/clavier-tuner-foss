package at.clavierhaus.unisonmaster.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Triggered waveform of one string's fundamental.
 *
 * A sharp bandpass latched to the fundamental removes every partial, so the
 * trace is a clean sine by construction. One filtered sweep, triggered on a
 * rising zero-crossing, drawn at full sample resolution and normalised so it
 * keeps full height as the note decays. Nothing computed on top.
 */
class Scope(private val sampleRate: Int) {

    private val ring = FloatArray(sampleRate / 2)   // 500 ms
    private var pos = 0
    private var filled = 0

    private var b0 = 0.0; private var a1 = 0.0; private var a2 = 0.0
    private var z1 = 0.0; private var z2 = 0.0
    private var tunedHz = 0.0

    fun sampleRateForDraw(): Double = sampleRate.toDouble()

    /** Latch the bandpass on [hz]. Q high, so neighbouring partials are gone. */
    fun tune(hz: Double) {
        if (hz <= 0.0 || kotlin.math.abs(hz - tunedHz) < 0.1) return
        tunedHz = hz
        val w0 = 2.0 * PI * hz / sampleRate
        val q = 20.0
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        b0 = alpha / a0
        a1 = -2.0 * cos(w0) / a0
        a2 = (1.0 - alpha) / a0
    }

    fun push(buffer: FloatArray) {
        for (x in buffer) {
            val y = if (tunedHz <= 0.0) x.toDouble() else {
                val v = x - a1 * z1 - a2 * z2
                val out = b0 * (v - z2)
                z2 = z1; z1 = v
                out
            }
            ring[pos] = y.toFloat()
            pos = (pos + 1) % ring.size
            if (filled < ring.size) filled++
        }
    }

    /** [spanSamples] samples from the latest rising zero-crossing, unit peak. */
    fun window(spanSamples: Int): FloatArray? {
        if (filled < ring.size || spanSamples >= ring.size) return null
        val n = ring.size
        val lin = FloatArray(n)
        for (i in 0 until n) lin[i] = ring[(pos + i) % n]

        var peak = 0f
        for (i in n / 2 until n) { val a = if (lin[i] < 0) -lin[i] else lin[i]; if (a > peak) peak = a }
        if (peak < 3e-5f) return null

        var trig = -1
        var i = n - spanSamples - 1
        while (i >= 1) {
            if (lin[i - 1] <= 0f && lin[i] > 0f) { trig = i; break }
            i--
        }
        if (trig < 0) trig = n - spanSamples - 1

        val inv = 1f / peak
        return FloatArray(spanSamples) { (lin[trig + it] * inv).coerceIn(-1f, 1f) }
    }
}
